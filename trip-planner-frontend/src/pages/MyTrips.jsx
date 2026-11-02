import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getMyTrips, deleteTrip } from "../api/tripApi";
import { useTrip } from "../context/useTrip";
import { useAuth } from "../context/useAuth";

const MyTrips = () => {
  const { myTrips, setMyTrips, setItinerary } = useTrip();
  const [loading, setLoading] = useState(!myTrips);
  const [error, setError] = useState(null);
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    document.title = "My Trips | TripPlanner";
    if (!isAuthenticated) {
      navigate("/login", { replace: true });
      return;
    }

    if (myTrips) {
      setLoading(false);
      return;
    }

    const fetchTrips = async () => {
      try {
        setLoading(true);
        const data = await getMyTrips();
        setMyTrips(data || []);
      } catch (err) {
        setError(err.response?.data?.message || err.message || "Failed to load trips");
      } finally {
        setLoading(false);
      }
    };

    fetchTrips();
  }, [isAuthenticated, navigate, myTrips, setMyTrips]);

  const trips = myTrips || [];

  const handleOpenTrip = (trip) => {
    setItinerary(null);
    navigate(`/result/${trip.id}`);
  };

  const handleDeleteTrip = async (e, tripId) => {
    e.stopPropagation();
    if (!window.confirm("Are you sure you want to delete this trip?")) return;
    try {
      await deleteTrip(tripId);
      setMyTrips(trips.filter(t => t.id !== tripId));
    } catch (err) {
      console.error(err);
      alert("Failed to delete trip");
    }
  };

  if (loading) {
    return (
      <div style={{ padding: "80px 24px", textAlign: "center", color: "var(--text-secondary)", flex: 1 }}>
        Loading your trips...
      </div>
    );
  }

  return (
    <div 
      style={{ 
        width: "100%", 
        flex: 1, 
        paddingBottom: "60px",
        backgroundImage: `linear-gradient(rgba(37, 42, 36, 0.5), rgba(37, 42, 36, 0.7)), url('/images/My-trips-hero.jpg')`,
        backgroundSize: "cover",
        backgroundPosition: "center",
        backgroundRepeat: "no-repeat",
        backgroundAttachment: "scroll",
        minHeight: "calc(100vh - 70px)"
      }}
    >
      <div
        style={{
          width: "100%",
          padding: "50px 40px 30px",
          color: "#ffffff"
        }}
      >
        <div style={{ maxWidth: "1000px", margin: "0 auto", display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "20px", zIndex: 2 }}>
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
              Saved Itineraries
            </span>
            <h1 style={{ fontSize: "32px", fontWeight: "800", color: "#FFFFFF", margin: 0, fontFamily: "var(--font-heading)" }}>
              My Trips
            </h1>
            <p style={{ fontSize: "15px", color: "#E9E3D6", margin: "6px 0 0" }}>
              Access and manage all your planned travel itineraries in one place.
            </p>
          </div>
          <Link 
            to="/" 
            style={{ background: "var(--brand-primary)", color: "white", padding: "12px 24px", borderRadius: "6px", fontWeight: "600", fontSize: "14px", textDecoration: "none", zIndex: 2, border: "1px solid rgba(255,255,255,0.2)" }}
          >
            Plan New Trip
          </Link>
        </div>
      </div>

      <div style={{ maxWidth: "1000px", margin: "0 auto", padding: "20px 20px 0" }}>
      {error && (
        <div style={{ color: "#991b1b", background: "#fef2f2", padding: "16px", borderRadius: "8px", marginBottom: "24px", border: "1px solid #fca5a5" }}>
          {error}
        </div>
      )}

      {trips.length === 0 ? (
        <div style={{ textAlign: "center", background: "var(--surface-primary)", padding: "60px 20px", borderRadius: "12px", border: "1px solid var(--color-border)", boxShadow: "0 1px 3px rgba(37, 42, 36, 0.04)" }}>
          <h2 style={{ fontSize: "20px", color: "var(--text-primary)", marginBottom: "8px", fontFamily: "var(--font-heading)" }}>No trips yet.</h2>
          <p style={{ color: "var(--text-secondary)", marginBottom: "24px" }}>You haven't created any travel itineraries yet.</p>
          <Link 
            to="/" 
            style={{ display: "inline-block", background: "var(--brand-primary)", color: "white", padding: "12px 24px", borderRadius: "6px", fontWeight: "600" }}
          >
            Plan Your First Trip
          </Link>
        </div>
      ) : (
        <div style={{ display: "grid", gap: "16px" }}>
          {trips.map((trip) => (
            <div
              key={trip.id}
              onClick={() => handleOpenTrip(trip)}
              style={{
                background: "var(--surface-primary)",
                borderRadius: "12px",
                padding: "24px",
                border: "1px solid var(--color-border)",
                boxShadow: "0 1px 3px rgba(37, 42, 36, 0.04)",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                cursor: "pointer",
                transition: "border-color 0.15s ease"
              }}
              onMouseEnter={(e) => { e.currentTarget.style.borderColor = "var(--brand-primary)"; }}
              onMouseLeave={(e) => { e.currentTarget.style.borderColor = "var(--color-border)"; }}
            >
              <div>
                <h3 style={{ margin: "0 0 8px", fontSize: "18px", color: "var(--text-primary)", fontFamily: "var(--font-heading)" }}>
                  {trip.origin} → {trip.destination}
                </h3>
                <div style={{ display: "flex", gap: "16px", fontSize: "13px", color: "var(--text-secondary)" }}>
                  <span>{trip.startDate} to {trip.endDate}</span>
                  <span>{trip.days} Days</span>
                </div>
              </div>
              <div style={{ display: "flex", gap: "12px", alignItems: "center" }}>
                <button
                  onClick={(e) => handleDeleteTrip(e, trip.id)}
                  style={{
                    background: "#fef2f2",
                    color: "#991b1b",
                    border: "1px solid #fca5a5",
                    padding: "8px 14px",
                    borderRadius: "6px",
                    fontWeight: "600",
                    cursor: "pointer",
                    fontSize: "13px"
                  }}
                >
                  Delete
                </button>
                <span style={{ color: "var(--brand-primary)", fontWeight: "600", fontSize: "14px" }}>View →</span>
              </div>
            </div>
          ))}
        </div>
      )}
      </div>
    </div>
  );
};

export default MyTrips;
