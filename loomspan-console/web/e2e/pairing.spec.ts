import { expect, test } from "./fixtures/consoleProcess";

test("pairing fragment is removed and security state is not persisted", async ({
  page,
  consoleProcess,
}) => {
  await page.goto(consoleProcess.pairingUrl);
  await expect(page.getByRole("heading", { name: "Instance Overview", exact: true })).toBeVisible();
  await expect.poll(() => page.url()).not.toContain("#/pair/");
  const storage = await page.evaluate(() => ({
    local: Object.keys(localStorage),
    session: Object.keys(sessionStorage),
  }));
  expect(storage.local).toEqual([]);
  expect(storage.session).toEqual([]);
});

test("replaying a consumed fragment does not disturb the established session", async ({
  page,
  consoleProcess,
}) => {
  await page.goto(consoleProcess.pairingUrl);
  await expect(page.getByRole("heading", { name: "Instance Overview", exact: true })).toBeVisible();
  await page.goto(consoleProcess.pairingUrl);
  await expect(page.getByRole("heading", { name: "Instance Overview", exact: true })).toBeVisible();
});
