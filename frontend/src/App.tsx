import "./App.css";
import { useState } from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import AppLayout from "./components/layout/AppLayout";
import Dashboard from "./pages/Dashboard/Dashboard";
import Nutrition from "./pages/Nutrition/Nutrition";
import ReplaceDinner from "./pages/ReplaceDinner/ReplaceDinner";
import PlanReview from "./pages/PlanReview/PlanReview";
import Workouts from "./pages/Workouts/Workouts";
import History from "./pages/History/History";
import Settings from "./pages/Settings/Settings";
import Registration from "./pages/Registration/Registration";
import Login from "./pages/Login/Login";

function App() {
  const [isRegistered, setIsRegistered] = useState(
    localStorage.getItem("registered") === "true"
  );
  const [screen, setScreen] = useState<"register" | "login">("register");

  const handleCreate = () => {
    localStorage.setItem("registered", "true");
    setIsRegistered(true);
  };

  if (!isRegistered) {
    if (screen === "login") {
      return (
        <Login
          onLogin={handleCreate}
          onGoRegister={() => setScreen("register")}
        />
      );
    }

    return (
      <Registration
        onCreate={handleCreate}
        onSignIn={() => setScreen("login")}
      />
    );
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/nutrition" element={<Nutrition />} />
          <Route path="/nutrition/replace-dinner" element={<ReplaceDinner />} />
          <Route path="/nutrition/plan-review" element={<PlanReview />} />
          <Route path="/workouts" element={<Workouts />} />
          <Route path="/history" element={<History />} />
          <Route path="/settings" element={<Settings />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;