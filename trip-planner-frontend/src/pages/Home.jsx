import TripForm from "../components/TripForm";
import ErrorMessage from "../components/ErrorMessage";
import { useTrip } from "../context/useTrip";

const Home = () => {
  const { error } = useTrip();

  return (
    <div style={{ display: "flex", flexDirection: "column", width: "100%", flex: 1 }}>
      <div 
        style={{
          position: "relative",
          width: "100%",
          minHeight: "380px",
          backgroundImage: `linear-gradient(rgba(37, 42, 36, 0.45), rgba(37, 42, 36, 0.65)), url('/images/Home-page-hero.jpg')`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          color: "#ffffff",
          textAlign: "center",
          padding: "60px 24px"
        }}
      >
        <div style={{ maxWidth: "700px", zIndex: 2 }}>
          <span style={{
            fontFamily: "var(--font-heading)",
            fontSize: "12px",
            fontWeight: "700",
            letterSpacing: "2px",
            textTransform: "uppercase",
            color: "#E9E3D6",
            background: "rgba(36, 75, 60, 0.8)",
            padding: "6px 14px",
            borderRadius: "4px",
            display: "inline-block",
            marginBottom: "16px"
          }}>
            TripPlanner Editorial
          </span>
          <h1 style={{ fontFamily: "var(--font-heading)", fontSize: "42px", fontWeight: "800", color: "#FFFFFF", marginBottom: "16px", lineHeight: "1.15" }}>
            Craft Your Next Extraordinary Journey
          </h1>
          <p style={{ fontSize: "17px", color: "#E9E3D6", margin: 0, lineHeight: "1.6", fontWeight: "400" }}>
            Plan intelligent, tailored itineraries with local insights, maps, and seamless budgeting.
          </p>
        </div>
      </div>
      <div style={{ padding: "40px 20px", display: "flex", justifyContent: "center", flex: 1 }}>
        <TripForm />
      </div>
      {error && <div style={{ maxWidth: "600px", margin: "0 auto 24px" }}><ErrorMessage message={error} /></div>}
    </div>
  );
};

export default Home;
