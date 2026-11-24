import "./Dashboard.css";
import { Link } from "react-router-dom";
import {
  CalendarDays,
  Info,
  Dumbbell,
  CircleCheck,
  RefreshCw,
  SlidersHorizontal,
} from "lucide-react";
import dinner from "../../assets/images/dinner.png";
const days = [
  "Mon 19",
  "Tue 20",
  "Wed 21",
  "Thu 22",
  "Fri 23",
  "Sat 24",
  "Sun 25",
];
const activeDay = "Sat 24";

const macros = [
  { name: "Protein", eaten: 62, goal: 120, fill: "protein-fill" },
  { name: "Fat", eaten: 40, goal: 65, fill: "fat-fill" },
  { name: "Carbs", eaten: 150, goal: 230, fill: "carbs-fill" },
];

const meals = [
  {
    name: "Breakfast",
    time: "08:30",
    description: "Oatmeal with berries",
    calories: 420,
    status: "logged",
  },
  {
    name: "Lunch",
    time: "13:15",
    description: "Rice, salmon and vegetables",
    calories: 820,
    status: "logged",
    warning: "+180 over plan",
  },
  {
    name: "Snack",
    time: "16:30",
    description: "Yogurt with nuts",
    calories: 250,
    status: "planned",
  },
  {
    name: "Dinner",
    time: "19:00",
    description: "Chicken and vegetable pasta",
    calories: 690,
    status: "planned",
  },
];

function Dashboard() {
  return (
    <div className="dashboard">
      <div className="dashboard-heading">
        <div className="dashboard-heading-info">
          <h1>Good afternoon, Anna</h1>
          <p>Your plan fits your life, not the other way around.</p>
        </div>
        <button className="add-entry-button">+ Add entry</button>
      </div>

      <div className="dashboard-date">
        <div className="dashboard-date-info">
          <h2>24 October 2026</h2>
          <p className="selected-date">Saturday · Today</p>
        </div>
        <div className="week-calendar">
          {days.map((day) => (
            <button
              key={day}
              className={`day-item ${day === activeDay ? "active" : ""}`}
            >
              <span>{day}</span>
            </button>
          ))}
          <button className="calendar-button" aria-label="Open calendar">
            <CalendarDays size={18} />
          </button>
        </div>
      </div>

      <section className="recovery-card">
        <div className="recovery-card-content">
          <Info className="recovery-card-icon" size={20} />
          <div className="recovery-card-heading">
            <h2>Lunch was 180 kcal over plan. We can adjust for that.</h2>
            <p>
              Logged meals stay as they are. We will suggest changes only for
              the rest of the day.
            </p>
          </div>
          <Link to="/nutrition/plan-review" className="recovery-review-button">
            Review the rest of the day
          </Link>
        </div>
      </section>

      <section className="dashboard-summary">
        {/* Daily nutrition */}
        <div className="card nutrition-card">
          <h2 className="card-title">Daily nutrition</h2>

          <div className="calories-info">
            <div className="calories-chart">
              <svg viewBox="0 0 120 120" className="calories-circle">
                <circle className="calories-circle-bg" cx="60" cy="60" r="48" />
                <circle
                  className="calories-circle-progress"
                  cx="60"
                  cy="60"
                  r="48"
                />
              </svg>
              <div className="calories-chart-center">
                <strong>1 240</strong>
                <span>eaten, kcal</span>
              </div>
            </div>

            <div className="remaining-info">
              <span>Remaining</span>
              <strong>760</strong>
              <span>of 2 000 kcal for today</span>
            </div>
          </div>

          <div className="macros">
            {macros.map((m) => (
              <div className="macro" key={m.name}>
                <div className="macro-value">
                  <span>{m.name}</span>
                  <span>
                    {m.eaten} / {m.goal} g
                  </span>
                </div>
                <div className="progress">
                  <div
                    className={`progress-fill ${m.fill}`}
                    style={{ width: `${(m.eaten / m.goal) * 100}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Meals */}
        <div className="card meals-card">
          <div className="meals-header">
            <h2 className="card-title">Meals</h2>
            <button className="meals-link">Go to diary →</button>
          </div>

          <div className="meals-list">
            {meals.map((meal) => (
              <div className="meal-item" key={meal.name}>
                <div className="meal-main">
                  <div className="meal-title">
                    <span className="meal-name">{meal.name}</span>
                    <span className="meal-hour">{meal.time}</span>
                  </div>
                  <p className="meal-description">
                    {meal.description}
                    {meal.warning && (
                      <>
                        {" "}
                        · <span className="meal-warning">{meal.warning}</span>
                      </>
                    )}
                  </p>
                </div>
                <div className="meal-side">
                  <span className="meal-calories">{meal.calories}</span>
                  <span
                    className={`meal-status ${meal.status} ${meal.warning ? "over" : ""}`}
                  >
                    {meal.status === "logged" ? "Logged" : "Planned"}
                  </span>
                </div>
              </div>
            ))}
          </div>

          <p className="meals-footer">
            940 kcal still planned · daily forecast 2 180 kcal
          </p>
        </div>
        {/* Movement */}
        <div className="card movement-card">
          <h2 className="card-title">Movement</h2>
          <div className="workout">
            <div className="workout-top">
              <Dumbbell size={18} className="workout-icon" />
              <span className="meal-status planned">Planned</span>
            </div>
            <h3>Strength workout</h3>
            <p>18:00 · 45 min · dumbbells</p>
            <button className="outline-button">Open workout</button>
          </div>
          <div className="activity">
            <div className="activity-row">
              <CircleCheck size={18} className="activity-icon" />
              <div>
                <h4>Stretching · 15 min</h4>
                <p>Completed at 09:00</p>
              </div>
            </div>
            <div className="progress">
              <div
                className="progress-fill protein-fill"
                style={{ width: "50%" }}
              />
            </div>
            <p className="activity-caption">1 of 2 activities completed</p>
          </div>
        </div>
      </section>
      <section className="dashboard-next">
        <div className="card next-meal-card">
          <img
            className="next-meal-image"
            src={dinner}
            alt="Chicken and vegetable pasta"
          />
          <div className="next-meal-info">
            <span className="next-meal-badge">Next · Dinner at 18:00</span>
            <h2>Chicken and vegetable pasta</h2>
            <p>690 kcal · 30 min to cook</p>
            <Link
              to="/nutrition/replace-dinner"
              className="outline-button with-icon"
            >
              <RefreshCw size={14} />
              Replace meal
            </Link>
          </div>
        </div>
        <aside className="plans-card">
          <div className="plans-title">
            <SlidersHorizontal size={16} />
            <h3>Evening plans changed?</h3>
          </div>
          <p className="plans-text">
            Tell us how much time you have and what is available. VEYA will
            suggest a suitable option.
          </p>
          <div className="plans-chips">
            <span className="chip">30 min</span>
            <span className="chip">No equipment</span>
          </div>
          <button className="plans-link">
            Update constraints and review →
          </button>
        </aside>
      </section>
      <footer className="dashboard-footer">
        <p>
          Calories and macros are estimates; you can edit them in the diary.
        </p>
        <p>VEYA · Reach your goals at your own pace</p>
      </footer>
    </div>
  );
}

export default Dashboard;
