import React from "react";

const WeatherForecast = ({ weatherResponse }) => {
  if (!weatherResponse) return null;

  const { status, message, forecasts } = weatherResponse;

  if (status === "API_ERROR") {
    return (
      <div style={{ marginTop: "32px", padding: "20px", background: "#fff5f5", borderRadius: "20px", border: "1px solid #fed7d7", textAlign: "center" }}>
        <p style={{ color: "#c53030", margin: 0 }}>{message}</p>
      </div>
    );
  }

  if (status === "NOT_AVAILABLE_YET") {
     return (
       <div style={{ marginTop: "32px", padding: "20px", background: "#fffaf0", borderRadius: "20px", border: "1px solid #feebc8", textAlign: "center" }}>
         <p style={{ color: "#975a16", margin: 0 }}>{message}</p>
       </div>
     );
  }

  return (
    <div style={{ marginTop: "32px" }}>
      <h2 style={{ marginBottom: "20px", fontSize: "28px", color: "#0f172a" }}>Daily Weather Forecast</h2>
      {status === "PARTIALLY_AVAILABLE" && (
        <p style={{ color: "#975a16", marginBottom: "16px", fontSize: "14px" }}>⚠️ {message}</p>
      )}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(120px, 1fr))", gap: "16px" }}>
        {forecasts.map((day, i) => (
          <div key={i} style={{ background: "white", padding: "16px", borderRadius: "16px", border: "1px solid #e2e8f0", textAlign: "center", boxShadow: "0 2px 4px -1px rgba(0,0,0,0.1)" }}>
            <div style={{ fontSize: "14px", fontWeight: "600", color: "#64748b" }}>{new Date(day.date).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}</div>
            {day.icon && <img src={`http://openweathermap.org/img/wn/${day.icon}@2x.png`} alt={day.condition} style={{ width: "50px", height: "50px", margin: "8px 0" }} />}
            <div style={{ fontSize: "18px", fontWeight: "bold", color: "#1e293b" }}>{Math.round(day.maxTemperature)}°C</div>
            <div style={{ fontSize: "14px", color: "#94a3b8" }}>{Math.round(day.minTemperature)}°C</div>
            <div style={{ fontSize: "12px", color: "#3b82f6", marginTop: "8px" }}>{day.condition}</div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default WeatherForecast;
