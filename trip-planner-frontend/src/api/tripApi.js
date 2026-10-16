import axios from "axios";

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "",
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: true,
});

export const registerUser = async (data) => {
  const response = await apiClient.post("/api/auth/register", data);
  return response.data;
};

export const loginUser = async (data) => {
  const response = await apiClient.post("/api/auth/login", data);
  return response.data;
};

export const logoutUser = async () => {
  await apiClient.post("/api/auth/logout");
};

export const getCurrentUser = async () => {
  const response = await apiClient.get("/api/auth/me");
  return response.data;
};

export const getProfile = async () => {
  const response = await apiClient.get("/api/auth/profile");
  return response.data;
};

export const updateProfile = async (data) => {
  const response = await apiClient.put("/api/auth/profile", data);
  return response.data;
};

export const uploadProfilePicture = async (formData) => {
  const response = await apiClient.post("/api/auth/profile/picture", formData);
  return response.data;
};

export const deleteProfilePicture = async () => {
  const response = await apiClient.delete("/api/auth/profile/picture");
  return response.data;
};

export const forgotPassword = async (email) => {
  const response = await apiClient.post("/api/auth/forgot-password", { email });
  return response.data;
};

export const verifyResetOtp = async (email, otp) => {
  const response = await apiClient.post("/api/auth/verify-reset-otp", { email, otp });
  return response.data;
};

export const resetPassword = async (email, otp, newPassword) => {
  const response = await apiClient.post("/api/auth/reset-password", { email, otp, newPassword });
  return response.data;
};

export const createTrip = async (tripRequest) => {
  const response = await apiClient.post("/api/trips", tripRequest);
  return response.data;
};

export const generateItinerary = async (tripId) => {
  const response = await apiClient.post(`/api/trips/${tripId}/generate`);
  return response.data;
};

export const getTripById = async (tripId) => {
  const response = await apiClient.get(`/api/trips/${tripId}`);
  return response.data;
};

export const getMyTrips = async () => {
  const response = await apiClient.get("/api/trips/my");
  return response.data;
};

export const deleteTrip = async (tripId) => {
  await apiClient.delete(`/api/trips/${tripId}`);
};
