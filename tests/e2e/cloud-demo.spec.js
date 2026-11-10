import { expect, test } from "@playwright/test";

const uniqueKey = () => `e2e/test-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

test("two users compete for a resource and the owner releases it", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "ResourceAcquisitionService demo" })).toBeVisible();
  await page.locator("#resource").fill(uniqueKey());
  await page.getByRole("button", { name: "Acquire lock", exact: true }).click();
  await expect(page.locator("#status")).toHaveText("LockSucceeded");
  await page.locator("#user").selectOption("bob");
  await page.getByRole("button", { name: "Acquire lock", exact: true }).click();
  await expect(page.locator("#status")).toHaveText("LockFailed");
  await page.getByRole("button", { name: "Release lock", exact: true }).click();
  await expect(page.locator("#status")).toHaveText("UnlockFailed");
  await page.locator("#user").selectOption("alice");
  await page.getByRole("button", { name: "Release lock", exact: true }).click();
  await expect(page.locator("#status")).toHaveText("UnlockSucceeded");
  await page.locator("#user").selectOption("bob");
  await page.getByRole("button", { name: "Acquire lock", exact: true }).click();
  await expect(page.locator("#status")).toHaveText("LockSucceeded");
  await page.getByRole("button", { name: "Release lock", exact: true }).click();
  await expect(page.locator("#status")).toHaveText("UnlockSucceeded");
});

test("an expired lease becomes available to another user", async ({ request }) => {
  const key = uniqueKey();
  const command = async (action, user) => {
    const query = new URLSearchParams({ action, user, key });
    const response = await request.post(`/api/command?${query}`);
    expect(response.ok()).toBeTruthy();
    return response.json();
  };
  expect((await command("lock", "alice")).result).toBe("LockSucceeded");
  await expect.poll(async () => (await command("lock", "bob")).result, { timeout: 20_000 }).toBe("LockSucceeded");
  expect((await command("unlock", "bob")).result).toBe("UnlockSucceeded");
});
