export const validateTripInput = (data) => {
  if (!data.source || !data.destination) {
    return "Source and destination are required";
  }

  if (data.days <= 0) {
    return "Number of days must be greater than zero";
  }

  return null;
};

export const validatePassword = (password) => {
  if (!/^(?=.*[A-Za-z])(?=.*\d).{6,}$/.test(password || "")) {
    return "Password must be at least 6 characters and include a letter and a number";
  }
  return null;
};
