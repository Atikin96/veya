import { useState } from "react";
import "./Nutrition.css";
import { Link } from "react-router-dom";
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Sunrise,
  Sun,
  Apple,
  Moon,
  Search,
  CircleCheck,
  Plus,
  Sparkles,
} from "lucide-react";

function Nutrition() {
  const [tab, setTab] = useState("search");

  return (
    <div className="nutrition">
      <div className="nt-heading">
        <div>
          <h1>Nutrition</h1>
          <p>
            24 October 2026, Saturday · Logged and planned meals kept separate.
          </p>
        </div>
        <button className="nt-today">
          <CalendarDays size={14} />
          Today
          <ChevronLeft size={14} />
          <ChevronRight size={14} />
        </button>
      </div>

      <div className="nt-stats">
        <div className="nt-stat">
          <span>Eaten</span>
          <strong>1 240 / 2 000 kcal</strong>
          <div className="nt-bar">
            <div className="nt-bar-fill" style={{ width: "62%" }}></div>
          </div>
        </div>
        <div className="nt-stat">
          <span>Protein</span>
          <strong>62 / 120 g</strong>
          <div className="nt-bar">
            <div className="nt-bar-fill" style={{ width: "52%" }}></div>
          </div>
        </div>
        <div className="nt-stat">
          <span>Fat</span>
          <strong>40 / 65 g</strong>
          <div className="nt-bar">
            <div className="nt-bar-fill" style={{ width: "62%" }}></div>
          </div>
        </div>
        <div className="nt-stat">
          <span>Carbs</span>
          <strong>150 / 230 g</strong>
          <div className="nt-bar">
            <div className="nt-bar-fill" style={{ width: "65%" }}></div>
          </div>
        </div>
      </div>

      <div className="nt-layout">
        {/* Left part */}
        <section>
          <div className="nt-section-head">
            <h2>Diary and plan</h2>
            <button className="nt-link">Weekly plan →</button>
          </div>

          <div className="nt-meal">
            <div className="nt-meal-top">
              <div className="nt-meal-icon">
                <Sunrise size={18} />
              </div>
              <div>
                <h3>Breakfast</h3>
                <span>08:30</span>
              </div>
              <div className="nt-meal-side">
                <strong>420 kcal</strong>
                <span className="nt-pill logged">Logged</span>
              </div>
            </div>
            <h4>Oatmeal with milk and berries</h4>
            <p>Oatmeal 250 g · berries 80 g · honey 10 g</p>
            <div className="nt-meal-bottom">
              <span>Planned 420 kcal · unchanged</span>
              <button className="nt-link">Edit</button>
            </div>
          </div>

          <div className="nt-meal">
            <div className="nt-meal-top">
              <div className="nt-meal-icon">
                <Sun size={18} />
              </div>
              <div>
                <h3>Lunch</h3>
                <span>13:15</span>
              </div>
              <div className="nt-meal-side">
                <strong>820 kcal</strong>
                <span className="nt-pill logged">Logged</span>
              </div>
            </div>
            <h4>Salmon with rice and vegetables</h4>
            <p>Salmon 180 g · rice 200 g · vegetables 150 g · oil</p>
            <div className="nt-meal-bottom">
              <span className="nt-warning">
                Planned 640 kcal · actual +180 kcal
              </span>
              <button className="nt-link">Edit</button>
            </div>
          </div>

          <div className="nt-meal">
            <div className="nt-meal-top">
              <div className="nt-meal-icon">
                <Apple size={18} />
              </div>
              <div>
                <h3>Snack</h3>
                <span>16:30</span>
              </div>
              <div className="nt-meal-side">
                <strong>250 kcal</strong>
                <span className="nt-pill planned">Planned</span>
              </div>
            </div>
            <h4>Yogurt with nuts</h4>
            <p>Yogurt 150 g · nuts 25 g</p>
            <div className="nt-meal-bottom">
              <span>Not eaten yet · can be changed</span>
              <div className="nt-actions">
                <button className="nt-link">Log</button>
                <Link to="/nutrition/replace-dinner" className="nt-link">
                  Replace
                </Link>
              </div>
            </div>
          </div>

          <div className="nt-meal">
            <div className="nt-meal-top">
              <div className="nt-meal-icon">
                <Moon size={18} />
              </div>
              <div>
                <h3>Dinner</h3>
                <span>19:00</span>
              </div>
              <div className="nt-meal-side">
                <strong>690 kcal</strong>
                <span className="nt-pill planned">Planned</span>
              </div>
            </div>
            <h4>Chicken and vegetable pasta</h4>
            <p>1 serving · P 42 g · F 20 g · C 80 g</p>
            <div className="nt-meal-bottom">
              <span>Not eaten yet · 30 min to cook</span>
              <div className="nt-actions">
                <button className="nt-link">Log</button>
                <button className="nt-link">Replace</button>
              </div>
            </div>
          </div>

          <div className="nt-note">
            <Sparkles size={18} />
            <div>
              <strong>760 kcal left to reach your target</strong>
              <p>
                Eating everything in the current plan totals 2 180 kcal. Only
                the remaining meals can be reviewed.
              </p>
            </div>
          </div>
        </section>

        {/* Right part */}
        <aside>
          <div className="nt-panel">
            <h2>Add food</h2>

            <div className="nt-tabs">
              <button
                className={tab === "search" ? "active" : ""}
                onClick={() => setTab("search")}
              >
                Food search
              </button>
              <button
                className={tab === "manual" ? "active" : ""}
                onClick={() => setTab("manual")}
              >
                Manual entry
              </button>
            </div>

            {tab === "search" ? (
              <div>
                <label>Name or brand</label>
                <div className="nt-input-wrap">
                  <input defaultValue="Yogurt" />
                  <Search size={13} />
                </div>

                <p className="nt-hint">Results · per 100 g</p>

                <div className="nt-result active">
                  <div>
                    <strong>Plain yogurt 2%</strong>
                    <span>60 kcal · P 4 · F 2 · C 6</span>
                  </div>
                  <CircleCheck size={16} />
                </div>
                <div className="nt-result">
                  <div>
                    <strong>Greek yogurt 2%</strong>
                    <span>73 kcal · P 9 · F 2 · C 4</span>
                  </div>
                  <Plus size={14} />
                </div>
                <div className="nt-result">
                  <div>
                    <strong>Strawberry yogurt</strong>
                    <span>95 kcal · P 3 · F 3 · C 16</span>
                  </div>
                  <Plus size={14} />
                </div>
              </div>
            ) : (
              <div>
                <label>Food or meal name</label>
                <input placeholder="For example, homemade soup" />
                <label>Calories per 100 g</label>
                <input type="number" placeholder="kcal" />
              </div>
            )}

            <div className="nt-row">
              <div>
                <label>Amount</label>
                <input type="number" defaultValue="150" />
              </div>
              <div>
                <label>Meal</label>
                <select defaultValue="snack">
                  <option value="breakfast">Breakfast</option>
                  <option value="lunch">Lunch</option>
                  <option value="snack">Snack</option>
                  <option value="dinner">Dinner</option>
                </select>
              </div>
            </div>

            <div className="nt-draft">
              <span>Draft · not logged</span>
              <strong>90 kcal</strong>
            </div>

            <button className="nt-primary">
              <Plus size={14} />
              Add to diary
            </button>
          </div>

          <div className="nt-panel">
            <h2 className="nt-small-title">Cannot find a food?</h2>
            <p className="nt-text">
              Enter the name, weight, calories and macros from the packaging or
              your own recipe.
            </p>
            <label>Food or meal name</label>
            <input placeholder="For example, homemade soup" />
            <button className="nt-outline" onClick={() => setTab("manual")}>
              Open manual entry
            </button>
          </div>

          <p className="nt-disclaimer">
            Nutrition values are estimates. You can edit the weight, calories
            and macros before saving.
          </p>
        </aside>
      </div>

      <p className="nt-footer">VEYA · Reach your goals at your own pace</p>
    </div>
  );
}

export default Nutrition;
