
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Nutrition from "./Nutrition";
import userEvent from "@testing-library/user-event";

describe("Nutrition component", () => {
  it("renders the nutrition heading", () => {
    render(
      <MemoryRouter>
        <Nutrition />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole("heading", { name: "Nutrition" }),
    ).toBeInTheDocument();
  });

  it("renders breakfast and dinner meals", () => {
  render(
    <MemoryRouter>
      <Nutrition />
    </MemoryRouter>,
  );

  expect(
    screen.getByRole("heading", { name: "Breakfast" }),
  ).toBeInTheDocument();

  expect(
    screen.getByRole("heading", { name: "Dinner" }),
  ).toBeInTheDocument();
});

it("switches to manual entry", async () => {
  const user = userEvent.setup();

  render(
    <MemoryRouter>
      <Nutrition />
    </MemoryRouter>,
  );

  await user.click(
    screen.getByRole("button", { name: "Manual entry" }),
  );

  expect(
    screen.getByPlaceholderText("kcal"),
  ).toBeInTheDocument();
});
});
