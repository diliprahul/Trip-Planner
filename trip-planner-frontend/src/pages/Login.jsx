import { useState, useEffect, useRef } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/useAuth";
import { forgotPassword, verifyResetOtp, resetPassword } from "../api/tripApi";
import { validatePassword } from "../utils/validators";

const Login = () => {
  const [formData, setFormData] = useState({ email: "", password: "" });
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [resendingOtp, setResendingOtp] = useState(false);
  const [resendSeconds, setResendSeconds] = useState(0);
  const resendInProgress = useRef(false);
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  // Password reset flow state: "login" | "request" | "verify" | "reset"
  const [resetStep, setResetStep] = useState("login");
  const [resetEmail, setResetEmail] = useState("");
  const [otp, setOtp] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  useEffect(() => {
    document.title = "Sign In | TripPlanner";
    const params = new URLSearchParams(window.location.search);
    const errorParam = params.get("error");
    if (errorParam) {
      if (errorParam === "OAuthAuthenticationFailed") {
        setError("Google authentication failed. Please try again.");
      } else {
        setError("Unable to sign in. Please try again.");
      }
    }
    if (isAuthenticated) {
      navigate("/", { replace: true });
    }
  }, [isAuthenticated, navigate]);

  useEffect(() => {
    if (resetStep !== "verify" || resendSeconds <= 0) return undefined;
    const timer = window.setTimeout(() => {
      setResendSeconds((seconds) => Math.max(0, seconds - 1));
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [resetStep, resendSeconds]);

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccessMessage(null);
    setSubmitting(true);

    try {
      await login(formData);
      navigate("/");
    } catch (err) {
      const status = err.response?.status;
      if (status === 401) {
        setError("Incorrect email or password.");
      } else if (!err.response && (err.request || err.code === "ERR_NETWORK")) {
        setError("Unable to connect. Please try again.");
      } else if (status >= 500) {
        setError("Server error. Please try again later.");
      } else {
        setError("Unable to sign in. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleRequestReset = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccessMessage(null);
    setSubmitting(true);

    try {
      await forgotPassword(resetEmail);
      setSuccessMessage("If an account is eligible for password reset, instructions will be sent.");
      setResendSeconds(60);
      setResetStep("verify");
    } catch (err) {
      setError(err.response?.data?.message || "We could not process this request right now. Please try again later.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleResendResetOtp = async () => {
    if (resendInProgress.current || resendSeconds > 0) return;
    resendInProgress.current = true;
    setResendingOtp(true);
    setError(null);
    setSuccessMessage(null);

    try {
      await forgotPassword(resetEmail);
      setSuccessMessage("If an account is eligible for password reset, another code will be sent.");
      setOtp("");
      setResendSeconds(60);
    } catch {
      setError("We couldn't resend the code. Please try again.");
    } finally {
      resendInProgress.current = false;
      setResendingOtp(false);
    }
  };

  const handleChangeResetEmail = () => {
    setOtp("");
    setResendSeconds(0);
    setError(null);
    setSuccessMessage(null);
    setResetStep("request");
  };

  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccessMessage(null);
    setSubmitting(true);

    try {
      const res = await verifyResetOtp(resetEmail, otp);
      if (res.valid) {
        setSuccessMessage("OTP verified successfully. Please enter your new password.");
        setResetStep("reset");
      } else {
        setError("Invalid or expired OTP");
      }
    } catch (err) {
      setError(err.response?.data?.message || err.message || "Invalid or expired OTP");
    } finally {
      setSubmitting(false);
    }
  };

  const handleResetPassword = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccessMessage(null);

    const passwordError = validatePassword(newPassword);
    if (passwordError) {
      setError(passwordError);
      return;
    }

    if (newPassword !== confirmPassword) {
      setError("Passwords do not match");
      return;
    }

    setSubmitting(true);

    try {
      await resetPassword(resetEmail, otp, newPassword);
      setSuccessMessage("Password reset successfully. You can now sign in with your new password.");
      setResetStep("login");
      setFormData({ email: resetEmail, password: "" });
    } catch (err) {
      setError(err.response?.data?.message || err.message || "Failed to reset password");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div 
      className="auth-editorial-wrapper"
      style={{
        backgroundImage: `linear-gradient(rgba(37, 42, 36, 0.45), rgba(37, 42, 36, 0.65)), url('/images/travel-hero.jpg')`
      }}
    >
      <div className="auth-editorial-card" style={{ maxWidth: "460px", width: "100%", margin: "0 auto" }}>
          {resetStep === "login" && (
            <>
              <h1>Welcome Back</h1>
              <p className="subtitle">Sign in to access your saved trips and itineraries</p>

              {error && <div className="form-error">{error}</div>}
              {successMessage && <div className="form-success">{successMessage}</div>}

              <form onSubmit={handleSubmit}>
                <div className="form-group">
                  <label>Email Address</label>
                  <input
                    type="email"
                    name="email"
                    placeholder="e.g. user@example.com"
                    value={formData.email}
                    onChange={handleChange}
                    autoComplete="email"
                    required
                  />
                </div>

                <div className="form-group">
                  <label>Password</label>
                  <input
                    type="password"
                    name="password"
                    placeholder="Enter your password"
                    value={formData.password}
                    onChange={handleChange}
                    autoComplete="current-password"
                    required
                  />
                  <div style={{ display: "flex", justifyContent: "flex-end", marginTop: "6px" }}>
                    <button
                      type="button"
                      onClick={() => {
                        setResetStep("request");
                        setError(null);
                        setSuccessMessage(null);
                        setResetEmail(formData.email);
                      }}
                      className="auth-link"
                    >
                      Forgot Password?
                    </button>
                  </div>
                </div>

                <button type="submit" disabled={submitting}>
                  {submitting ? "Signing in..." : "Sign In"}
                </button>
              </form>

              <div className="auth-separator">
                <span>or</span>
              </div>

              <button
                type="button"
                onClick={() => {
                  const apiBase = import.meta.env.VITE_API_BASE_URL || "";
                  window.location.href = `${apiBase}/oauth2/authorization/google`;
                }}
                className="google-signin-btn"
              >
                <svg width="18" height="18" viewBox="0 0 24 24">
                  <path
                    fill="#4285F4"
                    d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.66-5.17 3.66-9.17z"
                  />
                  <path
                    fill="#34A853"
                    d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.13 0-5.78-2.11-6.73-4.96H1.19v3.15C3.18 21.32 7.28 24 12 24z"
                  />
                  <path
                    fill="#FBBC05"
                    d="M5.27 14.24c-.25-.72-.39-1.49-.39-2.24s.14-1.52.39-2.24V6.61H1.19C.43 8.14 0 9.88 0 12s.43 3.86 1.19 5.39l4.08-3.15z"
                  />
                  <path
                    fill="#EA4335"
                    d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.28 0 3.18 2.68 1.19 6.61l4.08 3.15c.95-2.85 3.6-4.96 6.73-4.96z"
                  />
                </svg>
                Continue with Google
              </button>

              <p style={{ marginTop: "20px", fontSize: "14px", color: "var(--text-secondary)" }}>
                Don't have an account? <Link to="/register" className="auth-link">Register</Link>
              </p>
            </>
          )}

          {resetStep === "request" && (
            <>
              <h1>Reset Password</h1>
              <p className="subtitle">Enter your registered email address to receive an OTP</p>

              {error && <div className="form-error">{error}</div>}

              <form onSubmit={handleRequestReset}>
                <div className="form-group">
                  <label>Email Address</label>
                  <input
                    type="email"
                    name="resetEmail"
                    placeholder="e.g. user@example.com"
                    value={resetEmail}
                    onChange={(e) => setResetEmail(e.target.value)}
                    autoComplete="email"
                    required
                  />
                </div>

                <button type="submit" disabled={submitting}>
                  {submitting ? "Sending OTP..." : "Send Reset OTP"}
                </button>
              </form>

              <p style={{ marginTop: "20px", fontSize: "14px", color: "var(--text-secondary)" }}>
                Remember your password?{" "}
                <button
                  type="button"
                  onClick={() => {
                    setResetStep("login");
                    setError(null);
                    setSuccessMessage(null);
                  }}
                  className="auth-link"
                >
                  Sign In
                </button>
              </p>
            </>
          )}

          {resetStep === "verify" && (
            <>
              <h1>Verify OTP</h1>
              <p className="subtitle">Enter the 6-digit verification code sent to your email</p>

              {error && <div className="form-error">{error}</div>}
              {successMessage && <div className="form-success">{successMessage}</div>}

              <form onSubmit={handleVerifyOtp}>
                <div className="form-group">
                  <label>Verification Code (OTP)</label>
                  <input
                    type="text"
                    name="otp"
                    placeholder="Enter 6-digit code"
                    value={otp}
                    onChange={(e) => setOtp(e.target.value)}
                    maxLength={6}
                    required
                  />
                </div>

                <button type="submit" disabled={submitting}>
                  {submitting ? "Verifying..." : "Verify OTP"}
                </button>
              </form>

              <div style={{ marginTop: "20px", fontSize: "14px", color: "var(--text-secondary)" }}>
                <button
                  type="button"
                  onClick={handleResendResetOtp}
                  className="auth-link"
                  disabled={resendSeconds > 0 || resendingOtp || submitting}
                >
                  {resendingOtp
                    ? "Sending OTP..."
                    : resendSeconds > 0
                      ? `Resend available in ${resendSeconds}s`
                      : "Resend OTP"}
                </button>
                <button
                  type="button"
                  onClick={handleChangeResetEmail}
                  className="auth-link"
                  style={{ marginLeft: "12px" }}
                  disabled={resendingOtp || submitting}
                >
                  Change Email
                </button>
              </div>
            </>
          )}

          {resetStep === "reset" && (
            <>
              <h1>Set New Password</h1>
              <p className="subtitle">Enter your new secure password</p>

              {error && <div className="form-error">{error}</div>}
              {successMessage && <div className="form-success">{successMessage}</div>}

              <form onSubmit={handleResetPassword}>
                <div className="form-group">
                  <label>New Password</label>
                  <input
                    type="password"
                    name="newPassword"
                    placeholder="At least 6 characters"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    required
                  />
                </div>

                <div className="form-group">
                  <label>Confirm New Password</label>
                  <input
                    type="password"
                    name="confirmPassword"
                    placeholder="Re-enter new password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required
                  />
                </div>

                <button type="submit" disabled={submitting}>
                  {submitting ? "Resetting..." : "Reset Password"}
                </button>
              </form>
            </>
          )}
        </div>
      </div>
    );
};

export default Login;
