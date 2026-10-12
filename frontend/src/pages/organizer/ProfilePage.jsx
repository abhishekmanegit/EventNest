import { useEffect, useState } from "react";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { Button, Card, ErrorState, Field, Input, PageLoader, useToast } from "../../components/ui";
import { AlertBanner } from "../auth/shared";

export default function OrganizerProfilePage() {
  const { data, loading, error, reload } = useAsync(() => api.organizer.profile(), []);
  const [form, setForm] = useState(null);
  const [busy, setBusy] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [logoBusy, setLogoBusy] = useState(false);
  const toast = useToast();

  useEffect(() => {
    if (data && !form) {
      setForm({
        fullName: data.fullName || "",
        designation: data.designation || "",
        department: data.department || "",
        phone: data.phone || "",
      });
    }
  }, [data, form]);

  if (loading || !data) return <div className="mx-auto max-w-3xl"><PageLoader /></div>;
  if (error) return <ErrorState message={error.message} onRetry={reload} />;
  if (!form) return <div className="mx-auto max-w-3xl"><PageLoader /></div>;

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setSaveError("");
    try {
      await api.organizer.updateProfile({
        fullName: form.fullName.trim(),
        designation: form.designation.trim() || null,
        department: form.department.trim() || null,
        phone: form.phone.trim() || null,
      });
      toast.success("Profile saved");
      setForm(null);
      reload();
    } catch (err) { setSaveError(err.message); } finally { setBusy(false); }
  };

  const uploadLogo = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setLogoBusy(true);
    try {
      const res = await api.organizer.uploadLogo(file);
      toast.success("Logo updated — it's live on your events.");
      reload();
    } catch (err) { toast.error(err.message); } finally { setLogoBusy(false); e.target.value = ""; }
  };

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="text-2xl font-extrabold">College profile</h1>
      <p className="mt-1 text-sm text-ink-soft">How your college appears on events across EventNest.</p>

      <Card className="mt-6 p-6">
        <div className="flex flex-wrap items-center gap-4">
          <span className="flex h-16 w-16 items-center justify-center overflow-hidden rounded-ctl bg-primary-soft text-2xl">
            {data.collegeLogoUrl ? <img src={data.collegeLogoUrl} alt="logo" className="h-full w-full object-cover" /> : "🎓"}
          </span>
          <div className="flex-1 min-w-0">
            <p className="text-lg font-bold">{data.collegeName}</p>
            <p className="text-sm text-ink-soft">{[data.collegeCity, data.collegeState].filter(Boolean).join(", ") || "College"}</p>
          </div>
          <label className="cursor-pointer">
            <span className="inline-flex h-10 items-center gap-2 rounded-ctl border border-line bg-surface px-4 text-sm font-semibold hover:bg-canvas">
              {logoBusy ? "Uploading…" : "Change logo"}
            </span>
            <input type="file" accept="image/png,image/jpeg,image/webp" className="hidden" onChange={uploadLogo} />
          </label>
        </div>
      </Card>

      <Card className="mt-5 p-6">
        <form onSubmit={save} className="space-y-4">
          <Field label="Contact name" required>
            <Input value={form.fullName} onChange={set("fullName")} required />
          </Field>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Field label="Designation"><Input value={form.designation} onChange={set("designation")} placeholder="Faculty coordinator" /></Field>
            <Field label="Department"><Input value={form.department} onChange={set("department")} /></Field>
            <Field label="Phone"><Input value={form.phone} onChange={set("phone")} /></Field>
          </div>
          {saveError && <AlertBanner>{saveError}</AlertBanner>}
          <div className="flex justify-end">
            <Button type="submit" isLoading={busy} disabled={!form.fullName.trim()}>Save changes</Button>
          </div>
        </form>
      </Card>
    </div>
  );
}