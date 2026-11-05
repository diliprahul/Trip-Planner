/* eslint-disable react-refresh/only-export-components */
import { createContext, useState, useEffect } from "react";
import { getCurrentUser, loginUser, logoutUser, registerUser, verifyRegistrationOtp } from "../api/tripApi";

export const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchCurrentUser = async () => {
    try {
      setLoading(true);
      const data = await getCurrentUser();
      setUser(data);
    } catch (err) {
      if (err.response?.status === 401) {
        setUser(null);
      } else {
        console.error(err);
        setUser(null);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCurrentUser();
  }, []);

  const login = async (credentials) => {
    const data = await loginUser(credentials);
    setUser(data);
    return data;
  };

  const register = async (userData) => {
    return registerUser(userData);
  };

  const verifyRegistration = async (email, otp) => {
    const data = await verifyRegistrationOtp(email, otp);
    setUser(data);
    return data;
  };

  const logout = async () => {
    try {
      await logoutUser();
    } finally {
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        loading,
        login,
        register,
        verifyRegistration,
        logout,
        fetchCurrentUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
