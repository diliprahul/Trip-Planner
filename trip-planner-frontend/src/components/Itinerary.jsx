import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useTrip } from "../context/useTrip";
import { MapContainer, TileLayer, Marker, Popup } from "react-leaflet";
import L from "leaflet";
import WeatherForecast from "./WeatherForecast";

// Fix for default marker icons in Leaflet + React
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";

let DefaultIcon = L.icon({
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
});

L.Marker.prototype.options.icon = DefaultIcon;

const Itinerary = () => {
  const { itinerary } = useTrip();
  const navigate = useNavigate();

  useEffect(() => {
    if (!itinerary) {
      navigate("/", { replace: true });
    }
  }, [itinerary, navigate]);

  if (!itinerary) return null;

  const open = (url) => {
    if (url) window.open(url, "_blank", "noopener,noreferrer");
  };

  const center = [itinerary.latitude || 20.5937, itinerary.longitude || 78.9629];

  return (
    <div style={{ background: "#f1f5f9", minHeight: "100vh", paddingBottom: "80px" }}>
      {/* HEADER */}
      <div
        style={{
          background: "linear-gradient(135deg, #0f172a, #1e293b)",
          color: "white",
          padding: "60px 24px",
          textAlign: "center",
        }}
      >
        <h1 style={{ fontSize: "36px", fontWeight: "800", marginBottom: "12px", letterSpacing: "-0.025em" }}>
          {itinerary.origin} <span style={{ color: "#94a3b8", fontWeight: "300" }}>→</span> {itinerary.destination}
        </h1>
        <div style={{ display: "flex", justifyContent: "center", gap: "12px", fontSize: "16px" }}>
          <span style={{ background: "rgba(255,255,255,0.1)", padding: "6px 16px", borderRadius: "99px" }}>
            📅 {itinerary.startDate} → {itinerary.endDate}
          </span>
          <span style={{ background: "rgba(255,255,255,0.1)", padding: "6px 16px", borderRadius: "99px" }}>
            ⏱️ {itinerary.days} Days
          </span>
        </div>
      </div>

      <div style={{ maxWidth: "1000px", margin: "auto", padding: "0 20px" }}>
        
        {/* SECTION 1: CITY MAP */}
        <div style={{ marginTop: "-40px", background: "white", borderRadius: "24px", overflow: "hidden", boxShadow: "0 20px 25px -5px rgba(0, 0, 0, 0.1)", border: "1px solid #e2e8f0" }}>
           <div style={{ height: "400px", width: "100%", zIndex: 1 }}>
              <MapContainer center={center} zoom={13} style={{ height: "100%", width: "100%" }}>
                <TileLayer
                  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                />
                <Marker position={center}>
                  <Popup><b>{itinerary.destination}</b><br/>City Center</Popup>
                </Marker>
                {itinerary.dayPlans.map((plan, idx) => (
                  plan.latitude && plan.longitude && (
                    <Marker key={idx} position={[plan.latitude, plan.longitude]}>
                      <Popup>
                        <b>Day {plan.dayNumber}: {plan.placeName}</b><br/>
                        {plan.description}
                      </Popup>
                    </Marker>
                  )
                ))}
              </MapContainer>
           </div>
           <div style={{ padding: "20px", textAlign: "center", borderTop: "1px solid #f1f5f9" }}>
              <p style={{ color: "#64748b", fontSize: "14px", margin: 0 }}>
                Showing <b>{itinerary.destination}</b> and <b>{itinerary.dayPlans.length}</b> recommended attractions
              </p>
           </div>
        </div>

        {/* SECTION 2: WEATHER */}
        {itinerary.weatherResponse && (
          <WeatherForecast weatherResponse={itinerary.weatherResponse} />
        )}

        {/* SECTION 3: ITINERARY */}
        <h2 style={{ marginTop: "64px", marginBottom: "32px", fontSize: "28px", color: "#0f172a" }}>
          Daily Itinerary
        </h2>
        <div style={{ display: "grid", gap: "24px" }}>
          {itinerary.dayPlans.map((plan) => (
            <div
              key={plan.dayNumber}
              style={{
                background: "white",
                borderRadius: "20px",
                padding: "24px",
                display: "flex",
                gap: "24px",
                alignItems: "flex-start",
                boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.1)",
                border: "1px solid #e2e8f0",
              }}
            >
              <div style={{
                background: "#3b82f6",
                color: "white",
                minWidth: "60px",
                height: "60px",
                borderRadius: "16px",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                fontWeight: "bold"
              }}>
                <span style={{ fontSize: "12px", opacity: 0.8 }}>DAY</span>
                <span style={{ fontSize: "24px" }}>{plan.dayNumber}</span>
              </div>
              
              <div style={{ flex: 1 }}>
                <h3 style={{ margin: "0 0 8px", fontSize: "20px", color: "#1e293b" }}>{plan.placeName}</h3>
                <p style={{ margin: "0 0 16px", color: "#64748b", lineHeight: "1.6" }}>{plan.description}</p>
                <button
                  onClick={() => open(plan.mapsUrl)}
                  style={{
                    background: "#f1f5f9",
                    color: "#334155",
                    border: "1px solid #e2e8f0",
                    padding: "8px 16px",
                    borderRadius: "10px",
                    fontWeight: "600",
                    cursor: "pointer",
                    fontSize: "14px",
                    display: "flex",
                    alignItems: "center",
                    gap: "8px",
                    transition: "all 0.2s"
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = "#e2e8f0";
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = "#f1f5f9";
                  }}
                >
                  📍 Get Directions
                </button>
              </div>
            </div>
          ))}
        </div>

        {/* SECTION 3: HOTELS */}
        <h2 style={{ marginTop: "64px", marginBottom: "32px", fontSize: "28px", color: "#0f172a" }}>
          Where to Stay
        </h2>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))", gap: "20px" }}>
          {itinerary.hotels.length === 0 && (
            <p style={{ color: "#64748b", margin: 0 }}>
              No named accommodations were returned by OpenStreetMap for this area. Try generating again later.
            </p>
          )}
          {itinerary.hotels.map((hotel, i) => (
            <div
              key={i}
              style={{
                background: "white",
                padding: "24px",
                borderRadius: "20px",
                border: "1px solid #e2e8f0",
                boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.1)",
                display: "flex",
                flexDirection: "column",
                justifyContent: "space-between"
              }}
            >
              <div>
                <h4 style={{ margin: "0 0 8px", fontSize: "18px", color: "#1e293b" }}>{hotel.name}</h4>
                <p style={{ fontSize: "14px", color: "#64748b", margin: "0 0 20px" }}>{hotel.address || "Near attraction area"}</p>
              </div>
              <button
                onClick={() => open(hotel.searchUrl)}
                style={{
                  background: "#10b981",
                  color: "white",
                  border: "none",
                  padding: "10px",
                  borderRadius: "10px",
                  fontWeight: "600",
                  cursor: "pointer",
                  fontSize: "14px"
                }}
              >
                Get Directions
              </button>
            </div>
          ))}
        </div>

        {/* SECTION 4: BUDGET */}
        <h2 style={{ marginTop: "64px", marginBottom: "8px", fontSize: "28px", color: "#0f172a" }}>
          Estimated Budget
        </h2>
        <p style={{ color: "#64748b", marginBottom: "32px" }}>
          Estimates per traveler for {itinerary.days} days in {itinerary.destination}; they are planning ranges, not live hotel, transport, or ticket prices.
        </p>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: "24px" }}>
          {itinerary.budgetEstimates.map((tier, i) => (
            <div key={i} style={{
              background: tier.budgetCategory === "Standard" ? "#eff6ff" : "white",
              padding: "32px",
              borderRadius: "24px",
              border: tier.budgetCategory === "Standard" ? "2px solid #3b82f6" : "1px solid #e2e8f0",
              boxShadow: "0 10px 15px -3px rgba(0, 0, 0, 0.1)",
              position: "relative"
            }}>
              {tier.budgetCategory === "Standard" && (
                <span style={{
                  position: "absolute",
                  top: "-12px",
                  left: "50%",
                  transform: "translateX(-50%)",
                  background: "#3b82f6",
                  color: "white",
                  padding: "4px 16px",
                  borderRadius: "99px",
                  fontSize: "12px",
                  fontWeight: "bold"
                }}>RECOMMENDED</span>
              )}
              <h3 style={{ textAlign: "center", margin: "0 0 8px", fontSize: "24px", color: "#1e293b" }}>{tier.budgetCategory}</h3>
              <p style={{ textAlign: "center", color: "#3b82f6", fontSize: "32px", fontWeight: "800", margin: "0 0 24px" }}>
                ₹{tier.minTotal.toLocaleString()} - ₹{tier.maxTotal.toLocaleString()}
              </p>
              
              <div style={{ display: "grid", gap: "12px", fontSize: "14px", borderTop: "1px solid #e2e8f0", paddingTop: "24px" }}>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ color: "#64748b" }}>Accommodation</span>
                  <span style={{ fontWeight: "600" }}>₹{tier.minAccommodation} - ₹{tier.maxAccommodation}</span>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ color: "#64748b" }}>Food & Dining</span>
                  <span style={{ fontWeight: "600" }}>₹{tier.minFood} - ₹{tier.maxFood}</span>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ color: "#64748b" }}>Transport</span>
                  <span style={{ fontWeight: "600" }}>₹{tier.minTransport} - ₹{tier.maxTransport}</span>
                </div>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span style={{ color: "#64748b" }}>Attractions</span>
                  <span style={{ fontWeight: "600" }}>₹{tier.minAttractions} - ₹{tier.maxAttractions}</span>
                </div>
              </div>
              
              <p style={{ marginTop: "24px", fontSize: "12px", color: "#94a3b8", fontStyle: "italic" }}>
                Includes: {tier.assumptions}
              </p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default Itinerary;
