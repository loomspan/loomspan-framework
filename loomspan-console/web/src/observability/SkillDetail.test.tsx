import { render, screen } from "@testing-library/react";
import { beforeEach, expect, test, vi } from "vitest";
import type { ReactNode } from "react";

const route = vi.hoisted(() => ({
  scope: "scope-1",
  navigate: vi.fn(),
}));

vi.mock("../api/client", () => ({
  getSkillDetail: vi.fn(),
  BrowserAPIError: class BrowserAPIError extends Error {
    code: string;
    status: number;
    constructor(code: string, message: string, status: number) {
      super(message);
      this.code = code;
      this.status = status;
    }
  },
}));

vi.mock("react-router", () => ({
  Link: ({ children, to }: { children: ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
  useParams: () => ({ registeredName: "CheckDns" }),
  useNavigate: () => route.navigate,
  useSearchParams: () => [new URLSearchParams({ targetScopeId: route.scope })],
}));

vi.mock("../target/TargetProvider", () => ({
  useTarget: () => ({
    target: { status: { targetScopeId: "scope-1" } },
    scopeGeneration: 0,
    refresh: vi.fn().mockResolvedValue(undefined),
  }),
}));

import { getSkillDetail } from "../api/client";
import { SkillDetailView } from "./SkillDetail";

beforeEach(() => {
  vi.mocked(getSkillDetail).mockReset();
  route.scope = "scope-1";
  route.navigate.mockReset();
});

test("stale skill deep link resets before requesting the identifier", async () => {
  route.scope = "scope-old";
  render(<SkillDetailView />);
  await vi.waitFor(() => {
    expect(route.navigate).toHaveBeenCalledWith("/", {
      replace: true,
      state: { staleTargetScope: true },
    });
  });
  expect(getSkillDetail).not.toHaveBeenCalled();
});

test("skill detail renders sourcePath as plain text and yaml inside pre element", async () => {
  const yamlWithHtml = `<script>alert("xss")</script>\n# Skill YAML\nname: CheckDns`;
  vi.mocked(getSkillDetail).mockResolvedValue({
    targetScopeId: "scope-1",
    registeredName: "CheckDns",
    source: "YAML", sourcePath: "/skills/check_dns.yaml",
    yaml: yamlWithHtml,
  });
  render(<SkillDetailView />);
  await vi.waitFor(() => {
    expect(screen.getByText("CheckDns")).toBeInTheDocument();
  });
  expect(screen.getByText("/skills/check_dns.yaml")).toBeInTheDocument();
  const pre = document.querySelector("pre.yaml-block");
  expect(pre).not.toBeNull();
  expect(pre?.textContent).toBe(yamlWithHtml);
  expect(pre?.innerHTML).not.toContain("<script>");
});

test("skill detail renders loading state", () => {
  vi.mocked(getSkillDetail).mockReturnValue(new Promise(() => {}));
  render(<SkillDetailView />);
  expect(screen.getByText("Loading skill detail…")).toBeInTheDocument();
});

test("skill detail renders error state", async () => {
  const { BrowserAPIError } = await import("../api/client");
  vi.mocked(getSkillDetail).mockRejectedValue(
    new BrowserAPIError("NOT_FOUND", "Skill not found", 404),
  );
  render(<SkillDetailView />);
  await vi.waitFor(() => {
    expect(screen.getByText("Skill not found")).toBeInTheDocument();
  });
});

test("skill detail sourcePath is not a clickable link", async () => {
  vi.mocked(getSkillDetail).mockResolvedValue({
    targetScopeId: "scope-1",
    registeredName: "CheckDns",
    source: "YAML", sourcePath: "/skills/check_dns.yaml",
    yaml: "name: CheckDns",
  });
  render(<SkillDetailView />);
  await vi.waitFor(() => {
    expect(screen.getByText("/skills/check_dns.yaml")).toBeInTheDocument();
  });
  const sourcePathElement = screen.getByText("/skills/check_dns.yaml");
  expect(sourcePathElement.tagName).not.toBe("A");
});

test("Java skill details show bean and declared method without YAML content", async () => {
  vi.mocked(getSkillDetail).mockResolvedValue({
    targetScopeId: "scope-1", registeredName: "CheckDns", source: "JAVA",
    beanName: "dnsSkills", method: "public java.lang.String example.Dns.lookup(java.lang.String)",
  });
  render(<SkillDetailView />);
  expect(await screen.findByText("dnsSkills")).toBeInTheDocument();
  expect(screen.getByText("Java")).toBeInTheDocument();
  expect(screen.getByText("example.Dns.lookup()")).toBeInTheDocument();
  expect(screen.getByText("example.Dns.lookup()").closest("div")).toHaveClass("skill-detail-method");
  expect(screen.getByText("example.Dns.lookup()").closest("dl")).toHaveClass("java-skill-detail-facts");
  expect(screen.queryByText("Skill YAML")).not.toBeInTheDocument();
  expect(screen.queryByText("Source path")).not.toBeInTheDocument();
});
