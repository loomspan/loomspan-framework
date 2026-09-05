import { expect, test } from "./fixtures/consoleProcess";

test("paired refresh reuses cookie and two tabs bootstrap independently", async ({
  context,
  page,
  consoleProcess,
}) => {
  await page.goto(consoleProcess.pairingUrl);
  await expect(page.getByRole("heading", { name: "Instance Overview", exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole("heading", { name: "Instance Overview", exact: true })).toBeVisible();

  const second = await context.newPage();
  await second.goto(consoleProcess.origin);
  await expect(second.getByRole("heading", { name: "Instance Overview", exact: true })).toBeVisible();
  expect(await context.cookies()).toEqual([
    expect.objectContaining({
      name: "LOOMSPAN_console_session",
      httpOnly: true,
      sameSite: "Strict",
      secure: false,
    }),
  ]);
});
