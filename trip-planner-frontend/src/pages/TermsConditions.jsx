import { useEffect } from "react";

const TermsConditions = () => {
  useEffect(() => {
    document.title = "Terms & Conditions | TripPlanner";
  }, []);

  return (
    <div className="legal-page">
      <div className="legal-container">
        <h1>Terms & Conditions</h1>
        <p className="legal-updated">Last updated: October 2026</p>

        <section>
          <h2>1. Acceptance of Terms</h2>
          <p>
            By accessing and using TripPlanner, you agree to comply with and be bound by these Terms & Conditions. If you do not agree with any part of these terms, please discontinue use of the service.
          </p>
        </section>

        <section>
          <h2>2. Use of Service</h2>
          <p>
            TripPlanner provides automated itinerary generation, weather data integration, and budget estimation. All travel itineraries, cost breakdowns, and activity suggestions are provided for informational and planning purposes.
          </p>
        </section>

        <section>
          <h2>3. Accuracy of Estimates & Data</h2>
          <p>
            Budget estimates, transport links, and weather forecasts are calculated using algorithmic models and third-party data providers. Prices, operational hours, and availability are subject to change and should be verified independently prior to travel.
          </p>
        </section>

        <section>
          <h2>4. Limitation of Liability</h2>
          <p>
            TripPlanner shall not be held liable for any direct, indirect, incidental, or consequential damages arising from the use of generated travel plans or reliance on third-party service data.
          </p>
        </section>
      </div>
    </div>
  );
};

export default TermsConditions;
