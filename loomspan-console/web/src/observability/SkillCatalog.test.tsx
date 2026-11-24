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
    { registeredName: "default:check-dns", namespace: "default", localName: "check-dns", kind: "YAML", declaration: "YAML", sourcePath: "/skills/check_dns.yaml" },
    { registeredName: "default:fetch-dns", namespace: "default", localName: "fetch-dns", kind: "JAVA", declaration: "YAML", executorName: "default:fetch-dns", beanName: "dnsSkills", method: "example.DnsSkills.fetch()", sourcePath: "/skills/fetch_dns.yaml" },
    { registeredName: "default:build-project", namespace: "default", localName: "build-project", kind: "JAVA", declaration: "SKILL_METHOD", beanName: "buildSkills", method: "public java.lang.String example.Build.build(java.lang.String,boolean)" },
  ];
  render(<SkillCatalog />);
  expect(screen.getByText("default:check-dns")).toBeInTheDocument();
  expect(screen.getByText("default:build-project")).toBeInTheDocument();
  expect(screen.getByRole("columnheader", { name: "Execution kind" })).toBeInTheDocument();
  expect(screen.getByText("YAML")).toBeInTheDocument();
  expect(screen.getAllByText("Java")).toHaveLength(2);
  expect(screen.getByText("example.Build.build()")).toBeInTheDocument();
  expect(screen.queryByText(/buildSkills:/)).toBeNull();
  expect(screen.getByText("/skills/check_dns.yaml")).toBeInTheDocument();
  expect(screen.getByText("/skills/fetch_dns.yaml")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "default:check-dns" })).toHaveAttribute(
    "href",
    "/skills/default%3Acheck-dns?targetScopeId=scope-1",
  );
  expect(screen.getByRole("region", { name: "Skill catalog table" })).toHaveAttribute("tabindex", "0");
});

test("skill catalog renders empty state", () => {
  render(<SkillCatalog />);
  expect(screen.getByText("No skills are registered.")).toBeInTheDocument();
});

test("same local names have distinct owner-qualified detail links", () => {
  view.current.skills.items = ["orders", "warehouse"].map((namespace) => ({
    registeredName: `${namespace}:lookup`, namespace, localName: "lookup",
    kind: "YAML", declaration: "YAML", sourcePath: "opaque label",
  }));
  render(<SkillCatalog />);
  for (const namespace of ["orders", "warehouse"]) {
    expect(screen.getByRole("link", { name: `${namespace}:lookup` })).toHaveAttribute(
      "href", `/skills/${namespace}%3Alookup?targetScopeId=scope-1`,
    );
  }
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
  view.current.skills.items = [{ registeredName: "default:check-dns", namespace: "default", localName: "check-dns", kind: "YAML", declaration: "YAML", sourcePath: "/skills/check_dns.yaml" }];
  view.current.skills.hasMore = true;
  view.current.skills.nextCursor = "cursor-1";
  render(<SkillCatalog />);
  expect(screen.getByText("Load more")).toBeInTheDocument();
});

test("skill catalog actions request refresh, retry, and continuation", async () => {
  view.current.skills.items = [{ registeredName: "default:check-dns", namespace: "default", localName: "check-dns", kind: "YAML", declaration: "YAML", sourcePath: "/skills/check_dns.yaml" }];
  view.current.skills.hasMore = true;
  view.current.skills.nextCursor = "cursor-1";
  view.current.skills.error = new BrowserAPIError("TARGET_UNAVAILABLE", "Target down", 503);
  render(<SkillCatalog />);
  await userEvent.click(screen.getByRole("button", { name: "Refresh" }));
  await userEvent.click(screen.getByRole("button", { name: "Retry" }));
  await userEvent.click(screen.getByRole("button", { name: "Load more" }));
  expect(view.current.loadSkills.mock.calls).toEqual([[], [], ["cursor-1"]]);
});
