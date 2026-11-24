
import { useState, type FormEvent } from "react";
import "../Registration/Registration.css";
import "./Login.css";
import { Sprout, Eye, EyeOff, ArrowRight } from "lucide-react";

type LoginProps = {
  onLogin: () => void;
  onGoRegister: () => void;
};

type LoginErrors = {
  email?: string;
  password?: string;
};

function Login({ onLogin, onGoRegister }: LoginProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState<LoginErrors>({});

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    const newErrors: LoginErrors = {};

    if (!email.trim()) {
      newErrors.email = "Please enter your email.";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      newErrors.email = "Please enter a valid email address.";
    }

    if (!password) {
      newErrors.password = "Please enter your password.";
    }

    setErrors(newErrors);

    if (Object.keys(newErrors).length > 0) {
      return;
    }

    onLogin();
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
          <span>New to VEYA?</span>
          <button
            type="button"
            className="rg-signin-btn"
            onClick={onGoRegister}
          >
            Create account
          </button>
        </div>
      </header>

      <main className="lg-main">
        <form className="rg-form" onSubmit={handleSubmit} noValidate>
          <h1>Welcome back</h1>

          <p className="rg-subtitle">
            Sign in to continue with your plan.
          </p>

          <label htmlFor="login-email">Email</label>
          <input
            id="login-email"
            type="email"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              setErrors((prev) => ({ ...prev, email: undefined }));
            }}
            placeholder="anna@example.com"
            aria-invalid={Boolean(errors.email)}
          />

          {errors.email && <p className="rg-error">{errors.email}</p>}

          <label htmlFor="login-password">Password</label>

          <div className="rg-input-wrap">
            <input
              id="login-password"
              type={showPassword ? "text" : "password"}
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setErrors((prev) => ({ ...prev, password: undefined }));
              }}
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

          <div className="rg-bottom">
            <button type="submit" className="rg-create">
              <ArrowRight size={14} />
              Sign in
            </button>

            <button
              type="button"
              className="lg-link"
              onClick={onGoRegister}
            >
              Create an account
            </button>
          </div>

          <p className="rg-disclaimer">
            VEYA is an assistant for healthy adults, not a medical service.
            Mockup with example data.
          </p>
        </form>
      </main>
    </div>
  );
}

export default Login;
