import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useTrip } from "../context/useTrip";
import { useAuth } from "../context/useAuth";
import { getTripById, generateItinerary, backfillCoordinates } from "../api/tripApi";
import { MapContainer, TileLayer, Marker, Popup, useMap } from "react-leaflet";
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

const hasCoordinates = (latitude, longitude) =>
  latitude !== null && latitude !== undefined && latitude !== ""
  && longitude !== null && longitude !== undefined && longitude !== ""
  && Number.isFinite(Number(latitude)) && Number.isFinite(Number(longitude))
  && Number(latitude) >= -90 && Number(latitude) <= 90
  && Number(longitude) >= -180 && Number(longitude) <= 180;

const MapBounds = ({ center, dayPlans }) => {
  const map = useMap();

  useEffect(() => {
    const attractionPositions = (dayPlans || [])
      .filter((plan) => hasCoordinates(plan.latitude, plan.longitude))
      .map((plan) => [Number(plan.latitude), Number(plan.longitude)]);

    const validCenter = hasCoordinates(center?.[0], center?.[1]) ? center : null;

    if (validCenter) {
      if (attractionPositions.length > 0) {
        map.fitBounds([validCenter, ...attractionPositions], { padding: [40, 40], maxZoom: 13 });
      } else {
        map.setView(validCenter, 13);
      }
    }
  }, [map, center, dayPlans]);

  return null;
};

const Itinerary = () => {
  const { itinerary, setItinerary } = useTrip();
  const { tripId } = useParams();
  const { loading: authLoading, isAuthenticated } = useAuth();
  const [fetching, setFetching] = useState(false);
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (authLoading) return;

    if (!isAuthenticated) {
      navigate("/login", { replace: true });
      return;
    }

    if (tripId) {
      if (!itinerary || itinerary.id !== Number(tripId)) {
        const loadTrip = async () => {
          setFetching(true);
          setError(null);
          try {
            let data = await getTripById(tripId);
            if (!data.dayPlans || data.dayPlans.length === 0) {
              data = await generateItinerary(tripId);
            }
            const hasValidCoords = (data.dayPlans || []).some(p => hasCoordinates(p.latitude, p.longitude));
            if (!hasValidCoords && data.dayPlans && data.dayPlans.length > 0) {
              try {
                await backfillCoordinates(tripId);
                data = await getTripById(tripId);
              } catch (backfillErr) {
                console.warn("Automatic backfill attempt failed:", backfillErr);
              }
            }
            setItinerary(data);
          } catch (err) {
            console.error(err);
            setError(err.response?.data?.message || err.message || "Failed to load trip itinerary");
          } finally {
            setFetching(false);
          }
        };
        loadTrip();
      }
    } else if (!itinerary) {
      navigate("/my-trips", { replace: true });
    }
  }, [tripId, itinerary, authLoading, isAuthenticated, setItinerary, navigate]);

  if (authLoading || fetching) {
    return (
      <div style={{ padding: "100px 24px", textAlign: "center", color: "var(--text-secondary)", flex: 1 }}>
        Loading itinerary...
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: "100px 24px", textAlign: "center", color: "#991b1b", flex: 1 }}>
        <h2>Error Loading Itinerary</h2>
        <p>{error}</p>
        <button
          onClick={() => navigate("/my-trips")}
          style={{ marginTop: "16px", background: "var(--brand-primary)", color: "white", border: "none", padding: "10px 20px", borderRadius: "6px", cursor: "pointer", fontWeight: "600" }}
        >
          Go to My Trips
        </button>
      </div>
    );
  }

  if (!itinerary) return null;

  const open = (url) => {
    if (url) window.open(url, "_blank", "noopener,noreferrer");
  };

  const center = hasCoordinates(itinerary.latitude, itinerary.longitude)
    ? [Number(itinerary.latitude), Number(itinerary.longitude)]
    : [20.5937, 78.9629];
  const validAttractions = (itinerary.dayPlans || []).filter((plan) =>
    hasCoordinates(plan.latitude, plan.longitude)
  );

  return (
    <div 
      style={{ 
        backgroundImage: `linear-gradient(rgba(37, 42, 36, 0.55), rgba(37, 42, 36, 0.75)), url('/images/iternary-page-hero.jpg')`,
        backgroundSize: "cover",
        backgroundPosition: "center",
        backgroundRepeat: "no-repeat",
        backgroundAttachment: "scroll",
        minHeight: "calc(100vh - 70px)", 
        paddingBottom: "80px",
        width: "100%",
        flex: 1
      }}
    >
      {/* HEADER */}
      <div
        style={{
          width: "100%",
          color: "white",
          padding: "60px 24px 30px",
          textAlign: "center"
        }}
      >
        <span style={{
          fontFamily: "var(--font-heading)",
          fontSize: "11px",
          fontWeight: "700",
          letterSpacing: "1.5px",
          textTransform: "uppercase",
          color: "#E9E3D6",
          background: "rgba(36, 75, 60, 0.85)",
          padding: "4px 12px",
          borderRadius: "4px",
          display: "inline-block",
          marginBottom: "12px"
        }}>
          Itinerary Overview
        </span>
        <h1 style={{ fontFamily: "var(--font-heading)", fontSize: "36px", fontWeight: "800", marginBottom: "12px", letterSpacing: "-0.02em", color: "#FFFFFF" }}>
          {itinerary.origin} <span style={{ color: "#E9E3D6", fontWeight: "300" }}>→</span> {itinerary.destination}
        </h1>
        <div style={{ display: "flex", justifyContent: "center", gap: "12px", fontSize: "15px" }}>
          <span style={{ background: "rgba(255,255,255,0.15)", padding: "6px 16px", borderRadius: "6px", fontWeight: "500" }}>
            {itinerary.startDate} to {itinerary.endDate}
          </span>
          <span style={{ background: "rgba(255,255,255,0.15)", padding: "6px 16px", borderRadius: "6px", fontWeight: "500" }}>
            {itinerary.days} Days
          </span>
        </div>
      </div>

      <div style={{ maxWidth: "1000px", margin: "auto", padding: "20px 20px 0" }}>
        
        {/* SECTION 1: CITY MAP */}
        <div style={{ background: "var(--surface-primary)", borderRadius: "12px", overflow: "hidden", boxShadow: "0 4px 6px -1px rgba(37, 42, 36, 0.05)", border: "1px solid var(--color-border)" }}>
           <div style={{ height: "400px", width: "100%", zIndex: 1 }}>
              <MapContainer key={`${center[0]}-${center[1]}-${validAttractions.length}`} center={center} zoom={13} style={{ height: "100%", width: "100%" }}>
                <TileLayer
                  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                />
                <Marker position={center}>
                  <Popup><b>{itinerary.destination}</b><br/>City Center</Popup>
                </Marker>
                <MapBounds center={center} dayPlans={validAttractions} />
                {validAttractions.map((plan) => (
                    <Marker key={plan.dayNumber} position={[Number(plan.latitude), Number(plan.longitude)]}>
                      <Popup>
                        <b>Day {plan.dayNumber}: {plan.placeName}</b><br/>
                        {plan.description}<br/>
                        {plan.mapsUrl && <a href={plan.mapsUrl} target="_blank" rel="noreferrer">Open in Google Maps</a>}
                      </Popup>
                    </Marker>
                ))}
              </MapContainer>
           </div>
           <div style={{ padding: "16px 20px", textAlign: "center", borderTop: "1px solid var(--color-border)", background: "var(--surface-secondary)" }}>
              <p style={{ color: "var(--text-secondary)", fontSize: "13px", margin: 0 }}>
                {validAttractions.length > 0
                  ? <>Showing <b>{validAttractions.length}</b> attraction pin{validAttractions.length === 1 ? "" : "s"} in <b>{itinerary.destination}</b>.</>
                  : <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "8px" }}>
                      <span>No attraction coordinates are available yet. Run the authenticated coordinate backfill to add only verified locations.</span>
                      <button
                        onClick={async () => {
                          try {
                            setFetching(true);
                            await backfillCoordinates(tripId);
                            const updated = await getTripById(tripId);
                            setItinerary(updated);
                          } catch (e) {
                            console.error(e);
                            setError("Failed to backfill coordinates");
                          } finally {
                            setFetching(false);
                          }
                        }}
                        style={{ background: "var(--brand-primary)", color: "white", border: "none", padding: "6px 14px", borderRadius: "4px", cursor: "pointer", fontSize: "12px", fontWeight: "600" }}
                      >
                        Run Coordinate Backfill
                      </button>
                    </div>}
              </p>
           </div>
        </div>

        {/* SECTION 2: WEATHER */}
        {itinerary.weatherResponse && (
          <WeatherForecast weatherResponse={itinerary.weatherResponse} />
        )}

        {/* SECTION 3: ITINERARY */}
        <h2 style={{ marginTop: "56px", marginBottom: "24px", fontSize: "24px", color: "var(--text-primary)", fontFamily: "var(--font-heading)" }}>
          Daily Itinerary
        </h2>
        <div style={{ display: "grid", gap: "20px" }}>
          {itinerary.dayPlans?.map((plan) => (
            <div
              key={plan.dayNumber}
              style={{
                background: "var(--surface-primary)",
                borderRadius: "12px",
                padding: "24px",
                display: "flex",
                gap: "24px",
                alignItems: "flex-start",
                boxShadow: "0 1px 3px rgba(37, 42, 36, 0.04)",
                border: "1px solid var(--color-border)",
              }}
            >
              <div style={{
                background: "var(--surface-secondary)",
                color: "var(--brand-primary)",
                minWidth: "60px",
                height: "60px",
                borderRadius: "8px",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                fontWeight: "700",
                border: "1px solid var(--color-border)"
              }}>
                <span style={{ fontSize: "11px", color: "var(--text-secondary)", letterSpacing: "0.05em" }}>DAY</span>
                <span style={{ fontSize: "22px", fontFamily: "var(--font-heading)" }}>{plan.dayNumber}</span>
              </div>
              
              <div style={{ flex: 1 }}>
                <h3 style={{ margin: "0 0 8px", fontSize: "18px", color: "var(--text-primary)", fontFamily: "var(--font-heading)" }}>{plan.placeName}</h3>
                <p style={{ margin: "0 0 16px", color: "var(--text-secondary)", lineHeight: "1.6", fontSize: "14px" }}>{plan.description}</p>
                <button
                  onClick={() => open(plan.mapsUrl)}
                  style={{
                    background: "var(--surface-secondary)",
                    color: "var(--text-primary)",
                    border: "1px solid var(--color-border)",
                    padding: "8px 16px",
                    borderRadius: "6px",
                    fontWeight: "600",
                    cursor: "pointer",
                    fontSize: "13px",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "8px",
                    transition: "background-color 0.15s ease"
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = "#ded7c5";
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = "var(--surface-secondary)";
                  }}
                >
                  Get Directions
                </button>
              </div>
            </div>
          ))}
        </div>

        {/* SECTION 3: HOTELS */}
        <h2 style={{ marginTop: "56px", marginBottom: "24px", fontSize: "24px", color: "var(--text-primary)", fontFamily: "var(--font-heading)" }}>
          Where to Stay
        </h2>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: "20px" }}>
          {(!itinerary.hotels || itinerary.hotels.length === 0) && (
            <p style={{ color: "var(--text-secondary)", margin: 0 }}>
              No named accommodations were returned by OpenStreetMap for this area. Try generating again later.
            </p>
          )}
          {itinerary.hotels?.map((hotel, i) => (
            <div
              key={i}
              style={{
                background: "var(--surface-primary)",
                padding: "24px",
                borderRadius: "12px",
                border: "1px solid var(--color-border)",
                boxShadow: "0 1px 3px rgba(37, 42, 36, 0.04)",
                display: "flex",
                flexDirection: "column",
                justifyContent: "space-between"
              }}
            >
              <div>
                <h4 style={{ margin: "0 0 8px", fontSize: "17px", color: "var(--text-primary)", fontFamily: "var(--font-heading)" }}>{hotel.name}</h4>
                <p style={{ fontSize: "13px", color: "var(--text-secondary)", margin: "0 0 20px" }}>{hotel.address || "Near attraction area"}</p>
              </div>
              <button
                onClick={() => open(hotel.searchUrl)}
                style={{
                  background: "var(--brand-primary)",
                  color: "white",
                  border: "none",
                  padding: "10px",
                  borderRadius: "6px",
                  fontWeight: "600",
                  cursor: "pointer",
                  fontSize: "13px"
                }}
              >
                Get Directions
              </button>
            </div>
          ))}
        </div>

        {/* SECTION 4: BUDGET */}
        <h2 style={{ marginTop: "56px", marginBottom: "8px", fontSize: "24px", color: "var(--text-primary)", fontFamily: "var(--font-heading)" }}>
          Estimated Budget
        </h2>
        <p style={{ color: "var(--text-secondary)", marginBottom: "24px", fontSize: "14px" }}>
          Estimates per traveler for {itinerary.days} days in {itinerary.destination}; they are planning ranges, not live hotel, transport, or ticket prices.
        </p>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: "20px" }}>
          {itinerary.budgetEstimates?.map((tier, i) => {
            const isStandard = tier.budgetCategory === "Standard Budget";
            return (
              <div key={i} style={{
                background: "var(--surface-primary)",
                padding: "32px 24px",
                borderRadius: "12px",
                border: isStandard ? "2px solid var(--brand-primary)" : "1px solid var(--color-border)",
                boxShadow: "0 1px 3px rgba(37, 42, 36, 0.04)",
                position: "relative"
              }}>
                {isStandard && (
                  <span style={{
                    position: "absolute",
                    top: "-12px",
                    left: "50%",
                    transform: "translateX(-50%)",
                    background: "var(--brand-primary)",
                    color: "white",
                    padding: "4px 14px",
                    borderRadius: "6px",
                    fontSize: "11px",
                    fontWeight: "700",
                    letterSpacing: "0.05em"
                  }}>RECOMMENDED</span>
                )}
                <h3 style={{ textAlign: "center", margin: "0 0 8px", fontSize: "20px", color: "var(--text-primary)", fontFamily: "var(--font-heading)" }}>{tier.budgetCategory}</h3>
                <p style={{ textAlign: "center", color: "var(--brand-primary)", fontSize: "26px", fontWeight: "700", margin: "0 0 24px", fontFamily: "var(--font-heading)" }}>
                  ₹{tier.minTotal.toLocaleString()} - ₹{tier.maxTotal.toLocaleString()}
                </p>
                
                <div style={{ display: "grid", gap: "12px", fontSize: "14px", borderTop: "1px solid var(--color-border)", paddingTop: "20px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between" }}>
                    <span style={{ color: "var(--text-secondary)" }}>Accommodation</span>
                    <span style={{ fontWeight: "600" }}>₹{tier.minAccommodation} - ₹{tier.maxAccommodation}</span>
                  </div>
                  <div style={{ display: "flex", justifyContent: "space-between" }}>
                    <span style={{ color: "var(--text-secondary)" }}>Food & Dining</span>
                    <span style={{ fontWeight: "600" }}>₹{tier.minFood} - ₹{tier.maxFood}</span>
                  </div>
                  <div style={{ display: "flex", justifyContent: "space-between" }}>
                    <span style={{ color: "var(--text-secondary)" }}>Transport</span>
                    <span style={{ fontWeight: "600" }}>₹{tier.minTransport} - ₹{tier.maxTransport}</span>
                  </div>
                  <div style={{ display: "flex", justifyContent: "space-between" }}>
                    <span style={{ color: "var(--text-secondary)" }}>Attractions</span>
                    <span style={{ fontWeight: "600" }}>₹{tier.minAttractions} - ₹{tier.maxAttractions}</span>
                  </div>
                </div>
                
                <p style={{ marginTop: "20px", fontSize: "12px", color: "var(--text-secondary)", fontStyle: "italic" }}>
                  Includes: {tier.assumptions}
                </p>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default Itinerary;
