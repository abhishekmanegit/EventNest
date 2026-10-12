import { useState } from "react";
import { api } from "../services/api";
import { useAsync } from "../hooks/useAsync";
import { Input, Select } from "./ui";

/**
 * "Enter your own college name — it'll be available in the list next time."
 *
 * The dribble of a college that a form's dropdown does not contain used to be a
 * dead end (or a free-text field that left the college unreferenced). Instead
 * the user picks "Other…", types the name, and the form resolves it to a real
 * directory row via {@link resolveCollegeId} before submitting.
 *
 * The dropdown fetches with {@code includePending=true} so that a college added
 * this way (a PENDING row, kept out of the public directory) is already there
 * for the next typist.
 */
export default function CollegeSelect({ value, onChange, required = false, newName, onNewName }) {
  const { data, loading } = useAsync(() => api.colleges.list({ size: 200, includePending: true }), []);
  const isOther = value === "OTHER";

  return (
    <div className="space-y-2">
      <Select
        value={isOther ? "OTHER" : value || ""}
        onChange={(e) => onChange(e.target.value || null)}
      >
        <option value="">
          {loading ? "Loading colleges…" : required ? "Select your college" : "No college (optional)"}
        </option>
        {(data?.content || []).map((c) => (
          <option key={c.id} value={c.id}>{c.name}</option>
        ))}
        <option value="OTHER">📝 Other (type it below)</option>
      </Select>
      {isOther && (
        <Input
          value={newName || ""}
          onChange={(e) => onNewName?.(e.target.value)}
          placeholder="Type the name of your college / institute"
          aria-label="Your college name"
          autoFocus
        />
      )}
    </div>
  );
}

/**
 * Turns the select's value into a concrete collegeId before the form submits.
 *
 * - a real college id passes straight through,
 * - {@code OTHER} creates (or reuses) a college from the typed name and returns
 *   its id, so "will it be in the list next time" is guaranteed by the backend's
 *   idempotency, not by a suggestion.
 *
 * Throws a friendly {@link Error} when the user chose "Other…" but did not type
 * anything — the caller shows it as a form error.
 */
export async function resolveCollegeId(collegeId, newCollegeName, extra = {}) {
  if (collegeId !== "OTHER") return { collegeId };
  const name = (newCollegeName || "").trim();
  if (!name) {
    throw new Error("Enter the name of your college — it'll be added to the list.");
  }
  const created = await api.colleges.create({ name, city: extra.city || "", state: extra.state || "" });
  return { collegeId: created.id };
}