import { useState } from "react";
import "./Settings.css";
import { X, Plus, ShieldCheck, Sparkles, ChevronDown } from "lucide-react";

function Settings() {
  const [tab, setTab] = useState("profile");
  const handleSignOut = () => {
    localStorage.removeItem("registered");
    window.location.href = "/";
  };
  return (
    <div className="settings">
      <div className="st-heading">
        <div>
          <h1>Your goals and constraints</h1>
          <p>
            Your plan reflects your preferences. Every value can be changed.
          </p>
        </div>
        <button className="st-save">Save changes</button>
      </div>

      <div className="st-tabs">
        <button
          className={tab === "profile" ? "active" : ""}
          onClick={() => setTab("profile")}
        >
          Profile and goals
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
          className={tab === "privacy" ? "active" : ""}
          onClick={() => setTab("privacy")}
        >
          Privacy
        </button>
      </div>

      <div className="st-layout">
        {/* Left column */}
        <div className="st-column">
          {/* Profile */}
          <div className="st-card">
            <div className="st-card-head">
              <h2>Personal profile</h2>
              <span className="st-link">Example data</span>
            </div>

            <div className="st-row st-row-2">
              <div>
                <label>Name</label>
                <input defaultValue="Anna" />
              </div>
              <div>
                <label>Email</label>
                <input defaultValue="anna@example.com" />
              </div>
            </div>

            <div className="st-row st-row-3">
              <div>
                <label>Age</label>
                <div className="st-input-wrap">
                  <input defaultValue="32" />
                  <em>years</em>
                </div>
              </div>
              <div>
                <label>Height</label>
                <div className="st-input-wrap">
                  <input defaultValue="168" />
                  <em>cm</em>
                </div>
              </div>
              <div>
                <label>Current weight</label>
                <div className="st-input-wrap">
                  <input defaultValue="83.6" />
                  <em>kg</em>
                </div>
              </div>
            </div>

            <p className="st-small">
              Latest weight entry: 24 October 2026 · Edit in history →
            </p>
          </div>

          {/* Goals */}
          <div className="st-card">
            <h2>Goal and nutrition targets</h2>

            <div className="st-row st-row-2">
              <div>
                <label>My goal</label>
                <div className="st-input-wrap">
                  <select defaultValue="loss">
                    <option value="loss">Weight loss</option>
                    <option value="maintain">Maintain weight</option>
                    <option value="gain">Weight gain</option>
                  </select>
                  <ChevronDown size={13} />
                </div>
              </div>
              <div>
                <label>Target weight</label>
                <div className="st-input-wrap">
                  <input defaultValue="78.0" />
                  <em>kg</em>
                </div>
              </div>
            </div>

            <div className="st-row st-row-4">
              <div>
                <label>Calories</label>
                <div className="st-input-wrap">
                  <input defaultValue="2 000" />
                  <em>kcal</em>
                </div>
              </div>
              <div>
                <label>Protein</label>
                <div className="st-input-wrap">
                  <input defaultValue="120" />
                  <em>g</em>
                </div>
              </div>
              <div>
                <label>Fat</label>
                <div className="st-input-wrap">
                  <input defaultValue="65" />
                  <em>g</em>
                </div>
              </div>
              <div>
                <label>Carbs</label>
                <div className="st-input-wrap">
                  <input defaultValue="230" />
                  <em>g</em>
                </div>
              </div>
            </div>

            <p className="st-small">
              Targets are set manually for planning. Calories and macros are
              estimates and may not match when recalculated.
            </p>
          </div>

          {/* Diet */}
          <div className="st-card">
            <h2>Diet and exclusions</h2>

            <label>Add a food or ingredient to exclusions</label>
            <div className="st-input-wrap">
              <input placeholder="Start typing a name" />
              <Plus size={13} />
            </div>

            <div className="st-chips">
              <span className="st-chip green">
                Mushrooms
                <X size={11} />
              </span>
              <span className="st-chip gray">Regular diet</span>
            </div>

            <p className="st-small">
              Ongoing exclusions apply to new suggestions. For a specific
              search, you can also specify "no nuts" or "no chicken available".
            </p>
          </div>

          {/* Account */}
          <div className="st-card">
            <h2>Account management</h2>

            <div className="st-account-buttons">
              <button className="st-outline">Change password</button>
              <button className="st-text" onClick={handleSignOut}>
                Sign out
              </button>
            </div>

            <div className="st-danger">
              <p className="st-small">
                Deleting your account and all entries is permanent. Separate
                confirmation is required.
              </p>
              <button className="st-delete">Delete account</button>
            </div>
          </div>
        </div>

        {/* Right column */}
        <div className="st-column">
          {/* Time and equipment */}
          <div className="st-card">
            <h2>Time and equipment</h2>

            <label>Usual workout time</label>
            <div className="st-input-wrap">
              <select defaultValue="45">
                <option value="15">15 min</option>
                <option value="30">30 min</option>
                <option value="45">45 min</option>
                <option value="60">60 min</option>
              </select>
              <ChevronDown size={13} />
            </div>

            <label className="st-label-gap">Available at home</label>
            <div className="st-checks">
              <label className="st-check">
                <input type="checkbox" defaultChecked />
                Dumbbells
              </label>
              <label className="st-check">
                <input type="checkbox" defaultChecked />
                Mat
              </label>
              <label className="st-check">
                <input type="checkbox" />
                Exercise machines
              </label>
            </div>

            <div className="st-today">
              <label>Today only</label>
              <div className="st-chips">
                <span className="st-chip yellow">30 min this evening</span>
                <span className="st-chip yellow">No equipment</span>
              </div>
              <p className="st-small">
                Temporary constraints do not change your usual settings.
              </p>
            </div>
          </div>

          {/* Privacy */}
          <div className="st-card">
            <h2>Privacy</h2>

            <div className="st-consent">
              <ShieldCheck size={18} />
              <div>
                <strong>Health data consent</strong>
                <span>Given · 3 October 2026</span>
              </div>
            </div>

            <p className="st-text-small">
              Weight, nutrition and activity are used for your personal plan.
              Manage consent and export your entries.
            </p>

            <div className="st-account-buttons">
              <button className="st-outline">Export data</button>
              <button className="st-text">Withdraw consent</button>
            </div>

            <div className="st-checks st-checks-top">
              <label className="st-check">
                <input type="checkbox" defaultChecked />
                Remind me about planned meals and activities
              </label>
              <label className="st-check">
                <input type="checkbox" />
                Receive VEYA news by email
              </label>
            </div>

            <button className="st-link">Privacy policy →</button>
          </div>

          {/* Privacy */}
          <div className="st-note">
            <Sparkles size={18} />
            <div>
              <strong>An assistant, not a medical service</strong>
              <p>
                VEYA is for healthy adults 18+. It does not diagnose conditions
                or prescribe treatment. If you have a medical condition, are
                pregnant or have specific needs, discuss nutrition and exercise
                with a professional.
              </p>
            </div>
          </div>
        </div>
      </div>

      <p className="st-footer">VEYA · Reach your goals at your own pace</p>
    </div>
  );
}

export default Settings;
