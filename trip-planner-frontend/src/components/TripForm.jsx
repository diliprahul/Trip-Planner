import { useState } from "react";
import { createTrip, generateItinerary } from "../api/tripApi";
import { useTrip } from "../context/useTrip";
import { useNavigate } from "react-router-dom";
import "./TripForm.css";

const TripForm = () => {
  const { setItinerary, setLoading, loading, setError } = useTrip();
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    origin: "",
    destination: "",
    startDate: "",
    endDate: "",
  });

  const duration = formData.startDate && formData.endDate 
    ? Math.max(0, (new Date(formData.endDate) - new Date(formData.startDate)) / (1000 * 60 * 60 * 24) + 1)
    : 0;

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (new Date(formData.endDate) < new Date(formData.startDate)) {
      setError("End date cannot be before start date");
      return;
    }

    try {
      setLoading(true);
      setError(null);

      const payload = {
        ...formData,
        categories: ["sightseeing"],
      };

      const createdTrip = await createTrip(payload);
      if (!createdTrip?.id) throw new Error("Trip ID missing");

      const itinerary = await generateItinerary(createdTrip.id);
      setItinerary(itinerary);
      navigate("/result");
    } catch (err) {
      console.error(err);
      setItinerary(null);
      setError("Itinerary generation failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="trip-form-wrapper">
      <div className="trip-form-card">
        <h1>Plan Your Trip</h1>
        <p className="subtitle">
          Create a personalized travel itinerary in seconds
        </p>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Origin</label>
            <input
              name="origin"
              placeholder="e.g. Vijayawada"
              value={formData.origin}
              onChange={handleChange}
              required
            />
          </div>

          <div className="form-group">
            <label>Destination</label>
            <input
              name="destination"
              placeholder="e.g. Hyderabad"
              value={formData.destination}
              onChange={handleChange}
              required
            />
          </div>

          <div className="form-group">
            <label>Start Date</label>
            <input type="date" name="startDate" value={formData.startDate} onChange={handleChange} required />
          </div>

          <div className="form-group">
            <label>End Date</label>
            <input type="date" name="endDate" value={formData.endDate} onChange={handleChange} required />
          </div>
          
          <p>Duration: {duration} days</p>

          <button type="submit" disabled={loading}>
            {loading ? "Creating Trip..." : "Create Trip"}
          </button>
        </form>
      </div>
    </div>
  );
};

export default TripForm;
