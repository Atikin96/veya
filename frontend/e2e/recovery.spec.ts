
import { test, expect } from "@playwright/test";

test("user can review the adapted daily plan", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("registered", "true");
  });

  await page.goto("/");

  await expect(
    page.getByRole("heading", {
      name: "Lunch was 180 kcal over plan. We can adjust for that.",
    }),
  ).toBeVisible();

  await page.getByRole("link", {
    name: "Review the rest of the day",
  }).click();

  await expect(
    page.getByRole("heading", {
      name: "Bring your plan back into rhythm",
    }),
  ).toBeVisible();

  await expect(page.getByText("Fruit · 150 kcal")).toBeVisible();
  await expect(page.getByText("Smaller serving · 610 kcal")).toBeVisible();
  await expect(page.getByText("Brisk walking · 25 min")).toBeVisible();

  await expect(
    page.getByRole("button", { name: "Accept changes" }),
  ).toBeVisible();

  await expect(
    page.getByRole("link", { name: "Decline" }),
  ).toBeVisible();
});
