import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test } from "vitest";
import { ThemeSelect } from "./ThemeSelect";

test("changes theme by keyboard and stores it only in sessionStorage", async () => {
  const user = userEvent.setup();
  render(<ThemeSelect />);
  const select = screen.getByRole("button", { name: /Console theme/ });
  select.focus();
  await user.keyboard("{Enter}");
  await user.keyboard("{End}{Enter}");
  expect(document.documentElement).toHaveAttribute("data-theme", "dark");
  expect(sessionStorage.getItem("loomspan.console.theme")).toBe("dark");
  expect(localStorage.length).toBe(0);
});

test("falls back to system theme when stored state is absent or invalid", () => {
  sessionStorage.setItem("loomspan.console.theme", "unknown");
  render(<ThemeSelect />);
  expect(document.documentElement).not.toHaveAttribute("data-theme");
  expect(screen.getByRole("button", { name: /Console theme/ })).toHaveTextContent("Follow system");
});
