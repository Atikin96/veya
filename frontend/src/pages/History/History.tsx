import { useState } from "react";
import "./History.css";
import { Download, CalendarDays } from "lucide-react";

function History() {
  const [tab, setTab] = useState("overview");

  return (
    <div className="history">
      <div className="hs-heading">
        <div>
          <h1>Your journey, without judgment</h1>
          <p>Food and activity logs, and your decisions about plan changes.</p>
        </div>
        <button className="hs-export">
          <Download size={14} />
          Export entries
        </button>
      </div>

      {/* Tab + period */}
      <div className="hs-toolbar">
        <div className="hs-tabs">
          <button
            className={tab === "overview" ? "active" : ""}
            onClick={() => setTab("overview")}
          >
            Overview
          </button>
          <button
            className={tab === "nutrition" ? "active" : ""}
            onClick={() => setTab("nutrition")}
          >
            Nutrition
          </button>
          <button
            className={tab === "activity" ? "active" : ""}
            onClick={() => setTab("activity")}
          >
            Activity
          </button>
          <button
            className={tab === "changes" ? "active" : ""}
            onClick={() => setTab("changes")}
          >
            Plan changes
          </button>
        </div>

        <button className="hs-period">
          <CalendarDays size={14} />
          3–24 October 2026
        </button>
      </div>
      {/* Upper row */}
      <div className="hs-top">
        <div className="hs-card">
          <div className="hs-card-head">
            <h2>Weight trend</h2>
            <span className="hs-link">Example data</span>
          </div>

          <div className="hs-weights">
            <div>
              <strong className="hs-big">83.6 kg</strong>
              <span>Latest entry · 24 Oct</span>
            </div>
            <div>
              <strong>84.0 kg</strong>
              <span>First entry · 3 Oct</span>
            </div>
            <div>
              <strong>78.0 kg</strong>
              <span>Personal goal</span>
            </div>
          </div>

          <svg viewBox="0 0 400 100" className="hs-chart">
            <line x1="0" y1="25" x2="400" y2="25" className="hs-grid" />
            <line x1="0" y1="60" x2="400" y2="60" className="hs-grid" />
            <polyline
              points="5,12 135,30 265,45 395,85"
              className="hs-line"
            />
            <circle cx="5" cy="12" r="2.5" className="hs-dot" />
            <circle cx="135" cy="30" r="2.5" className="hs-dot" />
            <circle cx="265" cy="45" r="2.5" className="hs-dot" />
            <circle cx="395" cy="85" r="2.5" className="hs-dot" />
          </svg>

          <div className="hs-chart-labels">
            <span>3 Oct</span>
            <span>10 Oct</span>
            <span>17 Oct</span>
            <span>24 Oct</span>
          </div>
        </div>

        <div className="hs-card">
          <h2>Add measurement</h2>

          <div className="hs-form-row">
            <div>
              <label>Weight</label>
              <div className="hs-input-wrap">
                <input defaultValue="83.6" />
                <em>kg</em>
              </div>
            </div>
            <div>
              <label>Date</label>
              <input defaultValue="24 Oct 2026" />
            </div>
          </div>

          <button className="hs-outline">
            <span>＋</span> Save entry
          </button>

          <p className="hs-small">
            Measurements are entered manually. The chart shows your entries, not a
            medical assessment.
          </p>
        </div>
      </div>

      {/* Low row */}
      <div className="hs-bottom">
        {/* List of notes */}
        <section>
          <div className="hs-section-head">
            <h2>Entry log</h2>
            <button className="hs-link">Choose date →</button>
          </div>

          <div className="hs-card hs-day">
            <div className="hs-day-head">
              <h3>Today, 24 October</h3>
              <span>Day in progress</span>
            </div>

            <div className="hs-entry">
              <span className="hs-time">08:30</span>
              <span className="hs-entry-name">Breakfast · oatmeal with berries</span>
              <strong>420 kcal</strong>
            </div>
            <div className="hs-entry">
              <span className="hs-time">09:00</span>
              <span className="hs-entry-name">Stretching · completed</span>
              <strong>15 min</strong>
            </div>
            <div className="hs-entry">
              <span className="hs-time">13:15</span>
              <span className="hs-entry-name">Lunch · salmon, rice and vegetables</span>
              <strong>820 kcal</strong>
            </div>

            <div className="hs-day-footer">
              <span>Logged 1 240 kcal · target 2 000</span>
              <span className="hs-pill warning">Review awaiting a decision</span>
            </div>
          </div>

          <div className="hs-card hs-day">
            <div className="hs-day-head">
              <h3>Friday, 23 October</h3>
              <span>Entries saved</span>
            </div>

            <div className="hs-entry">
              <span className="hs-time">08:20</span>
              <span className="hs-entry-name">Breakfast · cottage cheese with fruit</span>
              <strong>410 kcal</strong>
            </div>
            <div className="hs-entry">
              <span className="hs-time">13:00</span>
              <span className="hs-entry-name">Lunch · soup and rice with turkey</span>
              <strong>650 kcal</strong>
            </div>
            <div className="hs-entry">
              <span className="hs-time">16:30</span>
              <span className="hs-entry-name">Snack · yogurt and berries</span>
              <strong>220 kcal</strong>
            </div>
            <div className="hs-entry">
              <span className="hs-time">19:10</span>
              <span className="hs-entry-name">Dinner · fish with potatoes</span>
              <strong>700 kcal</strong>
            </div>

            <div className="hs-day-footer">
              <span>Logged 1 980 kcal · target 2 000</span>
              <span className="hs-pill success">Walking 20 min · completed</span>
            </div>
          </div>
        </section>
        {/* Solutions for plan */}
        <section>
          <div className="hs-section-head">
            <h2>Plan decisions</h2>
          </div>
          <div className="hs-card hs-decisions">
            <div className="hs-decision">
              <div className="hs-decision-top">
                <span>24 Oct · 14:10</span>
                <span className="hs-pill warning">Awaiting a decision</span>
              </div>
              <h4>Lunch +180 kcal, evening 30 min</h4>
              <p>
                A new snack, dinner serving and walk were suggested. The original
                plan is still active.
              </p>
            </div>
            <div className="hs-decision">
              <div className="hs-decision-top">
                <span>23 Oct · 17:40</span>
                <span className="hs-pill success">Accepted</span>
              </div>
              <h4>Less time for a walk</h4>
              <p>
                You accepted the replacement: walking 30 → 20 min. Only the
                upcoming activity changed.
              </p>
            </div>
            <div className="hs-decision">
              <div className="hs-decision-top">
                <span>22 Oct · 15:20</span>
                <span className="hs-pill gray">Declined</span>
              </div>
              <h4>Dinner replacement suggested</h4>
              <p>
                You declined the suggestion. The original dinner stayed planned;
                morning exercise was marked skipped.
              </p>
            </div>
            <div className="hs-decision">
              <div className="hs-decision-top">
                <span>21 Oct · 16:05</span>
                <span className="hs-pill success">Edited and accepted</span>
              </div>
              <h4>Snack adjusted manually</h4>
              <p>
                You edited the suggested serving and confirmed the updated option.
              </p>
            </div>
          </div>
        </section>
      </div>
      <p className="hs-footer">VEYA · Reach your goals at your own pace</p>
    </div>
  );
}

export default History;