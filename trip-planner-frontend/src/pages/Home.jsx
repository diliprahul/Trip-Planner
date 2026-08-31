import TripForm from "../components/TripForm";
import ErrorMessage from "../components/ErrorMessage";
import { useTrip } from "../context/useTrip";

const Home = () => {
  const { error } = useTrip();

  return (
    <>
      <TripForm />
      {error && <ErrorMessage message={error} />}
    </>
  );
};

export default Home;
