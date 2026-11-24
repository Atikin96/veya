
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import userEvent from "@testing-library/user-event";
import Sidebar from "./Sidebar";

describe("Sidebar component", () => {
  it("renders all navigation links", () => {
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole("link", { name: "Daily dashboard" }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("link", { name: "Nutrition" }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("link", { name: "Workouts" }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("link", { name: "History" }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("link", { name: "Settings" }),
    ).toBeInTheDocument();
  });

  it("has correct path for Nutrition link", () => {
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole("link", { name: "Nutrition" }),
    ).toHaveAttribute("href", "/nutrition");
  });

  it("opens and closes the mobile menu", async () => {
    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole("button", { name: "Open menu" }),
    ).toHaveAttribute("aria-expanded", "false");

    await user.click(
      screen.getByRole("button", { name: "Open menu" }),
    );

    expect(
      screen.getByRole("button", { name: "Close menu" }),
    ).toHaveAttribute("aria-expanded", "true");

    await user.click(
      screen.getByRole("button", { name: "Close menu" }),
    );

    expect(
      screen.getByRole("button", { name: "Open menu" }),
    ).toHaveAttribute("aria-expanded", "false");
  });
});
