import { useEffect, useState } from "react";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { useAuth } from "../../context/AuthContext";
import { fmtDateTime } from "../../utils/format";
import CollegeSelect, { resolveCollegeId } from "../../components/CollegeSelect";
import { Button, Card, ErrorState, Field, Input, PageLoader, useToast } from "../../components/ui";
import { AlertBanner } from "../auth/shared";

function ns(value, fallback) {
  return value === undefined || value === null || value === "" ? fallback : value;
}

export default function StudentProfilePage() {
  const { user } = useAuth();
  const { data, loading, error, reload } = useAsync(() => api.me.profile(), []);
  const toast = useToast();

  const [form, setForm] = useState(null);
  const [busy, setBusy] = useState(false);
  const [saveError, setSaveError] = useState("");

  useEffect(() => {
    if (data && user && !form) {
      setForm({
        fullName: ns(data.fullName, user.fullName),
        collegeId: data.collegeId || "",
        newCollege: "",
        course: ns(data.course, ""),
        graduationYear: data.graduationYear ? String(data.graduationYear) : "",
        phone: ns(data.phone, ""),
        city: ns(data.city, ""),
        district: ns(data.district, ""),
        state: ns(data.state, ""),
        skills: (data.skills || []).join(", "),
      });
    }
  }, [data, user, form]);

  if (loading || !data || !user) return <div className="mx-auto max-w-2xl"><PageLoader /></div>;
  if (error) return <ErrorState message={error.message} onRetry={reload} />;
  if (!form) return <div className="mx-auto max-w-2xl"><PageLoader /></div>;

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setSaveError("");
    try {
      let collegeId = null;
      try {
        const resolved = await resolveCollegeId(form.collegeId, form.newCollege, { city: form.city, state: form.state });
        collegeId = resolved.collegeId ? Number(resolved.collegeId) : null;
      } catch (err) {
        setSaveError(err.message);
        setBusy(false);
        return;
      }
      await api.me.updateProfile({
        fullName: form.fullName.trim(),
        collegeId,
        course: form.course.trim() || null,
        graduationYear: form.graduationYear ? Number(form.graduationYear) : null,
        phone: form.phone.trim() || null,
        city: form.city.trim() || null,
        district: form.district.trim() || null,
        state: form.state.trim() || null,
        skills: form.skills.split(",").map((s) => s.trim()).filter(Boolean),
      });
      toast.success("Profile saved");
      setForm(null);
      reload();
    } catch (err) {
      setSaveError(err.message);
    } finally { setBusy(false); }
  };

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-2xl font-extrabold">My profile</h1>
      <p className="mt-1 text-sm text-ink-soft">Used to personalise what you see and for organizer check-in.</p>
      <Card className="mt-6 p-6">
        <form onSubmit={save} className="space-y-4">
          <Field label="Full name" required>
            <Input value={form.fullName} onChange={set("fullName")} required />
          </Field>
          <Field label="College" hint={form.collegeId === "OTHER" ? "Pick this if yours isn't listed." : "Can't find yours? Choose “Other (type it below)”."}>
            <CollegeSelect
              value={form.collegeId}
              onChange={(v) => setForm({ ...form, collegeId: v })}
              newName={form.newCollege}
              onNewName={(v) => setForm({ ...form, newCollege: v })}
              required={false}
            />
          </Field>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Course"><Input value={form.course} onChange={set("course")} /></Field>
            <Field label="Graduation year"><Input type="number" min="1990" max="2100" value={form.graduationYear} onChange={set("graduationYear")} /></Field>
            <Field label="Phone"><Input value={form.phone} onChange={set("phone")} /></Field>
            <Field label="City"><Input value={form.city} onChange={set("city")} /></Field>
            <Field label="District"><Input value={form.district} onChange={set("district")} /></Field>
            <Field label="State"><Input value={form.state} onChange={set("state")} /></Field>
          </div>
          <Field label="Skills" hint="Comma-separated — used for recommendations">
            <Input value={form.skills} onChange={set("skills")} />
          </Field>
          {saveError && <AlertBanner>{saveError}</AlertBanner>}
          <div className="flex justify-end gap-3">
            <Button type="button" variant="secondary" onClick={() => { setForm(null); reload(); }}>Reset</Button>
            <Button type="submit" isLoading={busy}>Save changes</Button>
          </div>
        </form>
      </Card>
    </div>
  );
}