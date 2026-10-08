import { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getMyTrips, deleteTrip } from "../api/tripApi";
import { useTrip } from "../context/useTrip";
import { useAuth } from "../context/useAuth";

const MyTrips = () => {
  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { setItinerary } = useTrip();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    document.title = "My Trips | TripPlanner";
    if (!isAuthenticated) {
      navigate("/login", { replace: true });
      return;
    }

    const fetchTrips = async () => {
      try {
        setLoading(true);
        const data = await getMyTrips();
        setTrips(data || []);
      } catch (err) {
        setError(err.response?.data?.message || err.message || "Failed to load trips");
      } finally {
        setLoading(false);
      }
    };

    fetchTrips();
  }, [isAuthenticated, navigate]);

  const handleOpenTrip = (trip) => {
    setItinerary(trip);
    navigate("/result");
  };

  const handleDeleteTrip = async (e, tripId) => {
    e.stopPropagation();
    if (!window.confirm("Are you sure you want to delete this trip?")) return;
    try {
      await deleteTrip(tripId);
      setTrips(trips.filter(t => t.id !== tripId));
    } catch (err) {
      console.error(err);
      alert("Failed to delete trip");
    }
  };

  if (loading) {
    return (
      <div style={{ padding: "80px 24px", textAlign: "center", color: "#64748b" }}>
        Loading your trips...
      </div>
    );
  }

  return (
    <div style={{ maxWidth: "1000px", margin: "40px auto", padding: "0 20px", width: "100%", flex: 1 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "32px" }}>
        <h1 style={{ fontSize: "28px", fontWeight: "800", color: "#0f172a", margin: 0 }}>My Trips</h1>
        <Link 
          to="/" 
          style={{ background: "#2563eb", color: "white", padding: "10px 20px", borderRadius: "10px", fontWeight: "600", fontSize: "14px" }}
        >
          Plan New Trip
        </Link>
      </div>

      {error && (
        <div style={{ color: "#dc2626", background: "#fef2f2", padding: "16px", borderRadius: "12px", marginBottom: "24px" }}>
          {error}
        </div>
      )}

      {trips.length === 0 ? (
        <div style={{ textAlign: "center", background: "white", padding: "60px 20px", borderRadius: "20px", border: "1px solid #e2e8f0" }}>
          <h2 style={{ fontSize: "20px", color: "#1e293b", marginBottom: "8px" }}>No trips yet.</h2>
          <p style={{ color: "#64748b", marginBottom: "24px" }}>You haven't created any travel itineraries yet.</p>
          <Link 
            to="/" 
            style={{ display: "inline-block", background: "#2563eb", color: "white", padding: "12px 24px", borderRadius: "12px", fontWeight: "600" }}
          >
            Plan Your First Trip
          </Link>
        </div>
      ) : (
        <div style={{ display: "grid", gap: "20px" }}>
          {trips.map((trip) => (
            <div
              key={trip.id}
              onClick={() => handleOpenTrip(trip)}
              style={{
                background: "white",
                borderRadius: "16px",
                padding: "24px",
                border: "1px solid #e2e8f0",
                boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.05)",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                cursor: "pointer",
                transition: "all 0.15s ease"
              }}
              onMouseEnter={(e) => { e.currentTarget.style.borderColor = "#2563eb"; }}
              onMouseLeave={(e) => { e.currentTarget.style.borderColor = "#e2e8f0"; }}
            >
              <div>
                <h3 style={{ margin: "0 0 8px", fontSize: "20px", color: "#0f172a" }}>
                  {trip.origin} → {trip.destination}
                </h3>
                <div style={{ display: "flex", gap: "16px", fontSize: "14px", color: "#64748b" }}>
                  <span>{trip.startDate} to {trip.endDate}</span>
                  <span>{trip.days} Days</span>
                </div>
              </div>
              <div style={{ display: "flex", gap: "12px", alignItems: "center" }}>
                <button
                  onClick={(e) => handleDeleteTrip(e, trip.id)}
                  style={{
                    background: "#fee2e2",
                    color: "#dc2626",
                    border: "none",
                    padding: "8px 14px",
                    borderRadius: "8px",
                    fontWeight: "600",
                    cursor: "pointer",
                    fontSize: "13px"
                  }}
                >
                  Delete
                </button>
                <span style={{ color: "#2563eb", fontWeight: "600", fontSize: "14px" }}>View →</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default MyTrips;
