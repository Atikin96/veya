
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Login from "./Login";

describe("Login component", () => {
  it("renders the login form", () => {
    render(<Login onLogin={vi.fn()} onGoRegister={vi.fn()} />);

    expect(
      screen.getByRole("heading", { name: "Welcome back" }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("button", { name: /sign in/i }),
    ).toBeInTheDocument();
  });

  it("shows errors when submitting an empty form", async () => {
    const user = userEvent.setup();

    render(<Login onLogin={vi.fn()} onGoRegister={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(screen.getByText("Please enter your email.")).toBeInTheDocument();
    expect(
      screen.getByText("Please enter your password."),
    ).toBeInTheDocument();
  });

  it("shows an error for an invalid email", async () => {
    const user = userEvent.setup();

    render(<Login onLogin={vi.fn()} onGoRegister={vi.fn()} />);

    await user.type(screen.getByLabelText("Email"), "invalid-email");
    await user.type(screen.getByLabelText("Password"), "password123");

    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(
      screen.getByText("Please enter a valid email address."),
    ).toBeInTheDocument();
  });

  it("calls onLogin when the fields are valid", async () => {
    const user = userEvent.setup();
    const onLogin = vi.fn();

    render(<Login onLogin={onLogin} onGoRegister={vi.fn()} />);

    await user.type(screen.getByLabelText("Email"), "anna@example.com");
    await user.type(screen.getByLabelText("Password"), "password123");

    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(onLogin).toHaveBeenCalledOnce();
  });
});
