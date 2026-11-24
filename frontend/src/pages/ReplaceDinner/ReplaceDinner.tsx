import "./ReplaceDinner.css";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import "./ReplaceDinner.css";
import dinner from "../../assets/images/dinner.png"
import option1 from "../../assets/images/option1.png"
import option2 from "../../assets/images/option2.png"
import option3 from "../../assets/images/option3.png"
import { ArrowLeft, ArrowRight, CircleCheck } from "lucide-react";

function ReplaceDinner() {
  const [selected, setSelected] = useState("turkey");
  const navigate = useNavigate();

  // calories
  let kcal = 680;
  if (selected === "tuna") kcal = 700;
  if (selected === "tofu") kcal = 660;

  const diff = kcal - 690;
  const forecast = 2180 + diff;

  return (
    <div className="replace">
      <div className="rp-heading">
        <div>
          <h1>Replace dinner?</h1>
          <p>
            24 October 2026 · Find a similar meal that meets your constraints.
          </p>
        </div>
        <Link to="/nutrition" className="rp-back">
          <ArrowLeft size={14} />
          Back to diary
        </Link>
      </div>

      <div className="rp-filters">
        <div>
          <label>Unavailable ingredient</label>
          <select defaultValue="chicken">
            <option value="chicken">Chicken</option>
            <option value="fish">Fish</option>
            <option value="eggs">Eggs</option>
          </select>
        </div>
        <div>
          <label>Exclude from suggestions</label>
          <select defaultValue="nuts">
            <option value="nuts">Nuts</option>
            <option value="dairy">Dairy</option>
            <option value="gluten">Gluten</option>
          </select>
        </div>
        <div>
          <label>Cooking time</label>
          <select defaultValue="30">
            <option value="15">Up to 15 min</option>
            <option value="30">Up to 30 min</option>
            <option value="45">Up to 45 min</option>
          </select>
        </div>
      </div>

      <div className="rp-layout">
        {/* Current dinner */}
        <div className="rp-card rp-current">
          <span className="rp-label">CURRENT DINNER · 19:00</span>
          <img src={dinner} alt="Chicken and vegetable pasta" />
          <h2>Chicken and vegetable pasta</h2>
          <p className="rp-current-kcal">
            <strong>690</strong> kcal / serving
          </p>
          <p className="rp-small">P 42 g · F 20 g · C 80 g</p>
          <div className="rp-current-bottom">
            <span className="rp-chip">Not eaten yet</span>
            <p className="rp-small">Reason: no chicken at home.</p>
          </div>
        </div>

        {/* Options */}
        <div className="rp-options">
          <div className="rp-options-head">
            <h2>Suitable options</h2>
            <span>3 meals</span>
          </div>

          <button
            className={selected === "turkey" ? "rp-option active" : "rp-option"}
            onClick={() => setSelected("turkey")}
          >
            <img
              src={option1}
              alt="Turkey and vegetable pasta"
            />
            <div className="rp-option-info">
              <h3>Turkey and vegetable pasta</h3>
              <strong>680 kcal · 25 min</strong>
              <span>P 41 g · F 18 g · C 82 g</span>
            </div>
            <div className="rp-option-bottom">
              <span>Similar calories and protein</span>
              <span className="rp-select-text">
                {selected === "turkey" ? "Selected" : "Select"}
              </span>
            </div>
            <div className="rp-radio">
              {selected === "turkey" ? (
                <CircleCheck size={18} />
              ) : (
                <span></span>
              )}
            </div>
          </button>

          <button
            className={selected === "tuna" ? "rp-option active" : "rp-option"}
            onClick={() => setSelected("tuna")}
          >
            <img src={option2} alt="Tuna and vegetable pasta" />
            <div className="rp-option-info">
              <h3>Tuna and vegetable pasta</h3>
              <strong>700 kcal · 20 min</strong>
              <span>P 44 g · F 18 g · C 81 g</span>
            </div>
            <div className="rp-option-bottom">
              <span>Quick to make with canned tuna</span>
              <span className="rp-select-text">
                {selected === "tuna" ? "Selected" : "Select"}
              </span>
            </div>
            <div className="rp-radio">
              {selected === "tuna" ? <CircleCheck size={18} /> : <span></span>}
            </div>
          </button>

          <button
            className={selected === "tofu" ? "rp-option active" : "rp-option"}
            onClick={() => setSelected("tofu")}
          >
            <img src={option3} alt="Tofu and vegetable pasta" />
            <div className="rp-option-info">
              <h3>Tofu and vegetable pasta</h3>
              <strong>660 kcal · 25 min</strong>
              <span>P 36 g · F 21 g · C 85 g</span>
            </div>
            <div className="rp-option-bottom">
              <span>Plant-based protein option</span>
              <span className="rp-select-text">
                {selected === "tofu" ? "Selected" : "Select"}
              </span>
            </div>
            <div className="rp-radio">
              {selected === "tofu" ? <CircleCheck size={18} /> : <span></span>}
            </div>
          </button>
        </div>

        {/* Changes */}
        <div className="rp-card rp-change">
          <h2>What will change</h2>
          <span className="rp-chip">Dinner only</span>

          <div className="rp-compare">
            <div>
              <span>Before</span>
              <strong>690</strong>
            </div>
            <ArrowRight size={16} />
            <div>
              <span>After, kcal</span>
              <strong>{kcal}</strong>
            </div>
          </div>

          <div className="rp-diff">
            <span>Difference</span>
            <strong>
              {diff > 0 ? "+" : "−"}
              {Math.abs(diff)} kcal
            </strong>
          </div>

          <p className="rp-small">
            Daily forecast: 2 180 → {forecast.toLocaleString("ru-RU")} kcal.
            This replaces one meal, not the whole rest of the day.
          </p>
        <button className="rp-confirm" onClick={() => navigate("/nutrition/plan-review")}>
            Confirm replacement
        </button>
          <Link to="/nutrition" className="rp-cancel">
            Cancel
          </Link>

          <p className="rp-small">
            Until you confirm, the 690 kcal dinner stays in your plan.
          </p>
        </div>
      </div>

      {/* Recipe */}
      <div className="rp-card rp-recipe">
        <div className="rp-recipe-head">
          <h2>Turkey pasta: what you need</h2>
          <span className="rp-chip">1 serving · 25 min</span>
        </div>
        <div className="rp-recipe-body">
          <div>
            <h4>Ingredients</h4>
            <p>
              Pasta 90 g (dry) · turkey 130 g · Zucchini and tomatoes 180 g ·
              oil 10 g · Basil and spices to taste
            </p>
          </div>
          <div>
            <h4>How to cook</h4>
            <p>
              Boil the pasta. Sauté the turkey and vegetables until cooked
              through. Add a little pasta water and combine. Sprinkle with
              basil.
            </p>
          </div>
        </div>
      </div>

      <p className="rp-footer">
        Calories and macros are estimates. Logged breakfast and lunch stay
        unchanged.
      </p>
      <p className="rp-footer">VEYA · Reach your goals at your own pace</p>
    </div>
  );
}

export default ReplaceDinner;
