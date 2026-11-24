import { useState } from "react";
import { Link } from "react-router-dom";
import running from "../../assets/images/running.png"
import "./Workouts.css";
import {
  Plus,
  Dumbbell,
  Check,
  CircleCheck,
  RefreshCw,
  Search,
  ChevronDown,
  Activity,
  Footprints,
} from "lucide-react";

function Workouts() {
  const [filter, setFilter] = useState("all");

  return (
    <div className="workouts">
      <div className="wk-heading">
        <div>
          <p>24 October 2026, Saturday · Activity plan and exercise catalog.</p>
          <h1>Move at your own pace</h1>
        </div>
        <button className="wk-add">
          <Plus size={14} />
          Add activity
        </button>
      </div>
      {/* filters */}
      <div className="wk-filters">
        <button
          className={filter === "all" ? "active" : ""}
          onClick={() => setFilter("all")}
        >
          All activities
        </button>
        <button
          className={filter === "planned" ? "active" : ""}
          onClick={() => setFilter("planned")}
        >
          Planned
        </button>
        <button
          className={filter === "completed" ? "active" : ""}
          onClick={() => setFilter("completed")}
        >
          Completed
        </button>
        <button
          className={filter === "skipped" ? "active" : ""}
          onClick={() => setFilter("skipped")}
        >
          Skipped
        </button>
        <button
          className={filter === "modified" ? "active" : ""}
          onClick={() => setFilter("modified")}
        >
          Modified
        </button>
      </div>
      <div className="wk-layout">
        {/* left part */}
        <section className="wk-left">
          <div className="wk-card">
            <div className="wk-workout-top">
              <div className="wk-icon">
                <Dumbbell size={20} />
              </div>
              <div>
                <h2>Full-body strength workout</h2>
                <span>18:00 · 45 min · dumbbells and mat</span>
              </div>
              <span className="wk-pill planned">Planned</span>
            </div>
            <p className="wk-description">
              5 min warm-up · 30 min main workout including rest · 10 min
              cool-down. Keep the effort comfortable; never push through pain.
            </p>
            <div className="wk-exercise">
              <span className="wk-circle"></span>
              <div className="wk-exercise-name">
                <strong>Joint warm-up</strong>
                <span>No equipment</span>
              </div>
              <b>5 min</b>
              <RefreshCw size={13} />
            </div>
            <div className="wk-exercise">
              <span className="wk-circle"></span>
              <div className="wk-exercise-name">
                <strong>Dumbbell squats</strong>
                <span>Dumbbells · 60 sec rest</span>
              </div>
              <b>3 × 12</b>
              <RefreshCw size={13} />
            </div>
            <div className="wk-exercise">
              <span className="wk-circle"></span>
              <div className="wk-exercise-name">
                <strong>Bent-over dumbbell rows</strong>
                <span>Dumbbells · 60 sec rest</span>
              </div>
              <b>3 × 12</b>
              <RefreshCw size={13} />
            </div>
            <div className="wk-exercise">
              <span className="wk-circle"></span>
              <div className="wk-exercise-name">
                <strong>Glute bridge</strong>
                <span>Mat · 45 sec rest</span>
              </div>
              <b>3 × 15</b>
              <RefreshCw size={13} />
            </div>
            <div className="wk-exercise">
              <span className="wk-circle"></span>
              <div className="wk-exercise-name">
                <strong>Cool-down and stretching</strong>
                <span>Mat</span>
              </div>
              <b>10 min</b>
              <RefreshCw size={13} />
            </div>
            <div className="wk-buttons">
              <button className="wk-primary">
                <Check size={14} />
                Mark completed
              </button>
              <button className="wk-outline">
                <RefreshCw size={14} />
                Replace
              </button>
              <button className="wk-skip">Skip</button>
            </div>
          </div>
          <div className="wk-card wk-done">
            <CircleCheck size={22} />
            <div>
              <strong>Stretching · 15 min</strong>
              <span>Today, 09:00 · entry saved</span>
            </div>
            <span className="wk-pill completed">Completed</span>
          </div>
        </section>
        {/* right part */}
        <aside className="wk-card wk-change">
          <h2>If evening plans change</h2>
          <div className="wk-selects">
            <div>
              <label>Available time</label>
              <select defaultValue="30">
                <option value="15">15 min</option>
                <option value="30">30 min</option>
                <option value="45">45 min</option>
              </select>
            </div>
            <div>
              <label>Equipment</label>
              <select defaultValue="no">
                <option value="no">No</option>
                <option value="dumbbells">Dumbbells</option>
                <option value="mat">Mat</option>
              </select>
            </div>
          </div>
          <img src={running} alt="Brisk walking" />
          <div className="wk-change-title">
            <h3>Brisk walking</h3>
            <span className="wk-pill completed">25 min</span>
          </div>
          <p className="wk-text">
            Instead of a 45 min strength workout: no dumbbells needed, fits your
            available 30 min. Choose a convenient route.
          </p>
          <span className="wk-suggestion">Suggestion · not active yet</span>
          <button className="wk-primary">Confirm replacement</button>
          <p className="wk-small">
            The strength workout stays planned until you confirm. Replacing an
            activity does not change your nutrition target.
          </p>
        </aside>
      </div>
      {/* catalog */}
      <section>
        <div className="wk-catalog-head">
          <h2>Exercise catalog</h2>
          <button className="wk-link">All exercises →</button>
        </div>

        <div className="wk-catalog-filters">
          <div>
            <label>Search</label>
            <div className="wk-input-wrap">
              <input placeholder="Exercise name" />
              <Search size={13} />
            </div>
          </div>
          <div>
            <label>Muscle group</label>
            <div className="wk-input-wrap">
              <select defaultValue="all">
                <option value="all">All groups</option>
                <option value="legs">Legs</option>
                <option value="glutes">Glutes</option>
                <option value="back">Back</option>
              </select>
              <ChevronDown size={13} />
            </div>
          </div>
          <div>
            <label>Equipment</label>
            <div className="wk-input-wrap">
              <select defaultValue="any">
                <option value="any">Any</option>
                <option value="dumbbells">Dumbbells</option>
                <option value="mat">Mat</option>
                <option value="none">No equipment</option>
              </select>
              <ChevronDown size={13} />
            </div>
          </div>
          <div>
            <label>Level</label>
            <div className="wk-input-wrap">
              <select defaultValue="beginner">
                <option value="beginner">Beginner</option>
                <option value="medium">Intermediate</option>
                <option value="advanced">Advanced</option>
              </select>
              <ChevronDown size={13} />
            </div>
          </div>
        </div>
        <div className="wk-exercises">
          <div className="wk-card wk-ex-card">
            <div className="wk-ex-top">
              <div className="wk-icon">
                <Dumbbell size={20} />
              </div>
              <div>
                <strong>Dumbbell squats</strong>
                <span>Legs · dumbbells · 3 × 12</span>
              </div>
            </div>
            <button className="wk-link">Technique and alternatives →</button>
          </div>
          <div className="wk-card wk-ex-card">
            <div className="wk-ex-top">
              <div className="wk-icon">
                <Activity size={20} />
              </div>
              <div>
                <strong>Glute bridge</strong>
                <span>Glutes · mat · 3 × 15</span>
              </div>
            </div>
            <button className="wk-link">Technique and alternatives →</button>
          </div>
          <div className="wk-card wk-ex-card">
            <div className="wk-ex-top">
              <div className="wk-icon">
                <Footprints size={20} />
              </div>
              <div>
                <strong>Brisk walking</strong>
                <span>Full body · no equipment</span>
              </div>
            </div>
            <button className="wk-link">Technique and alternatives →</button>
          </div>
        </div>
      </section>
      {/* last notes */}
      <div className="wk-recent">
        <span>Recent entries</span>
        <div className="wk-recent-chips">
          <span className="wk-chip">23 Oct · Modified: walking 20 min</span>
          <span className="wk-chip">22 Oct · Skipped: morning exercise</span>
        </div>
        <Link to="/history" className="wk-link">
          Go to history →
        </Link>
      </div>
      <p className="wk-footer">VEYA · Reach your goals at your own pace</p>
    </div>
  );
}

export default Workouts;