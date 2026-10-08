import { useEffect } from "react";

const PrivacyPolicy = () => {
  useEffect(() => {
    document.title = "Privacy Policy | TripPlanner";
  }, []);

  return (
    <div className="legal-page">
      <div className="legal-container">
        <h1>Privacy Policy</h1>
        <p className="legal-updated">Last updated: October 2026</p>

        <section>
          <h2>1. Information We Process</h2>
          <p>
            TripPlanner is a travel itinerary planning application. When you use our service, we process the origin, destination, travel dates, and category preferences you enter to generate structured travel plans, weather forecasts, and cost estimations.
          </p>
        </section>

        <section>
          <h2>2. How We Use Data</h2>
          <p>
            The information provided is transmitted solely to retrieve geographic coordinates, local weather conditions, point-of-interest suggestions, and accommodations required to assemble your personalized itinerary. We do not sell or share your personal trip parameters with third-party advertisers.
          </p>
        </section>

        <section>
          <h2>3. Data Retention</h2>
          <p>
            Trip details and generated itineraries are maintained temporarily within active session storage to facilitate your planning workflow.
          </p>
        </section>

        <section>
          <h2>4. Contact</h2>
          <p>
            If you have questions regarding this privacy policy, please reach out through official application support channels.
          </p>
        </section>
      </div>
    </div>
  );
};

export default PrivacyPolicy;
