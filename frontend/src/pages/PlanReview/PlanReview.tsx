import { Link, useNavigate } from "react-router-dom";
import "./PlanReview.css";
import { Apple, Utensils, Footprints, Lock, Sparkles } from "lucide-react";

function PlanReview() {
  const navigate = useNavigate();

  return (
    <div className="plan-review">
      <div className="pr-heading">
        <div>
          <h1>Bring your plan back into rhythm</h1>
          <p>24 October 2026, Saturday · A proposal for the rest of your day.</p>
        </div>
        <span className="pr-badge">Awaiting your decision</span>
      </div>

      <div className="pr-top">
        <div className="pr-reason">
          <span className="pr-label">REASON FOR REVIEW</span>
          <strong>Lunch: 820 instead of 640 kcal</strong>
          <span className="pr-warning">+180 kcal over planned lunch</span>
        </div>

        <div>
          <label>Time this evening</label>
          <select defaultValue="30">
            <option value="15">15 min</option>
            <option value="30">30 min</option>
            <option value="45">45 min</option>
          </select>
        </div>

        <div>
          <label>Available equipment</label>
          <select defaultValue="none">
            <option value="none">No equipment</option>
            <option value="dumbbells">Dumbbells</option>
            <option value="mat">Yoga mat</option>
          </select>
        </div>
      </div>

      <div className="pr-layout">
        {/* left part */}
        <section>
          <div className="pr-section-head">
            <h2>Change only what is still ahead</h2>
            <span>3 suggestions</span>
          </div>

          <div className="pr-item">
            <div className="pr-item-top">
              <h3>
                <Apple size={18} />
                Snack · 16:30
              </h3>
              <span className="pr-pill">−100 kcal</span>
            </div>
            <div className="pr-change">
              <span>Yogurt with nuts · 250 kcal</span>
              <span className="pr-arrow">→</span>
              <strong>Fruit · 150 kcal</strong>
            </div>
            <div className="pr-item-bottom">
              <p>
                A lighter snack offsets part of the extra lunch calories. For
                example, an apple and a banana, about 280 g.
              </p>
              <button className="pr-link">Edit</button>
            </div>
          </div>

          <div className="pr-item">
            <div className="pr-item-top">
              <h3>
                <Utensils size={18} />
                Dinner · 19:00
              </h3>
              <span className="pr-pill">−80 kcal</span>
            </div>
            <div className="pr-change">
              <span>Chicken pasta · 690 kcal</span>
              <span className="pr-arrow">→</span>
              <strong>Smaller serving · 610 kcal</strong>
            </div>
            <div className="pr-item-bottom">
              <p>
                The ingredients stay the same; the serving is reduced to about 88%
                of the original. This covers the remaining 80 kcal.
              </p>
              <button className="pr-link">Edit</button>
            </div>
          </div>

          <div className="pr-item">
            <div className="pr-item-top">
              <h3>
                <Footprints size={18} />
                Activity · 18:00
              </h3>
              <span className="pr-pill">−20 min</span>
            </div>
            <div className="pr-change">
              <span>Strength workout · 45 min</span>
              <span className="pr-arrow">→</span>
              <strong>Brisk walking · 25 min</strong>
            </div>
            <div className="pr-item-bottom">
              <p>
                Only 30 minutes are available this evening, with no dumbbells.
                Walking needs no equipment and leaves 5 minutes to prepare.
              </p>
              <button className="pr-link">Edit</button>
            </div>
          </div>

          <div className="pr-locked">
            <Lock size={16} />
            <div>
              <strong>Logged entries stay unchanged</strong>
              <p>Breakfast 420 kcal · lunch 820 kcal · 15 min stretching completed.</p>
            </div>
          </div>
        </section>

        {/* right part */}
        <aside>
          <div className="pr-card">
            <h2>Your day after changes</h2>
            <span className="pr-label">NUTRITION FORECAST, NOT LOGGED</span>

            <p className="pr-total">
              <strong>2 000</strong> / 2 000
            </p>
            <p className="pr-small">kcal if you follow the new plan</p>

            <div className="pr-bar">
              <div className="pr-bar-logged"></div>
              <div className="pr-bar-plan"></div>
            </div>

            <div className="pr-row">
              <span>Already logged</span>
              <strong>1 240 kcal</strong>
            </div>
            <div className="pr-row">
              <span>New remaining plan</span>
              <strong>760 kcal</strong>
            </div>
            <div className="pr-row">
              <span>Food calorie reduction</span>
              <strong>−180 kcal</strong>
            </div>

            <p className="pr-small pr-card-note">
              Current forecast: 2 180 kcal. After confirmation: 2 000 kcal. Your
              2 000 kcal target stays the same.
            </p>
          </div>

          <div className="pr-tip">
            <Sparkles size={18} />
            <div>
              <strong>Walking is not food "compensation"</strong>
              <p>
                Duration is adjusted to your available time. Activity calories are
                not subtracted from food intake.
              </p>
            </div>
          </div>

          <p className="pr-small">
            This is a separate review scenario: dinner still contains chicken; only
            the serving size changes.
          </p>
        </aside>
      </div>

      <div className="pr-decision">
        <div>
          <h2>You decide what changes</h2>
          <p>
            Your plan stays the same until you confirm. If you decline, all
            original items are kept.
          </p>
        </div>
        <div className="pr-buttons">
          <button className="pr-accept" onClick={() => navigate("/")}>
            Accept changes
          </button>
          <button className="pr-edit">Edit</button>
          <Link to="/nutrition" className="pr-decline">
            Decline
          </Link>
        </div>
      </div>

      <p className="pr-footer">VEYA · Reach your goals at your own pace</p>
    </div>
  );
}

export default PlanReview;