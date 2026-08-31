import { createContext, useContext } from "react";

export const TripContext = createContext();

export const useTrip = () => useContext(TripContext);
