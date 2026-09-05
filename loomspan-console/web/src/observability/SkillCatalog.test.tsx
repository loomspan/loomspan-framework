import { render, screen } from "@testing-library/react";
import { beforeEach, expect, test, vi } from "vitest";
import type { ReactNode } from "react";
import type { SkillSummary } from "../api/contracts";
import { BrowserAPIError } from "../api/client";
import userEvent from "@testing-library/user-event";

const view = vi.hoisted(() => ({
  current: undefined as unknown as {
    skills: { targetScopeId: string | null; items: SkillSummary[]; hasMore: boolean; nextCursor: string | null; loading: boolean; loaded: boolean; error?: BrowserAPIError };
    loadSkills: ReturnType<typeof vi.fn>;
  },
}));

vi.mock("./ObservabilityProvider", () => ({
  useObservability: () => view.current,
  ObservabilityProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
}));

vi.mock("react-router", () => ({
  Link: ({ children, to }: { children: ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
}));

import { SkillCatalog } from "./SkillCatalog";

beforeEach(() => {
  view.current = {
    skills: { targetScopeId: "scope-1", items: [], hasMore: false, nextCursor: null, loading: false, loaded: true },
    loadSkills: vi.fn(),
  };
});

test("skill catalog renders items in a table", () => {
  view.current.skills.items = [
    { registeredName: "CheckDns", source: "YAML", sourcePath: "/skills/check_dns.yaml" },
    { registeredName: "BuildProject", source: "JAVA", beanName: "buildSkills", method: "public java.lang.String example.Build.build(java.lang.String,boolean)" },
  ];
  render(<SkillCatalog />);
  expect(screen.getByText("CheckDns")).toBeInTheDocument();
  expect(screen.getByText("BuildProject")).toBeInTheDocument();
  expect(screen.getByRole("columnheader", { name: "Source" })).toBeInTheDocument();
  expect(screen.getByText("YAML")).toBeInTheDocument();
  expect(screen.getByText("Java")).toBeInTheDocument();
  expect(screen.getByText("example.Build.build()")).toBeInTheDocument();
  expect(screen.queryByText(/buildSkills:/)).toBeNull();
  expect(screen.getByText("/skills/check_dns.yaml")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "CheckDns" })).toHaveAttribute(
    "href",
    "/skills/CheckDns?targetScopeId=scope-1",
  );
  expect(screen.getByRole("region", { name: "Skill catalog table" })).toHaveAttribute("tabindex", "0");
});

test("skill catalog renders empty state", () => {
  render(<SkillCatalog />);
  expect(screen.getByText("No skills are registered.")).toBeInTheDocument();
});

test("skill catalog renders loading state", () => {
  view.current.skills.loading = true;
  render(<SkillCatalog />);
  expect(screen.getByText("Loading skills…")).toBeInTheDocument();
});

test("skill catalog renders error state", () => {
  view.current.skills.error = new BrowserAPIError("TARGET_UNAVAILABLE", "Target down", 503);
  render(<SkillCatalog />);
  expect(screen.getByText("Target down")).toBeInTheDocument();
});

test("skill catalog shows retry button on error", () => {
  view.current.skills.error = new BrowserAPIError("TARGET_UNAVAILABLE", "Target down", 503);
  render(<SkillCatalog />);
  expect(screen.getByText("Retry")).toBeInTheDocument();
});

test("skill catalog shows load more button when hasMore is true", () => {
  view.current.skills.items = [{ registeredName: "CheckDns", source: "YAML", sourcePath: "/skills/check_dns.yaml" }];
  view.current.skills.hasMore = true;
  view.current.skills.nextCursor = "cursor-1";
  render(<SkillCatalog />);
  expect(screen.getByText("Load more")).toBeInTheDocument();
});

test("skill catalog actions request refresh, retry, and continuation", async () => {
  view.current.skills.items = [{ registeredName: "CheckDns", source: "YAML", sourcePath: "/skills/check_dns.yaml" }];
  view.current.skills.hasMore = true;
  view.current.skills.nextCursor = "cursor-1";
  view.current.skills.error = new BrowserAPIError("TARGET_UNAVAILABLE", "Target down", 503);
  render(<SkillCatalog />);
  await userEvent.click(screen.getByRole("button", { name: "Refresh" }));
  await userEvent.click(screen.getByRole("button", { name: "Retry" }));
  await userEvent.click(screen.getByRole("button", { name: "Load more" }));
  expect(view.current.loadSkills.mock.calls).toEqual([[], [], ["cursor-1"]]);
});
