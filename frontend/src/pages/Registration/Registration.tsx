
import { useState, type FormEvent } from "react";
import "./Registration.css";
import forest from "../../assets/images/forest.png";
import {
  Sprout,
  Utensils,
  RefreshCw,
  ShieldCheck,
  Eye,
  EyeOff,
  CircleCheck,
  ArrowRight,
  ExternalLink,
} from "lucide-react";

type RegistrationProps = {
  onCreate: () => void;
  onSignIn: () => void;
};

type FormErrors = {
  name?: string;
  email?: string;
  password?: string;
  confirmPassword?: string;
  age?: string;
  terms?: string;
};

function Registration({ onCreate, onSignIn }: RegistrationProps) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [isAdult, setIsAdult] = useState(false);
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [healthConsent, setHealthConsent] = useState(false);

  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState<FormErrors>({});

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    const newErrors: FormErrors = {};

    if (!name.trim()) {
      newErrors.name = "Please enter your name.";
    }

    if (!email.trim()) {
      newErrors.email = "Please enter your email.";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      newErrors.email = "Please enter a valid email address.";
    }

    if (!password) {
      newErrors.password = "Please enter a password.";
    } else if (
      password.length < 8 ||
      !/[a-zA-Z]/.test(password) ||
      !/[0-9]/.test(password)
    ) {
      newErrors.password =
        "Password must contain at least 8 characters, including letters and numbers.";
    }

    if (!confirmPassword) {
      newErrors.confirmPassword = "Please confirm your password.";
    } else if (password !== confirmPassword) {
      newErrors.confirmPassword = "Passwords do not match.";
    }

    if (!isAdult) {
      newErrors.age = "You must confirm that you are 18 or older.";
    }

    if (!acceptedTerms) {
      newErrors.terms = "You must accept the Terms of Use and Privacy Policy.";
    }

    setErrors(newErrors);

    if (Object.keys(newErrors).length > 0) {
      return;
    }

    // Save the optional consent for the next development stage.
    localStorage.setItem("healthConsent", String(healthConsent));

    onCreate();
  };

  return (
    <div className="registration">
      <header className="rg-header">
        <div className="rg-logo">
          <span className="rg-logo-icon">
            <Sprout size={20} />
          </span>
          <span className="rg-logo-text">veya</span>
        </div>

        <div className="rg-signin">
          <span>Already have an account?</span>
          <button type="button" className="rg-signin-btn" onClick={onSignIn}>
            Sign in
          </button>
        </div>
      </header>

      <main className="rg-main">
        <aside className="rg-panel">
          <div className="rg-panel-label">
            <Sprout size={14} />
            <span>YOUR SPACE FOR SELF-CARE</span>
          </div>

          <h2>Nutrition and movement. Built around your life.</h2>

          <p className="rg-panel-text">
            Build your plan with simple steps. Adjust it as your day changes.
            The decision is always yours.
          </p>

          <img src={forest} alt="Forest path" />

          <div className="rg-feature">
            <Utensils size={16} />
            <div>
              <strong>Nutrition made simple</strong>
              <span>A diary, plan and swaps for the food you have.</span>
            </div>
          </div>

          <div className="rg-feature">
            <RefreshCw size={16} />
            <div>
              <strong>Flexibility, not a fresh start</strong>
              <span>Only upcoming items, and only with your consent.</span>
            </div>
          </div>

          <div className="rg-feature">
            <ShieldCheck size={16} />
            <div>
              <strong>Control over your data</strong>
              <span>Manage consent in settings.</span>
            </div>
          </div>
        </aside>

        <form className="rg-form" onSubmit={handleSubmit} noValidate>
          <div className="rg-steps">
            <div className="rg-step active">
              <span>1</span>
              Account
            </div>
            <div className="rg-step">
              <span>2</span>
              Profile
            </div>
            <div className="rg-step">
              <span>3</span>
              My plan
            </div>
          </div>

          <h1>Create your account</h1>

          <p className="rg-subtitle">
            Start with the basics. You can add your goals and preferences in the
            next step.
          </p>

          <label htmlFor="rg-name">What should we call you?</label>
          <input
            id="rg-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Your name"
            aria-invalid={Boolean(errors.name)}
          />
          {errors.name && <p className="rg-error">{errors.name}</p>}

          <label htmlFor="rg-email">Email</label>
          <input
            id="rg-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="anna@example.com"
            aria-invalid={Boolean(errors.email)}
          />
          {errors.email && <p className="rg-error">{errors.email}</p>}

          <div className="rg-passwords">
            <div>
              <label htmlFor="rg-password">Password</label>
              <div className="rg-input-wrap">
                <input
                  id="rg-password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Create a password"
                  aria-invalid={Boolean(errors.password)}
                />
                <button
                  type="button"
                  className="rg-eye"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff size={14} /> : <Eye size={14} />}
                </button>
              </div>
              {errors.password && (
                <p className="rg-error">{errors.password}</p>
              )}
            </div>

            <div>
              <label htmlFor="rg-confirm-password">Confirm password</label>
              <div className="rg-input-wrap">
                <input
                  id="rg-confirm-password"
                  type={showPassword ? "text" : "password"}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Repeat your password"
                  aria-invalid={Boolean(errors.confirmPassword)}
                />
                <button
                  type="button"
                  className="rg-eye"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff size={14} /> : <Eye size={14} />}
                </button>
              </div>
              {errors.confirmPassword && (
                <p className="rg-error">{errors.confirmPassword}</p>
              )}
            </div>
          </div>

          <p className="rg-hint">
            <CircleCheck size={13} />
            At least 8 characters, including letters and numbers.
          </p>

          <div className="rg-checks">
            <label className="rg-check">
              <input
                type="checkbox"
                checked={isAdult}
                onChange={(e) => setIsAdult(e.target.checked)}
              />
              I am 18 or older.
            </label>
            {errors.age && <p className="rg-error">{errors.age}</p>}

            <label className="rg-check">
              <input
                type="checkbox"
                checked={acceptedTerms}
                onChange={(e) => setAcceptedTerms(e.target.checked)}
              />
              I accept the Terms of Use and confirm that I have read the Privacy
              Policy.
            </label>
            {errors.terms && <p className="rg-error">{errors.terms}</p>}

            <div className="rg-links">
              <a href="#">
                Terms of Use <ExternalLink size={10} />
              </a>
              <a href="#">
                Privacy Policy <ExternalLink size={10} />
              </a>
            </div>
          </div>

          <div className="rg-consent">
            <label className="rg-check">
              <input
                type="checkbox"
                checked={healthConsent}
                onChange={(e) => setHealthConsent(e.target.checked)}
              />
              I give separate consent to process my health data: weight,
              nutrition and physical activity, for personal planning in VEYA.
            </label>

            <p>
              Optional at this step. You can create an account without this
              consent, but a personal plan will not be available. You can
              withdraw consent in settings.
            </p>

            <a href="#">
              Read the health data processing consent <ExternalLink size={10} />
            </a>
          </div>

          <div className="rg-bottom">
            <button type="submit" className="rg-create">
              <ArrowRight size={14} />
              Create account
            </button>
            <span>Next: your profile, goals and constraints.</span>
          </div>

          <p className="rg-disclaimer">
            VEYA is an assistant for healthy adults, not a medical service. Not
            intended for diagnosis or treatment. Mockup with example data.
          </p>
        </form>
      </main>
    </div>
  );
}

export default Registration;
