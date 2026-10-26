import { useState, useEffect } from "react";
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

  useEffect(() => {
    setImgError(false);
  }, [effectiveAvatar]);

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
    setIsEditing(false);
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
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
    if (!selectedFile) return;
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
      <div style={{ padding: "80px 24px", textAlign: "center", color: "#64748b" }}>
        Loading profile...
      </div>
    );
  }

  return (
    <div style={{ maxWidth: "650px", margin: "24px auto", padding: "0 16px", width: "100%", flex: 1 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px", gap: "12px", flexWrap: "wrap" }}>
        <h1 style={{ fontSize: "26px", fontWeight: "800", color: "#0f172a", margin: 0 }}>User Profile</h1>
        {!isEditing && (
          <button
            onClick={handleStartEditing}
            style={{
              background: "#dc2626",
              color: "white",
              border: "none",
              padding: "5px 12px",
              borderRadius: "6px",
              fontWeight: "600",
              fontSize: "12px",
              cursor: "pointer",
              width: "auto",
              flexShrink: 0,
            }}
            onMouseEnter={(e) => (e.target.style.background = "#b91c1c")}
            onMouseLeave={(e) => (e.target.style.background = "#dc2626")}
          >
            Edit Details
          </button>
        )}
      </div>

      {error && (
        <div style={{ color: "#dc2626", background: "#fef2f2", padding: "12px 16px", borderRadius: "10px", marginBottom: "16px", fontSize: "14px" }}>
          {error}
        </div>
      )}

      {success && (
        <div style={{ color: "#16a34a", background: "#f0fdf4", padding: "12px 16px", borderRadius: "10px", marginBottom: "16px", fontSize: "14px" }}>
          {success}
        </div>
      )}

      <div style={{ background: "white", borderRadius: "16px", padding: "24px", border: "1px solid #e2e8f0", boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.05)" }}>

        {/* Profile Picture Section */}
        <div style={{ display: "flex", alignItems: "center", gap: "20px", marginBottom: "24px", paddingBottom: "20px", borderBottom: "1px solid #e2e8f0" }}>
          <div style={{ position: "relative", width: "80px", height: "80px", borderRadius: "50%", overflow: "hidden", background: "#e2e8f0", display: "flex", alignItems: "center", justifyContent: "center", border: "2px solid #cbd5e1", flexShrink: 0 }}>
            {effectiveAvatar && !imgError ? (
              <img src={effectiveAvatar} alt="Profile" onError={() => setImgError(true)} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
            ) : (
              <span style={{ fontSize: "30px", fontWeight: "700", color: "#64748b" }}>
                {profile?.name ? profile.name.charAt(0).toUpperCase() : "U"}
              </span>
            )}
          </div>

          <div>
            <h3 style={{ margin: "0 0 4px", fontSize: "16px", color: "#0f172a" }}>Profile Picture</h3>
            <p style={{ fontSize: "13px", color: "#64748b", margin: "0 0 10px" }}>
              Upload a JPEG, PNG, or WebP image (max 5 MB).
            </p>
            <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
              <label style={{ background: "#f1f5f9", color: "#334155", padding: "6px 14px", borderRadius: "8px", fontWeight: "600", fontSize: "12px", cursor: "pointer", border: "1px solid #cbd5e1" }}>
                Select Image
                <input type="file" accept="image/jpeg,image/png,image/webp" onChange={handleFileChange} style={{ display: "none" }} />
              </label>

              {selectedFile && (
                <button
                  onClick={handleUploadPicture}
                  disabled={uploadingPic}
                  style={{ background: "#2563eb", color: "white", border: "none", padding: "6px 14px", borderRadius: "8px", fontWeight: "600", fontSize: "12px", cursor: "pointer" }}
                >
                  {uploadingPic ? "Saving..." : "Save Picture"}
                </button>
              )}

              {profile?.customPicture && !selectedFile && (
                <button
                  onClick={handleDeletePicture}
                  disabled={uploadingPic}
                  style={{ background: "#fee2e2", color: "#dc2626", border: "none", padding: "6px 14px", borderRadius: "8px", fontWeight: "600", fontSize: "12px", cursor: "pointer" }}
                >
                  Remove Custom Picture
                </button>
              )}
            </div>
          </div>
        </div>

        {/* View Mode */}
        {!isEditing ? (
          <div>
            <div style={{ marginBottom: "16px" }}>
              <label style={{ display: "block", fontSize: "12px", fontWeight: "600", color: "#64748b", marginBottom: "4px" }}>
                Display Name
              </label>
              <div style={{ fontSize: "15px", fontWeight: "600", color: "#0f172a" }}>
                {profile?.name || "Not provided"}
              </div>
            </div>

            <div style={{ marginBottom: "16px" }}>
              <label style={{ display: "block", fontSize: "12px", fontWeight: "600", color: "#64748b", marginBottom: "4px" }}>
                Favourite Place or City
              </label>
              <div style={{ fontSize: "15px", fontWeight: "600", color: "#0f172a" }}>
                {profile?.favouritePlace || "Not provided"}
              </div>
            </div>

            <div style={{ marginBottom: "16px" }}>
              <label style={{ display: "block", fontSize: "12px", fontWeight: "600", color: "#64748b", marginBottom: "4px" }}>
                City Where You Live
              </label>
              <div style={{ fontSize: "15px", fontWeight: "600", color: "#0f172a" }}>
                {profile?.cityOfResidence || "Not provided"}
              </div>
            </div>

            <div style={{ marginBottom: "16px" }}>
              <label style={{ display: "block", fontSize: "12px", fontWeight: "600", color: "#64748b", marginBottom: "4px" }}>
                Email Address
              </label>
              <div style={{ fontSize: "15px", fontWeight: "600", color: "#64748b" }}>
                {profile?.email || "Not provided"}
              </div>
            </div>

            <div style={{ marginTop: "20px", paddingTop: "16px", borderTop: "1px solid #e2e8f0" }}>
              <span style={{ fontSize: "13px", color: "#64748b" }}>
                Member Since: <strong>{profile?.createdAt ? new Date(profile.createdAt).toLocaleDateString() : "N/A"}</strong>
              </span>
            </div>
          </div>
        ) : (
          /* Edit Details Mode */
          <form onSubmit={handleSaveProfile}>
            <div style={{ marginBottom: "16px" }}>
              <label style={{ display: "block", fontSize: "13px", fontWeight: "600", color: "#334155", marginBottom: "6px" }}>
                Display Name
              </label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                style={{ width: "100%", padding: "10px 14px", borderRadius: "8px", border: "1px solid #cbd5e1", fontSize: "14px", outline: "none", boxSizing: "border-box" }}
                required
              />
            </div>

            <div style={{ marginBottom: "16px" }}>
              <label style={{ display: "block", fontSize: "13px", fontWeight: "600", color: "#334155", marginBottom: "6px" }}>
                Favourite Place or City
              </label>
              <input
                type="text"
                value={favouritePlace}
                onChange={(e) => setFavouritePlace(e.target.value)}
                placeholder="e.g. Paris"
                maxLength={100}
                style={{ width: "100%", padding: "10px 14px", borderRadius: "8px", border: "1px solid #cbd5e1", fontSize: "14px", outline: "none", boxSizing: "border-box" }}
              />
            </div>

            <div style={{ marginBottom: "16px" }}>
              <label style={{ display: "block", fontSize: "13px", fontWeight: "600", color: "#334155", marginBottom: "6px" }}>
                City Where You Live
              </label>
              <input
                type="text"
                value={cityOfResidence}
                onChange={(e) => setCityOfResidence(e.target.value)}
                placeholder="e.g. New York"
                maxLength={100}
                style={{ width: "100%", padding: "10px 14px", borderRadius: "8px", border: "1px solid #cbd5e1", fontSize: "14px", outline: "none", boxSizing: "border-box" }}
              />
            </div>

            <div style={{ marginBottom: "20px" }}>
              <label style={{ display: "block", fontSize: "13px", fontWeight: "600", color: "#334155", marginBottom: "6px" }}>
                Email Address
              </label>
              <input
                type="email"
                value={profile?.email || ""}
                disabled
                style={{ width: "100%", padding: "10px 14px", borderRadius: "8px", border: "1px solid #e2e8f0", background: "#f8fafc", fontSize: "14px", color: "#64748b", boxSizing: "border-box" }}
              />
              <span style={{ fontSize: "12px", color: "#94a3b8", marginTop: "4px", display: "block" }}>Email address cannot be changed.</span>
            </div>

            <div style={{ display: "flex", gap: "12px" }}>
              <button
                type="submit"
                disabled={savingProfile}
                style={{ background: "#2563eb", color: "white", border: "none", padding: "10px 20px", borderRadius: "8px", fontWeight: "600", fontSize: "14px", cursor: "pointer", flex: 1 }}
              >
                {savingProfile ? "Saving Changes..." : "Save Changes"}
              </button>
              <button
                type="button"
                onClick={handleCancelEditing}
                disabled={savingProfile}
                style={{ background: "#f1f5f9", color: "#334155", border: "1px solid #cbd5e1", padding: "10px 20px", borderRadius: "8px", fontWeight: "600", fontSize: "14px", cursor: "pointer" }}
              >
                Cancel
              </button>
            </div>
          </form>
        )}

      </div>
    </div>
  );
};

export default Profile;
