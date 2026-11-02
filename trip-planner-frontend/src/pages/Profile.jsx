import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { getProfile, updateProfile, uploadProfilePicture, deleteProfilePicture } from "../api/tripApi";
import { useAuth } from "../context/useAuth";

const Profile = () => {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const [name, setName] = useState("");
  const [favouritePlace, setFavouritePlace] = useState("");
  const [cityOfResidence, setCityOfResidence] = useState("");
  const [savingProfile, setSavingProfile] = useState(false);
  const [uploadingPic, setUploadingPic] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [previewUrl, setPreviewUrl] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [imgError, setImgError] = useState(false);

  const { isAuthenticated, fetchCurrentUser } = useAuth();
  const navigate = useNavigate();

  const effectiveAvatar = previewUrl || profile?.profilePictureUrl;

  const prevAvatarRef = useRef(effectiveAvatar);
  if (prevAvatarRef.current !== effectiveAvatar) {
    prevAvatarRef.current = effectiveAvatar;
    if (imgError) setImgError(false);
  }

  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  useEffect(() => {
    document.title = "User Profile | TripPlanner";
    if (!isAuthenticated) {
      navigate("/login", { replace: true });
      return;
    }

    const loadProfile = async () => {
      try {
        setLoading(true);
        const data = await getProfile();
        setProfile(data);
        setName(data.name || "");
        setFavouritePlace(data.favouritePlace || "");
        setCityOfResidence(data.cityOfResidence || "");
      } catch (err) {
        setError(err.response?.data?.message || "Failed to load profile");
      } finally {
        setLoading(false);
      }
    };

    loadProfile();
  }, [isAuthenticated, navigate]);

  const handleStartEditing = () => {
    setError(null);
    setSuccess(null);
    setName(profile?.name || "");
    setFavouritePlace(profile?.favouritePlace || "");
    setCityOfResidence(profile?.cityOfResidence || "");
    setIsEditing(true);
  };

  const handleCancelEditing = () => {
    setError(null);
    setSuccess(null);
    setName(profile?.name || "");
    setFavouritePlace(profile?.favouritePlace || "");
    setCityOfResidence(profile?.cityOfResidence || "");
    setSelectedFile(null);
    setPreviewUrl(null);
    setIsEditing(false);
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    if (savingProfile) return;
    setError(null);
    setSuccess(null);

    if (!name.trim()) {
      setError("Display Name cannot be empty");
      return;
    }

    try {
      setSavingProfile(true);
      const updated = await updateProfile({
        name: name.trim(),
        favouritePlace: favouritePlace.trim(),
        cityOfResidence: cityOfResidence.trim(),
      });
      setProfile(updated);
      await fetchCurrentUser();
      setSuccess("Profile updated successfully");
      setIsEditing(false);
    } catch (err) {
      setError(err.response?.data?.message || "Failed to update profile");
    } finally {
      setSavingProfile(false);
    }
  };

  const handleFileChange = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 5 * 1024 * 1024) {
      setError("File size exceeds the 5 MB limit");
      return;
    }

    const ext = file.name.substring(file.name.lastIndexOf(".")).toLowerCase();
    if (![".jpg", ".jpeg", ".png", ".webp"].includes(ext)) {
      setError("Only JPEG, PNG, and WebP images are supported");
      return;
    }

    setError(null);
    setSelectedFile(file);
    setPreviewUrl(URL.createObjectURL(file));
  };

  const handleUploadPicture = async () => {
    if (!selectedFile || uploadingPic) return;
    setError(null);
    setSuccess(null);

    const formData = new FormData();
    formData.append("file", selectedFile);

    try {
      setUploadingPic(true);
      const updated = await uploadProfilePicture(formData);
      setProfile(updated);
      await fetchCurrentUser();
      setSelectedFile(null);
      setPreviewUrl(null);
      setSuccess("Profile picture uploaded successfully");
    } catch (err) {
      setError(err.response?.data?.message || "Failed to upload profile picture");
    } finally {
      setUploadingPic(false);
    }
  };

  const handleDeletePicture = async () => {
    if (uploadingPic) return;
    if (!window.confirm("Are you sure you want to remove your custom profile picture?")) return;
    setError(null);
    setSuccess(null);

    try {
      setUploadingPic(true);
      const updated = await deleteProfilePicture();
      setProfile(updated);
      await fetchCurrentUser();
      setSuccess("Custom profile picture removed");
    } catch (err) {
      setError(err.response?.data?.message || "Failed to remove profile picture");
    } finally {
      setUploadingPic(false);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: "80px 24px", textAlign: "center", color: "var(--text-secondary)", flex: 1 }}>
        Loading profile...
      </div>
    );
  }

  return (
    <div style={{ width: "100%", flex: 1, paddingBottom: "60px" }}>
      {/* Hero Header Section */}
      <div
        style={{
          position: "relative",
          width: "100%",
          minHeight: "220px",
          backgroundImage: `linear-gradient(rgba(37, 42, 36, 0.5), rgba(37, 42, 36, 0.7)), url('/images/Profile-page-hero.jpg')`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          display: "flex",
          alignItems: "center",
          padding: "40px 40px",
          color: "#ffffff",
          marginBottom: "40px"
        }}
      >
        <div style={{ maxWidth: "650px", margin: "0 auto", width: "100%", display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "16px", zIndex: 2 }}>
          <div>
            <span style={{
              fontFamily: "var(--font-heading)",
              fontSize: "11px",
              fontWeight: "700",
              letterSpacing: "1.5px",
              textTransform: "uppercase",
              color: "#E9E3D6",
              background: "rgba(36, 75, 60, 0.85)",
              padding: "4px 10px",
              borderRadius: "4px",
              display: "inline-block",
              marginBottom: "8px"
            }}>
              Account Settings
            </span>
            <h1 style={{ fontSize: "32px", fontWeight: "800", color: "#FFFFFF", margin: 0, fontFamily: "var(--font-heading)" }}>
              User Profile
            </h1>
            <p style={{ fontSize: "15px", color: "#E9E3D6", margin: "6px 0 0" }}>
              Manage your personal travel preferences, display details, and profile picture.
            </p>
          </div>
          {!isEditing && (
            <button
              onClick={handleStartEditing}
              style={{
                background: "var(--brand-primary)",
                color: "white",
                border: "1px solid rgba(255,255,255,0.2)",
                padding: "10px 20px",
                borderRadius: "6px",
                fontWeight: "600",
                fontSize: "14px",
                cursor: "pointer",
                transition: "background-color 0.15s ease",
              }}
              onMouseEnter={(e) => (e.target.style.background = "var(--brand-hover)")}
              onMouseLeave={(e) => (e.target.style.background = "var(--brand-primary)")}
            >
              Edit Details
            </button>
          )}
        </div>
      </div>

      <div style={{ maxWidth: "650px", margin: "0 auto", padding: "0 16px", width: "100%" }}>
        {error && (
          <div style={{ color: "#991b1b", background: "#fef2f2", border: "1px solid #fca5a5", padding: "12px 16px", borderRadius: "6px", marginBottom: "20px", fontSize: "14px" }}>
            {error}
          </div>
        )}

        {success && (
          <div style={{ color: "#166534", background: "#f0fdf4", border: "1px solid #bbf7d0", padding: "12px 16px", borderRadius: "6px", marginBottom: "20px", fontSize: "14px" }}>
            {success}
          </div>
        )}

        <div style={{ background: "var(--surface-primary)", borderRadius: "12px", padding: "32px", border: "1px solid var(--color-border)", boxShadow: "0 1px 3px rgba(37, 42, 36, 0.04)" }}>
          {!isEditing ? (
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: "16px", marginBottom: "24px", paddingBottom: "20px", borderBottom: "1px solid var(--color-border)" }}>
                <div style={{ width: "64px", height: "64px", borderRadius: "50%", overflow: "hidden", background: "var(--surface-secondary)", display: "flex", alignItems: "center", justifyContent: "center", border: "1px solid var(--color-border)", flexShrink: 0 }}>
                  {effectiveAvatar && !imgError ? (
                    <img src={effectiveAvatar} alt="Profile" onError={() => setImgError(true)} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
                  ) : (
                    <span style={{ fontSize: "24px", fontWeight: "700", color: "var(--text-secondary)" }}>
                      {profile?.name ? profile.name.charAt(0).toUpperCase() : "U"}
                    </span>
                  )}
                </div>
                <div>
                  <h2 style={{ fontSize: "20px", fontWeight: "700", color: "var(--text-primary)", margin: "0 0 4px", fontFamily: "var(--font-heading)" }}>
                    {profile?.name || "Not provided"}
                  </h2>
                  <p style={{ fontSize: "13px", color: "var(--text-secondary)", margin: 0 }}>
                    {profile?.email || ""}
                  </p>
                </div>
              </div>

              <div style={{ marginBottom: "20px" }}>
                <label style={{ display: "block", fontSize: "12px", fontWeight: "600", color: "var(--text-secondary)", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: "6px" }}>
                  Favourite Place or City
                </label>
                <div style={{ fontSize: "15px", fontWeight: "600", color: "var(--text-primary)" }}>
                  {profile?.favouritePlace || "Not provided"}
                </div>
              </div>

              <div style={{ marginBottom: "20px" }}>
                <label style={{ display: "block", fontSize: "12px", fontWeight: "600", color: "var(--text-secondary)", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: "6px" }}>
                  Current Place
                </label>
                <div style={{ fontSize: "15px", fontWeight: "600", color: "var(--text-primary)" }}>
                  {profile?.cityOfResidence || "Not provided"}
                </div>
              </div>

              <div style={{ marginBottom: "20px" }}>
                <label style={{ display: "block", fontSize: "12px", fontWeight: "600", color: "var(--text-secondary)", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: "6px" }}>
                  Email Address
                </label>
                <div style={{ fontSize: "15px", fontWeight: "600", color: "var(--text-primary)" }}>
                  {profile?.email || "Not provided"}
                </div>
              </div>

              <div style={{ marginTop: "24px", paddingTop: "20px", borderTop: "1px solid var(--color-border)" }}>
                <span style={{ fontSize: "13px", color: "var(--text-secondary)" }}>
                  Member Since: <strong>{profile?.createdAt ? new Date(profile.createdAt).toLocaleDateString() : "N/A"}</strong>
                </span>
              </div>
            </div>
          ) : (
            <form onSubmit={handleSaveProfile}>
              <div style={{ display: "flex", alignItems: "center", gap: "20px", marginBottom: "24px", paddingBottom: "20px", borderBottom: "1px solid var(--color-border)", flexWrap: "wrap" }}>
                <div style={{ position: "relative", width: "72px", height: "72px", borderRadius: "50%", overflow: "hidden", background: "var(--surface-secondary)", display: "flex", alignItems: "center", justifyContent: "center", border: "1px solid var(--color-border)", flexShrink: 0 }}>
                  {effectiveAvatar && !imgError ? (
                    <img src={effectiveAvatar} alt="Profile" onError={() => setImgError(true)} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
                  ) : (
                    <span style={{ fontSize: "26px", fontWeight: "700", color: "var(--text-secondary)" }}>
                      {name ? name.charAt(0).toUpperCase() : "U"}
                    </span>
                  )}
                </div>

                <div>
                  <h3 style={{ margin: "0 0 4px", fontSize: "15px", color: "var(--text-primary)", fontWeight: "600", fontFamily: "var(--font-heading)" }}>Profile Picture</h3>
                  <p style={{ fontSize: "12px", color: "var(--text-secondary)", margin: "0 0 10px" }}>
                    Upload a JPEG, PNG, or WebP image (max 5 MB).
                  </p>
                  <div style={{ display: "flex", gap: "8px", flexWrap: "wrap", alignItems: "center" }}>
                    <label style={{ background: "var(--surface-secondary)", color: "var(--text-primary)", padding: "6px 12px", borderRadius: "6px", fontWeight: "600", fontSize: "12px", cursor: "pointer", border: "1px solid var(--color-border)" }}>
                      Select Image
                      <input type="file" accept="image/jpeg,image/png,image/webp" onChange={handleFileChange} style={{ display: "none" }} />
                    </label>

                    {selectedFile && (
                      <button
                        type="button"
                        onClick={handleUploadPicture}
                        disabled={uploadingPic}
                        style={{ background: "var(--brand-primary)", color: "white", border: "none", padding: "6px 12px", borderRadius: "6px", fontWeight: "600", fontSize: "12px", cursor: "pointer" }}
                      >
                        {uploadingPic ? "Saving..." : "Save Picture"}
                      </button>
                    )}

                    {profile?.customPicture && !selectedFile && (
                      <button
                        type="button"
                        onClick={handleDeletePicture}
                        disabled={uploadingPic}
                        style={{ background: "#fef2f2", color: "#991b1b", border: "1px solid #fca5a5", padding: "6px 12px", borderRadius: "6px", fontWeight: "600", fontSize: "12px", cursor: "pointer" }}
                      >
                        Remove Custom Picture
                      </button>
                    )}
                  </div>
                </div>
              </div>

              <div style={{ marginBottom: "16px" }}>
                <label style={{ display: "block", fontSize: "13px", fontWeight: "600", color: "var(--text-primary)", marginBottom: "6px" }}>
                  Display Name
                </label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  style={{ width: "100%", padding: "10px 14px", borderRadius: "6px", border: "1px solid var(--color-border)", background: "var(--surface-primary)", color: "var(--text-primary)", fontSize: "14px", outline: "none", boxSizing: "border-box" }}
                  required
                />
              </div>

              <div style={{ marginBottom: "16px" }}>
                <label style={{ display: "block", fontSize: "13px", fontWeight: "600", color: "var(--text-primary)", marginBottom: "6px" }}>
                  Favourite Place or City
                </label>
                <input
                  type="text"
                  value={favouritePlace}
                  onChange={(e) => setFavouritePlace(e.target.value)}
                  placeholder="e.g. Paris"
                  maxLength={100}
                  style={{ width: "100%", padding: "10px 14px", borderRadius: "6px", border: "1px solid var(--color-border)", background: "var(--surface-primary)", color: "var(--text-primary)", fontSize: "14px", outline: "none", boxSizing: "border-box" }}
                />
              </div>

              <div style={{ marginBottom: "16px" }}>
                <label style={{ display: "block", fontSize: "13px", fontWeight: "600", color: "var(--text-primary)", marginBottom: "6px" }}>
                  Current Place
                </label>
                <input
                  type="text"
                  value={cityOfResidence}
                  onChange={(e) => setCityOfResidence(e.target.value)}
                  placeholder="e.g. New York"
                  maxLength={100}
                  style={{ width: "100%", padding: "10px 14px", borderRadius: "6px", border: "1px solid var(--color-border)", background: "var(--surface-primary)", color: "var(--text-primary)", fontSize: "14px", outline: "none", boxSizing: "border-box" }}
                />
              </div>

              <div style={{ marginBottom: "20px" }}>
                <label style={{ display: "block", fontSize: "13px", fontWeight: "600", color: "var(--text-primary)", marginBottom: "6px" }}>
                  Email Address
                </label>
                <input
                  type="email"
                  value={profile?.email || ""}
                  disabled
                  style={{ width: "100%", padding: "10px 14px", borderRadius: "6px", border: "1px solid var(--color-border)", background: "var(--surface-secondary)", fontSize: "14px", color: "var(--text-secondary)", boxSizing: "border-box" }}
                />
                <span style={{ fontSize: "12px", color: "var(--text-secondary)", marginTop: "4px", display: "block" }}>Email address cannot be changed.</span>
              </div>

              <div style={{ display: "flex", gap: "12px" }}>
                <button
                  type="submit"
                  disabled={savingProfile}
                  style={{ background: "var(--brand-primary)", color: "white", border: "none", padding: "10px 20px", borderRadius: "6px", fontWeight: "600", fontSize: "14px", cursor: "pointer", flex: 1 }}
                >
                  {savingProfile ? "Saving Changes..." : "Save Changes"}
                </button>
                <button
                  type="button"
                  onClick={handleCancelEditing}
                  disabled={savingProfile}
                  style={{ background: "var(--surface-secondary)", color: "var(--text-primary)", border: "1px solid var(--color-border)", padding: "10px 20px", borderRadius: "6px", fontWeight: "600", fontSize: "14px", cursor: "pointer" }}
                >
                  Cancel
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};

export default Profile;
