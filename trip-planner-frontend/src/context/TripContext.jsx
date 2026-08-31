import { useState } from "react";
import { TripContext } from "./useTrip";

export const TripProvider = ({ children }) => {
  const [tripRequest, setTripRequest] = useState(null);
  const [itinerary, setItinerary] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  return (
    <TripContext.Provider
      value={{
        tripRequest,
        setTripRequest,
        itinerary,
        setItinerary,
        loading,
        setLoading,
        error,
        setError,
      }}
    >
      {children}
    </TripContext.Provider>
  );
};
