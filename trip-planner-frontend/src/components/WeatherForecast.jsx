import React from "react";

const WeatherForecast = ({ weatherResponse }) => {
  if (!weatherResponse) return null;

  const { status, message, forecasts } = weatherResponse;

  if (status === "API_ERROR") {
    return (
      <div style={{ marginTop: "32px", padding: "20px", background: "#fef2f2", borderRadius: "8px", border: "1px solid #fca5a5", textAlign: "center" }}>
        <p style={{ color: "#991b1b", margin: 0, fontSize: "14px" }}>{message}</p>
      </div>
    );
  }

  if (status === "NOT_AVAILABLE_YET") {
     return (
       <div style={{ marginTop: "32px", padding: "20px", background: "#fdf8f0", borderRadius: "8px", border: "1px solid var(--color-border)", textAlign: "center" }}>
         <p style={{ color: "var(--text-secondary)", margin: 0, fontSize: "14px" }}>{message}</p>
       </div>
     );
  }

  return (
    <div style={{ marginTop: "40px" }}>
      <h2 style={{ marginBottom: "20px", fontSize: "24px", color: "var(--text-primary)", fontFamily: "var(--font-heading)" }}>Daily Weather Forecast</h2>
      {status === "PARTIALLY_AVAILABLE" && (
        <p style={{ color: "var(--text-secondary)", marginBottom: "16px", fontSize: "14px" }}>{message}</p>
      )}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(130px, 1fr))", gap: "16px" }}>
        {forecasts.map((day, i) => (
          <div key={i} style={{ background: "var(--surface-primary)", padding: "16px", borderRadius: "8px", border: "1px solid var(--color-border)", textAlign: "center", boxShadow: "0 1px 3px rgba(37, 42, 36, 0.04)" }}>
            <div style={{ fontSize: "13px", fontWeight: "600", color: "var(--text-secondary)" }}>{new Date(day.date).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}</div>
            {day.icon && <img src={`https://openweathermap.org/img/wn/${day.icon}@2x.png`} alt={day.condition} style={{ width: "48px", height: "48px", margin: "4px 0" }} />}
            <div style={{ fontSize: "18px", fontWeight: "700", color: "var(--text-primary)" }}>{Math.round(day.maxTemperature)}°C</div>
            <div style={{ fontSize: "13px", color: "var(--text-secondary)" }}>{Math.round(day.minTemperature)}°C</div>
            <div style={{ fontSize: "12px", color: "var(--brand-primary)", marginTop: "8px", fontWeight: "500" }}>{day.condition}</div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default WeatherForecast;
