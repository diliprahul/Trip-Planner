export const validateTripInput = (data) => {
  if (!data.source || !data.destination) {
    return "Source and destination are required";
  }

  if (data.days <= 0) {
    return "Number of days must be greater than zero";
  }

  return null;
};
