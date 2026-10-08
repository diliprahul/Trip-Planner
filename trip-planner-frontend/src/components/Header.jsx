import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/useAuth";

const Header = () => {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const { isAuthenticated, logout, user } = useAuth();

  const isActive = (path) => location.pathname === path;

  const handleLogout = async () => {
    try {
      await logout();
      navigate("/login");
      setMobileMenuOpen(false);
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <header className="site-header">
      <div className="header-container">
        <Link to="/" className="brand-logo">
          <svg className="logo-icon" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <polygon points="16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88 16.24 7.76"></polygon>
          </svg>
          <span>TripPlanner</span>
        </Link>

        <nav className={`nav-links ${mobileMenuOpen ? "open" : ""}`}>
          <Link 
            to="/" 
            className={`nav-link ${isActive("/") ? "active" : ""}`}
            onClick={() => setMobileMenuOpen(false)}
          >
            Plan Trip
          </Link>
          
          {isAuthenticated && (
            <Link 
              to="/my-trips" 
              className={`nav-link ${isActive("/my-trips") ? "active" : ""}`}
              onClick={() => setMobileMenuOpen(false)}
            >
              My Trips
            </Link>
          )}

          <Link 
            to="/privacy-policy" 
            className={`nav-link ${isActive("/privacy-policy") ? "active" : ""}`}
            onClick={() => setMobileMenuOpen(false)}
          >
            Privacy Policy
          </Link>
          <Link 
            to="/terms-conditions" 
            className={`nav-link ${isActive("/terms-conditions") ? "active" : ""}`}
            onClick={() => setMobileMenuOpen(false)}
          >
            Terms & Conditions
          </Link>

          {!isAuthenticated ? (
            <>
              <Link 
                to="/login" 
                className={`nav-link ${isActive("/login") ? "active" : ""}`}
                onClick={() => setMobileMenuOpen(false)}
              >
                Sign In
              </Link>
              <Link 
                to="/register" 
                className={`nav-link ${isActive("/register") ? "active" : ""}`}
                onClick={() => setMobileMenuOpen(false)}
                style={{ background: "#2563eb", color: "white", padding: "8px 16px", borderRadius: "8px" }}
              >
                Register
              </Link>
            </>
          ) : (
            <button
              onClick={handleLogout}
              className="nav-link"
              style={{ background: "transparent", border: "none", cursor: "pointer", padding: 0, font: "inherit", color: "#64748b" }}
            >
              Logout ({user?.name || "User"})
            </button>
          )}
        </nav>

        <button 
          className="mobile-menu-toggle" 
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          aria-label="Toggle mobile menu"
        >
          <span className={`hamburger-bar ${mobileMenuOpen ? "open" : ""}`}></span>
          <span className={`hamburger-bar ${mobileMenuOpen ? "open" : ""}`}></span>
          <span className={`hamburger-bar ${mobileMenuOpen ? "open" : ""}`}></span>
        </button>
      </div>
    </header>
  );
};

export default Header;
