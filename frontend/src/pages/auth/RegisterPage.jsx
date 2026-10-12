import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { ApiError } from "../../services/apiClient";
import { AuthLayout } from "../../components/layouts";
import CollegeSelect, { resolveCollegeId } from "../../components/CollegeSelect";
import { Button, Field, Input, Tabs } from "../../components/ui";
import { AlertBanner, PASSWORD_HINT, validatePassword } from "./shared";

export default function RegisterPage() {
  const [tab, setTab] = useState("student");
  return (
    <AuthLayout>
      <h1 className="text-2xl font-extrabold">Create your account</h1>
      <p className="mt-1 text-sm text-ink-soft">Join in 30 seconds — your completed registrations follow you anywhere.</p>
      <Tabs
        tabs={[
          { key: "student", label: "I'm a student" },
          { key: "organizer", label: "I organise events" },
        ]}
        active={tab}
        onChange={setTab}
      />
      <div className="mt-6">{tab === "student" ? <StudentForm /> : <OrganizerForm />}</div>
    </AuthLayout>
  );
}

function StudentForm() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: "", email: "", password: "", collegeId: null, newCollege: "", course: "", graduationYear: "", phone: "", city: "", state: "", skillsText: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    if (!validatePassword(form.password)) { setError(PASSWORD_HINT); return; }
    let collegeId;
    try {
      const resolved = await resolveCollegeId(form.collegeId, form.newCollege, { city: form.city, state: form.state });
      collegeId = resolved.collegeId;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't add your college. Try again.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await register("student", {
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        collegeId: collegeId || null,
        course: form.course.trim() || null,
        graduationYear: form.graduationYear ? Number(form.graduationYear) : null,
        phone: form.phone.trim() || null,
        city: form.city.trim() || null,
        state: form.state.trim() || null,
        skills: form.skillsText.split(",").map((s) => s.trim()).filter(Boolean),
      });
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Registration failed. Please try again.");
    } finally { setBusy(false); }
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      <Field label="Full name" required>
        <Input value={form.fullName} onChange={set("fullName")} required maxLength={120} placeholder="e.g. Ananya Iyer" />
      </Field>
      <Field label="Email" required>
        <Input type="email" value={form.email} onChange={set("email")} required placeholder="you@college.edu.in" />
      </Field>
      <Field label="Password" required hint={PASSWORD_HINT}>
        <Input type="password" value={form.password} onChange={set("password")} required />
      </Field>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="College" hint={form.collegeId === "OTHER" ? "Pick this if yours isn't listed." : "Can't find yours? Choose “Other (type it below)”."}>
          <CollegeSelect
            value={form.collegeId}
            onChange={(v) => setForm({ ...form, collegeId: v })}
            newName={form.newCollege}
            onNewName={(v) => setForm({ ...form, newCollege: v })}
            required={false}
          />
        </Field>
        <Field label="Course">
          <Input value={form.course} onChange={set("course")} placeholder="B.Tech CSE" />
        </Field>
        <Field label="Graduation year">
          <Input type="number" min="1990" max="2100" value={form.graduationYear} onChange={set("graduationYear")} />
        </Field>
        <Field label="Phone">
          <Input value={form.phone} onChange={set("phone")} placeholder="+91 98XXX XXXXX" />
        </Field>
        <Field label="City">
          <Input value={form.city} onChange={set("city")} />
        </Field>
        <Field label="State">
          <Input value={form.state} onChange={set("state")} />
        </Field>
      </div>
      <Field label="Skills" hint="Comma-separated, e.g. React, Figma, Flutter">
        <Input value={form.skillsText} onChange={set("skillsText")} />
      </Field>
      {error && <AlertBanner>{error}</AlertBanner>}
      <Button type="submit" size="lg" className="w-full" isLoading={busy}>Create student account</Button>
      <p className="text-center text-sm text-ink-soft">
        Already have an account? <Link to="/login" className="font-semibold text-primary hover:underline">Sign in</Link>
      </p>
    </form>
  );
}

function OrganizerForm() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: "", email: "", password: "", collegeId: null, newCollege: "", designation: "", department: "", phone: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    if (!validatePassword(form.password)) { setError(PASSWORD_HINT); return; }
    if (!form.collegeId) { setError("Select your college — every event is tied to one."); return; }
    let collegeId;
    try {
      const resolved = await resolveCollegeId(form.collegeId, form.newCollege);
      collegeId = resolved.collegeId;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't add your college. Try again.");
      return;
    }
    if (!collegeId) { setError("Select your college — every event is tied to one."); return; }
    setBusy(true);
    setError("");
    try {
      await register("organizer", {
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        collegeId,
        designation: form.designation.trim() || null,
        department: form.department.trim() || null,
        phone: form.phone.trim() || null,
      });
      navigate("/organizer/dashboard", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Registration failed. Please try again.");
    } finally { setBusy(false); }
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      <Field label="Full name" required>
        <Input value={form.fullName} onChange={set("fullName")} required maxLength={120} />
      </Field>
      <Field label="College email" required hint="Use the faculty/organiser email associated with your college.">
        <Input type="email" value={form.email} onChange={set("email")} required />
      </Field>
      <Field label="Password" required hint={PASSWORD_HINT}>
        <Input type="password" value={form.password} onChange={set("password")} required />
      </Field>
      <Field label="Your college" required hint={form.collegeId === "OTHER" ? "Pick this if yours isn't listed." : "Can't find yours? Choose “Other (type it below)”."}>
        <CollegeSelect
          value={form.collegeId}
          onChange={(v) => setForm({ ...form, collegeId: v })}
          newName={form.newCollege}
          onNewName={(v) => setForm({ ...form, newCollege: v })}
          required
        />
      </Field>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Designation">
          <Input value={form.designation} onChange={set("designation")} placeholder="Faculty coordinator" />
        </Field>
        <Field label="Department">
          <Input value={form.department} onChange={set("department")} />
        </Field>
        <Field label="Phone" className="sm:col-span-2">
          <Input value={form.phone} onChange={set("phone")} />
        </Field>
      </div>
      {error && <AlertBanner>{error}</AlertBanner>}
      <div className="rounded-ctl border border-amber/30 bg-amber-soft px-3 py-2.5 text-xs text-amber">
        New organizers are tagged <b>PENDING</b> until an admin approves the college account — until then you can draft events but not publish.
      </div>
      <Button type="submit" size="lg" className="w-full" isLoading={busy}>Create organizer account</Button>
      <p className="text-center text-sm text-ink-soft">
        Already have an account? <Link to="/login" className="font-semibold text-primary hover:underline">Sign in</Link>
      </p>
    </form>
  );
}