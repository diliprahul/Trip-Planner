const ErrorMessage = ({ message }) => {
  if (!message) return null;

  return (
    <div style={{ color: "red", marginTop: "10px" }}>
      {message}
    </div>
  );
};

export default ErrorMessage;
