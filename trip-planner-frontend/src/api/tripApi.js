import axios from "axios";

const apiClient = axios.create({
  headers: {
    "Content-Type": "application/json",
  },
});

export const createTrip = async (tripRequest) => {
  const response = await apiClient.post("/api/trips", tripRequest);
  return response.data;
};

export const generateItinerary = async (tripId) => {
  const response = await apiClient.post(`/api/trips/${tripId}/generate`);
  return response.data;
};