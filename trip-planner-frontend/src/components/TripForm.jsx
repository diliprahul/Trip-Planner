import { useState } from "react";
import { createTrip, generateItinerary } from "../api/tripApi";
import { useTrip } from "../context/useTrip";
import { useNavigate } from "react-router-dom";
import "./TripForm.css";

const CATEGORIES = [
  { id: "HISTORIC", label: "Historic & Monuments" },
  { id: "RELIGIOUS", label: "Religious Places" },
  { id: "NATURE", label: "Nature & Hills" },
  { id: "WATER", label: "Lakes & Water" },
  { id: "CULTURE", label: "Museums & Culture" },
  { id: "SHOPPING", label: "Shopping & Malls" },
  { id: "ENTERTAINMENT", label: "Entertainment" },
  { id: "LEISURE", label: "Parks & Gardens" },
  { id: "MARKET", label: "Local Markets" },
];

const TripForm = () => {
  const { setItinerary, setLoading, loading, setError } = useTrip();
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    origin: "",
    destination: "",
    startDate: "",
    endDate: "",
  });

  const [allPlaces, setAllPlaces] = useState(true);
  const [selectedCategories, setSelectedCategories] = useState([]);

  const duration = formData.startDate && formData.endDate 
    ? Math.max(0, (new Date(formData.endDate) - new Date(formData.startDate)) / (1000 * 60 * 60 * 24) + 1)
    : 0;

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleAllPlacesChange = (e) => {
    if (e.target.checked) {
      setAllPlaces(true);
      setSelectedCategories([]);
    }
  };

  const handleCategoryToggle = (id) => {
    setAllPlaces(false);
    setSelectedCategories(prev => {
      if (prev.includes(id)) {
        const updated = prev.filter(c => c !== id);
        if (updated.length === 0) {
          setAllPlaces(true);
        }
        return updated;
      } else {
        return [...prev, id];
      }
    });
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
        placeCategories: selectedCategories,
        categories: selectedCategories,
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

          <div className="form-group categories-section">
            <label>What would you like to explore?</label>
            <div className="categories-grid">
              <label className={`category-chip ${allPlaces ? "selected" : ""}`}>
                <input
                  type="checkbox"
                  checked={allPlaces}
                  onChange={handleAllPlacesChange}
                />
                All Places
              </label>
              {CATEGORIES.map(cat => {
                const isSelected = selectedCategories.includes(cat.id);
                return (
                  <label key={cat.id} className={`category-chip ${isSelected ? "selected" : ""}`}>
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => handleCategoryToggle(cat.id)}
                    />
                    {cat.label}
                  </label>
                );
              })}
            </div>
          </div>
          
          <p className="duration-text">Duration: {duration} days</p>

          <button type="submit" disabled={loading}>
            {loading ? "Creating Trip..." : "Create Trip"}
          </button>
        </form>
      </div>
    </div>
  );
};

export default TripForm;
