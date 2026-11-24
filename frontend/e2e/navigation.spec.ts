
import { test, expect } from "@playwright/test";

test("user can navigate from Dashboard to Nutrition", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("registered", "true");
  });

  await page.goto("/");

  await expect(
    page.getByRole("heading", { name: "Good afternoon, Anna" }),
  ).toBeVisible();

  await page.getByRole("link", { name: "Nutrition" }).click();

  await expect(
    page.getByRole("heading", { name: "Nutrition" }),
  ).toBeVisible();

  await page.getByRole("button", { name: "Manual entry", exact: true }).click();

  await expect(page.getByPlaceholder("kcal")).toBeVisible();
});
