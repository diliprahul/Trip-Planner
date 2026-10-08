import axios from "axios";

const apiClient = axios.create({
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
