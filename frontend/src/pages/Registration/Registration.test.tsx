
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Registration from "./Registration";

describe("Registration component", () => {
  it("renders the registration form", () => {
    render(<Registration onCreate={vi.fn()} onSignIn={vi.fn()} />);

    expect(
      screen.getByRole("heading", { name: "Create your account" }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("button", { name: /create account/i }),
    ).toBeInTheDocument();
  });

  it("shows validation errors when submitting an empty form", async () => {
    const user = userEvent.setup();

    render(<Registration onCreate={vi.fn()} onSignIn={vi.fn()} />);

    await user.click(
      screen.getByRole("button", { name: /create account/i }),
    );

    expect(screen.getByText("Please enter your name.")).toBeInTheDocument();
    expect(screen.getByText("Please enter your email.")).toBeInTheDocument();
    expect(screen.getByText("Please enter a password.")).toBeInTheDocument();
    expect(
      screen.getByText("Please confirm your password."),
    ).toBeInTheDocument();
  });

  it("creates an account when all required fields are valid", async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();

    render(<Registration onCreate={onCreate} onSignIn={vi.fn()} />);

    await user.type(screen.getByLabelText("What should we call you?"), "Anna");
    await user.type(screen.getByLabelText("Email"), "anna@example.com");
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.type(screen.getByLabelText("Confirm password"), "password123");

    await user.click(screen.getByLabelText("I am 18 or older."));
    await user.click(
      screen.getByLabelText(
        /I accept the Terms of Use and confirm that I have read the Privacy Policy/,
      ),
    );

    await user.click(
      screen.getByRole("button", { name: /create account/i }),
    );

    expect(onCreate).toHaveBeenCalledOnce();
  });

  it("does not require health data consent to create an account", async () => {
    const user = userEvent.setup();
    const onCreate = vi.fn();

    render(<Registration onCreate={onCreate} onSignIn={vi.fn()} />);

    await user.type(screen.getByLabelText("What should we call you?"), "Anna");
    await user.type(screen.getByLabelText("Email"), "anna@example.com");
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.type(screen.getByLabelText("Confirm password"), "password123");

    await user.click(screen.getByLabelText("I am 18 or older."));
    await user.click(
      screen.getByLabelText(
        /I accept the Terms of Use and confirm that I have read the Privacy Policy/,
      ),
    );

    expect(
      screen.getByLabelText(
        /I give separate consent to process my health data/,
      ),
    ).not.toBeChecked();

    await user.click(
      screen.getByRole("button", { name: /create account/i }),
    );

    expect(onCreate).toHaveBeenCalledOnce();
  });
});
