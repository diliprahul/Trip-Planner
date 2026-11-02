import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import React, { lazy, Suspense } from "react";
import Header from "./components/Header";
import Footer from "./components/Footer";
import { TripProvider } from "./context/TripContext";
import { AuthProvider } from "./context/AuthContext";
import { useAuth } from "./context/useAuth";

const Home = lazy(() => import("./pages/Home"));
const Result = lazy(() => import("./pages/result"));
const PrivacyPolicy = lazy(() => import("./pages/PrivacyPolicy"));
const TermsConditions = lazy(() => import("./pages/TermsConditions"));
const NotFound = lazy(() => import("./pages/NotFound"));
const Login = lazy(() => import("./pages/Login"));
const Register = lazy(() => import("./pages/Register"));
const MyTrips = lazy(() => import("./pages/MyTrips"));
const Profile = lazy(() => import("./pages/Profile"));

const ProtectedRoute = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();

  if (loading) {
    return (
      <div style={{ padding: "100px 24px", textAlign: "center", color: "#64748b", flex: 1 }}>
        Loading session...
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return children;
};

function App() {
  return (
    <AuthProvider>
      <TripProvider>
        <BrowserRouter>
          <div className="app-layout">
            <Header />
            <main className="main-content">
              <Suspense fallback={<div style={{ padding: "100px 24px", textAlign: "center", color: "var(--text-secondary)", flex: 1 }}>Loading page...</div>}>
                <Routes>
                  <Route path="/" element={<ProtectedRoute><Home /></ProtectedRoute>} />
                  <Route path="/result" element={<ProtectedRoute><Result /></ProtectedRoute>} />
                  <Route path="/result/:tripId" element={<ProtectedRoute><Result /></ProtectedRoute>} />
                  <Route path="/my-trips" element={<ProtectedRoute><MyTrips /></ProtectedRoute>} />
                  <Route path="/profile" element={<ProtectedRoute><Profile /></ProtectedRoute>} />
                  <Route path="/login" element={<Login />} />
                  <Route path="/register" element={<Register />} />
                  <Route path="/privacy-policy" element={<PrivacyPolicy />} />
                  <Route path="/terms-conditions" element={<TermsConditions />} />
                  <Route path="*" element={<NotFound />} />
                </Routes>
              </Suspense>
            </main>
            <Footer />
          </div>
        </BrowserRouter>
      </TripProvider>
    </AuthProvider>
  );
}

export default App;
