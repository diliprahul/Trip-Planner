import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/useAuth";
import { resendRegistrationOtp } from "../api/tripApi";
import { validatePassword } from "../utils/validators";

const Register = () => {
  const [formData, setFormData] = useState({ name: "", email: "", password: "", confirmPassword: "", cityOfResidence: "", favouritePlace: "" });
  const [step, setStep] = useState("form");
  const [otp, setOtp] = useState("");
  const [seconds, setSeconds] = useState(60);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const { register, verifyRegistration, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    document.title = "Register | TripPlanner";
    if (isAuthenticated) navigate("/", { replace: true });
  }, [isAuthenticated, navigate]);

  useEffect(() => {
    if (step !== "verify" || seconds <= 0) return undefined;
    const timer = window.setTimeout(() => setSeconds((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [step, seconds]);

  const handleSubmit = async (event) => {
    event.preventDefault(); setError(null); setSuccess(null);
    if (formData.password !== formData.confirmPassword) return setError("Passwords do not match");
    const passwordError = validatePassword(formData.password);
    if (passwordError) return setError(passwordError);
    setSubmitting(true);
    try {
      await register({ name: formData.name, email: formData.email, password: formData.password, cityOfResidence: formData.cityOfResidence, favouritePlace: formData.favouritePlace });
      setStep("verify"); setSeconds(60); setSuccess("Verification code sent. Check your email.");
    } catch (err) {
      const message = err.response?.status === 409 && err.response?.data?.code === "EMAIL_ALREADY_EXISTS"
        ? "Email already exists."
        : err.response?.data?.message || err.message || "Registration failed";
      setError(message);
      if (err.response?.status === 409 && message.includes("pending")) setStep("verify");
    } finally { setSubmitting(false); }
  };

  const handleVerify = async (event) => {
    event.preventDefault(); setError(null); setSuccess(null);
    if (!/^\d{6}$/.test(otp)) return setError("Enter the six-digit verification code");
    setSubmitting(true);
    try { await verifyRegistration(formData.email, otp); navigate("/"); }
    catch (err) { setError(err.response?.data?.message || err.message || "Verification failed"); }
    finally { setSubmitting(false); }
  };

  const handleResend = async () => {
    setError(null); setSuccess(null); setSubmitting(true);
    try { await resendRegistrationOtp(formData.email); setSeconds(60); setSuccess("A new verification code was sent."); }
    catch (err) { setError(err.response?.data?.message || err.message || "Could not resend the code"); }
    finally { setSubmitting(false); }
  };

  const field = (label, name, type = "text", extra = {}) => <div className="form-group"><label>{label}</label><input type={type} name={name} value={formData[name]} onChange={(e) => setFormData({ ...formData, [name]: e.target.value })} {...extra} required={extra.required ?? (name !== "cityOfResidence" && name !== "favouritePlace")} /></div>;

  return <div className="auth-editorial-wrapper" style={{ backgroundImage: "linear-gradient(rgba(37, 42, 36, 0.45), rgba(37, 42, 36, 0.65)), url('/images/Register-page-hero.jpg')" }}>
    <div className="auth-editorial-card" style={{ maxWidth: "460px", width: "100%", margin: "0 auto" }}>
      {step === "form" ? <>
        <h1>Create Account</h1><p className="subtitle">Start planning and saving your personalized travel itineraries</p>
        {error && <div className="form-error">{error}</div>}
        <form onSubmit={handleSubmit}>
          {field("Full Name", "name", "text", { placeholder: "e.g. John Doe" })}
          {field("Email Address", "email", "email", { placeholder: "e.g. user@example.com", autoComplete: "email" })}
          {field("Current City (Optional)", "cityOfResidence", "text", { placeholder: "e.g. New York", maxLength: 100, required: false })}
          {field("Favourite Place or City (Optional)", "favouritePlace", "text", { placeholder: "e.g. Paris", maxLength: 100, required: false })}
          {field("Password", "password", "password", { placeholder: "At least 6 characters", autoComplete: "new-password" })}
          {field("Confirm Password", "confirmPassword", "password", { placeholder: "Re-enter password", autoComplete: "new-password" })}
          <button type="submit" disabled={submitting}>{submitting ? "Sending Code..." : "Register"}</button>
        </form>
        <p style={{ marginTop: "20px", fontSize: "14px", color: "var(--text-secondary)" }}>Already have an account? <Link to="/login" className="auth-link">Sign In</Link></p>
      </> : <>
        <h1>Verify Your Email</h1><p className="subtitle">Enter the six-digit code sent to {formData.email}</p>
        {error && <div className="form-error">{error}</div>}{success && <div className="form-success">{success}</div>}
        <form onSubmit={handleVerify}><div className="form-group"><label>Verification Code</label><input type="text" inputMode="numeric" maxLength={6} value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))} placeholder="Enter 6-digit code" required /></div><button type="submit" disabled={submitting}>{submitting ? "Verifying..." : "Verify Email"}</button></form>
        <p style={{ marginTop: "20px", fontSize: "14px", color: "var(--text-secondary)" }}>{seconds > 0 ? `Resend available in ${seconds}s` : <button type="button" className="auth-link" disabled={submitting} onClick={handleResend}>Resend verification code</button>}</p>
      </>}
    </div>
  </div>;
};

export default Register;
