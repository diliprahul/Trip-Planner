import axios from "axios";

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "",
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: true,
});

let csrfToken = null;
let csrfBootstrap = null;

export const refreshCsrfToken = async () => {
  csrfBootstrap ??= apiClient.get("/api/auth/csrf").then(({ data }) => {
    csrfToken = data.token;
    return csrfToken;
  }).finally(() => {
    csrfBootstrap = null;
  });
  return csrfBootstrap;
};

apiClient.interceptors.request.use(async (config) => {
  const method = (config.method || "get").toLowerCase();
  if (["post", "put", "patch", "delete"].includes(method)) {
    const token = csrfToken || await refreshCsrfToken();
    config.headers["X-CSRF-TOKEN"] = token;
  }
  return config;
});

export const registerUser = async (data) => {
  const response = await apiClient.post("/api/auth/register", data);
  return response.data;
};

export const verifyRegistrationOtp = async (email, otp) => {
  const response = await apiClient.post("/api/auth/register/verify-otp", { email, otp });
  await refreshCsrfToken();
  return response.data;
};

export const resendRegistrationOtp = async (email) => {
  const response = await apiClient.post("/api/auth/register/resend-otp", { email });
  return response.data;
};

export const loginUser = async (data) => {
  const response = await apiClient.post("/api/auth/login", data);
  await refreshCsrfToken();
  return response.data;
};

export const logoutUser = async () => {
  await apiClient.post("/api/auth/logout");
  csrfToken = null;
  await refreshCsrfToken();
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
  const response = await apiClient.post("/api/auth/profile/picture", formData, {
    headers: {
      "Content-Type": undefined,
    },
  });
  return response.data;
};

export const deleteProfilePicture = async () => {
  const response = await apiClient.delete("/api/auth/profile/picture");
  return response.data;
};

export const deleteAccount = async (password) => {
  await apiClient.delete("/api/auth/account", { data: { password, confirmed: true } });
  csrfToken = null;
};

export const startGoogleAccountDeletionReauthentication = async () => {
  const response = await apiClient.get("/api/auth/account/google-reauth");
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

export const getTripWeather = async (tripId) => {
  const response = await apiClient.get(`/api/trips/${tripId}/weather`);
  return response.data;
};

export const getMyTrips = async () => {
  const response = await apiClient.get("/api/trips/my");
  return response.data;
};

export const deleteTrip = async (tripId) => {
  await apiClient.delete(`/api/trips/${tripId}`);
};

export const backfillCoordinates = async (tripId) => {
  const response = await apiClient.post(`/api/trips/${tripId}/coordinates/backfill`);
  return response.data;
};
