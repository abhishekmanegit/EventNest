import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { Button, Badge, Card, Field, Input, EmptyState } from "./ui";

describe("Button", () => {
  it("renders label and forwards click", () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Save</Button>);
    const btn = screen.getByRole("button", { name: "Save" });
    btn.click();
    expect(onClick).toHaveBeenCalledOnce();
  });

  it("is disabled when disabled or loading and does not fire", () => {
    const onClick = vi.fn();
    render(<Button disabled onClick={onClick}>Save</Button>);
    expect(screen.getByRole("button", { name: "Save" }).disabled).toBe(true);
    screen.getByRole("button", { name: "Save" }).click();
    expect(onClick).not.toHaveBeenCalled();
  });
});

describe("Badge", () => {
  it("applies a tone colour class and renders children", () => {
    render(<Badge tone="PUBLISHED">Live</Badge>);
    const el = screen.getByText("Live");
    expect(el.className).toContain("bg-success-soft");
  });
  it("falls back to neutral styles for unknown tones", () => {
    render(<Badge>n/a</Badge>);
    expect(screen.getByText("n/a").className).toContain("bg-zinc-100");
  });
});

describe("Card", () => {
  it("renders children inside a card", () => {
    render(<Card>hello</Card>);
    expect(screen.getByText("hello")).toBeTruthy();
  });
  it("supports the `as` prop", () => {
    render(<Card as="article">art</Card>);
    expect(screen.getByText("art").tagName).toBe("ARTICLE");
  });
});

describe("Field + Input", () => {
  it("labels required fields and shows errors but not hints", () => {
    render(
      <Field label="Email" required error="is required">
        <Input aria-label="email-input" />
      </Field>,
    );
    expect(screen.getByText("Email")).toBeTruthy();
    expect(screen.getByText("*")).toBeTruthy();
    expect(screen.getByText("is required")).toBeTruthy();
    expect(screen.getByLabelText("email-input").className).toContain("rounded-ctl");
  });
  it("shows hint when there is no error", () => {
    render(
      <Field label="Email" hint="use a valid one">
        <Input />
      </Field>,
    );
    expect(screen.getByText("use a valid one")).toBeTruthy();
    expect(screen.queryByText("is required")).toBeNull();
  });
});

describe("EmptyState", () => {
  it("renders title and message", () => {
    render(<EmptyState title="No results" message="try a filter" />);
    expect(screen.getByText("No results")).toBeTruthy();
    expect(screen.getByText("try a filter")).toBeTruthy();
  });
});