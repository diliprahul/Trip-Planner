import { useState, useEffect, useRef } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/useAuth";

const Header = () => {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [imgError, setImgError] = useState(false);
  const dropdownRef = useRef(null);
  const location = useLocation();
  const navigate = useNavigate();
  const { isAuthenticated, logout, user } = useAuth();

  const isActive = (path) => location.pathname === path;

  useEffect(() => {
    setImgError(false);
  }, [user?.profilePictureUrl]);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleLogout = async () => {
    try {
      setDropdownOpen(false);
      setMobileMenuOpen(false);
      await logout();
      navigate("/login");
    } catch (err) {
      console.error(err);
    }
  };

  const handleViewProfile = () => {
    setDropdownOpen(false);
    setMobileMenuOpen(false);
    navigate("/profile");
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
            <div className="user-menu-wrapper" ref={dropdownRef}>
              <button
                onClick={() => setDropdownOpen(!dropdownOpen)}
                className="user-menu-button"
                aria-expanded={dropdownOpen}
                aria-label="User menu"
              >
                <div className="user-avatar">
                  {user?.profilePictureUrl && !imgError ? (
                    <img src={user.profilePictureUrl} alt={user?.name || "User"} onError={() => setImgError(true)} />
                  ) : (
                    <span>{user?.name ? user.name.charAt(0).toUpperCase() : "U"}</span>
                  )}
                </div>
                <span className="user-name">{user?.name || "User"}</span>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="user-chevron">
                  <polyline points="6 9 12 15 18 9"></polyline>
                </svg>
              </button>

              {dropdownOpen && (
                <div className="user-dropdown-menu">
                  <div className="user-dropdown-header">
                    <div className="user-dropdown-name">{user?.name || "User"}</div>
                    {user?.email && (
                      <div className="user-dropdown-email">{user.email}</div>
                    )}
                  </div>
                  <button
                    onClick={handleViewProfile}
                    className="user-dropdown-item"
                  >
                    View Profile
                  </button>
                  <button
                    onClick={handleLogout}
                    className="user-dropdown-item logout-item"
                  >
                    Logout
                  </button>
                </div>
              )}
            </div>
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
