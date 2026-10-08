import { Link } from "react-router-dom";

const Footer = () => {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="site-footer">
      <div className="footer-container">
        <div className="footer-left">
          <span className="footer-brand">TripPlanner</span>
          <p className="footer-desc">Personalized travel itinerary generation powered by structured geographic and weather data.</p>
        </div>
        <div className="footer-links">
          <h4>Navigation</h4>
          <Link to="/">Plan Trip</Link>
          <Link to="/privacy-policy">Privacy Policy</Link>
          <Link to="/terms-conditions">Terms & Conditions</Link>
        </div>
      </div>
      <div className="footer-bottom">
        <p>&copy; {currentYear} TripPlanner. All rights reserved.</p>
      </div>
    </footer>
  );
};

export default Footer;
