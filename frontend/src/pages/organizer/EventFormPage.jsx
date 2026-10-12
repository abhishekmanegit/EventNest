import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useAsync } from "../../hooks/useAsync";
import { Button, Card, ErrorState, Field, Input, PageLoader, Select, Textarea, useToast } from "../../components/ui";
import { AlertBanner } from "../auth/shared";

const emptyForm = {
  title: "",
  summary: "",
  description: "",
  categoryId: "",
  posterUrl: "",
  mode: "OFFLINE",
  venue: "",
  city: "",
  state: "",
  startsAt: "",
  endsAt: "",
  registrationDeadline: "",
  registrationMode: "INTERNAL",
  externalRegistrationUrl: "",
  maxParticipants: "",
  teamSizeMin: "",
  teamSizeMax: "",
  prizeTitle: "",
  prizeAmount: "",
  prizeCurrency: "INR",
  prizeDescription: "",
  eligibility: "",
  rules: "",
  scheduleText: "",
  tags: "",
  coordinatorName: "",
  coordinatorEmail: "",
  coordinatorPhone: "",
};

function toLocalInput(instant) {
  if (!instant) return "";
  const d = new Date(instant);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function toInstant(local) {
  return local ? new Date(local).toISOString() : null;
}

function scheduleToText(schedule) {
  if (!schedule || typeof schedule !== "object") return "";
  return Object.entries(schedule).map(([t, l]) => `${t} § ${l}`).join("\n");
}

function textToSchedule(text) {
  const obj = {};
  text.split("\n").map((line) => line.trim()).filter(Boolean).forEach((line) => {
    const i = line.indexOf("§");
    if (i > 0) obj[line.slice(0, i).trim()] = line.slice(i + 1).trim();
  });
  return obj;
}

export default function EventFormPage() {
  const { id } = useParams();
  const isEdit = !!id;
  const navigate = useNavigate();
  const toast = useToast();
  const { user } = useAuth();
  const cats = useAsync(() => api.categories(), []);
  const detail = useAsync(() => (isEdit ? api.organizer.event(id) : Promise.resolve(null)), [id]);

  const [form, setForm] = useState(emptyForm);
  const [loaded, setLoaded] = useState(!isEdit);
  const [busy, setBusy] = useState("");
  const [saveError, setSaveError] = useState("");

  useEffect(() => {
    if (detail.data) {
      const s = detail.data.summary;
      setForm({
        title: s.title || "",
        summary: s.summary || "",
        description: detail.data.description || "",
        categoryId: s.category?.id ? String(s.category.id) : "",
        posterUrl: s.posterUrl || "",
        mode: s.mode || "OFFLINE",
        venue: s.venue || "",
        city: s.city || "",
        state: s.state || "",
        startsAt: toLocalInput(s.startsAt),
        endsAt: toLocalInput(s.endsAt),
        registrationDeadline: toLocalInput(s.registrationDeadline),
        registrationMode: detail.data.registrationMode || "INTERNAL",
        externalRegistrationUrl: detail.data.externalRegistrationUrl || "",
        maxParticipants: s.maxParticipants ? String(s.maxParticipants) : "",
        teamSizeMin: detail.data.teamSizeMin ? String(detail.data.teamSizeMin) : "",
        teamSizeMax: detail.data.teamSizeMax ? String(detail.data.teamSizeMax) : "",
        prizeTitle: s.prizeTitle || "",
        prizeAmount: s.prizeAmount ? String(s.prizeAmount) : "",
        prizeCurrency: s.prizeCurrency || "INR",
        prizeDescription: detail.data.prizeDescription || "",
        eligibility: detail.data.eligibility || "",
        rules: detail.data.rules || "",
        scheduleText: scheduleToText(detail.data.schedule),
        tags: (s.tags || []).join(", "),
        coordinatorName: detail.data.coordinatorName || "",
        coordinatorEmail: detail.data.coordinatorEmail || "",
        coordinatorPhone: detail.data.coordinatorPhone || "",
      });
      setLoaded(true);
    }
  }, [detail.data]);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const buildPayload = () => ({
    title: form.title.trim(),
    summary: form.summary.trim() || null,
    description: form.description.trim(),
    categoryId: Number(form.categoryId),
    posterUrl: form.posterUrl.trim() || null,
    mode: form.mode,
    venue: form.venue.trim() || null,
    city: form.city.trim() || null,
    state: form.state.trim() || null,
    startsAt: toInstant(form.startsAt),
    endsAt: toInstant(form.endsAt),
    registrationDeadline: toInstant(form.registrationDeadline),
    registrationMode: form.registrationMode,
    externalRegistrationUrl: form.externalRegistrationUrl.trim() || null,
    maxParticipants: form.maxParticipants ? Number(form.maxParticipants) : null,
    teamSizeMin: form.teamSizeMin ? Number(form.teamSizeMin) : null,
    teamSizeMax: form.teamSizeMax ? Number(form.teamSizeMax) : null,
    prizeTitle: form.prizeTitle.trim() || null,
    prizeAmount: form.prizeAmount ? Number(form.prizeAmount) : null,
    prizeCurrency: form.prizeCurrency.trim() || "INR",
    prizeDescription: form.prizeDescription.trim() || null,
    eligibility: form.eligibility.trim() || null,
    rules: form.rules.trim() || null,
    schedule: textToSchedule(form.scheduleText),
    tags: form.tags.split(",").map((t) => t.trim()).filter(Boolean),
    coordinatorName: form.coordinatorName.trim() || null,
    coordinatorEmail: form.coordinatorEmail.trim() || null,
    coordinatorPhone: form.coordinatorPhone.trim() || null,
  });

  const validate = () => {
    if (!form.title.trim()) return "Title is required.";
    if (!form.description.trim()) return "Description is required.";
    if (!form.categoryId) return "Pick a category.";
    if (!form.startsAt || !form.endsAt) return "Start and end times are required.";
    if (!form.registrationDeadline) return "Registration deadline is required.";
    if (new Date(form.startsAt) >= new Date(form.endsAt)) return "End time must be after start time.";
    if (new Date(form.registrationDeadline) > new Date(form.endsAt)) return "Deadline must be before the event ends.";
    if (form.registrationMode === "EXTERNAL" && !form.externalRegistrationUrl.trim()) return "External registration needs a URL.";
    return null;
  };

  const save = async (publish = false) => {
    const problem = validate();
    if (problem) { setSaveError(problem); return; }
    setBusy(publish ? "Publish" : "Save");
    setSaveError("");
    let created;
    try {
      const payload = buildPayload();
      if (isEdit) await api.organizer.updateEvent(id, payload);
      else created = await api.organizer.createEvent(payload, publish);
      toast.success(isEdit ? "Saved" : publish ? "Published" : "Draft saved");
      if (publish && created) {
        const status = created.summary.status;
        navigate(status === "PENDING_REVIEW" ? "/organizer/events" : `/events/${created.summary.slug}`, { replace: true });
      } else {
        navigate("/organizer/events", { replace: true });
      }
    } catch (err) {
      setSaveError(err.message);
    } finally { setBusy(""); }
  };

  if (detail.loading) return <PageLoader />;
  if (detail.error) return <ErrorState message={detail.error.message} onRetry={detail.reload} />;
  if (!loaded) return <PageLoader />;

  const canPublish = !!user.organizer?.canPublish;
  const canGoLive = canPublish || isEdit;

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="text-2xl font-extrabold">{isEdit ? "Edit event" : "Create event"}</h1>
      <p className="mt-1 text-sm text-ink-soft">A few good details beat a blurry poster.</p>
      {!canPublish && (
        <div className="mt-3 rounded-ctl border border-amber/30 bg-amber-soft px-3 py-2.5 text-xs text-amber">
          Your college is still pending approval — you can save drafts now and publish the moment an admin approves you.
        </div>
      )}
      <Card className="mt-6 space-y-5 p-6 sm:p-8">
        {/* basic */}
        <Field label="Title" required hint="Keep it under 200 chars — it's the headline.">
          <Input value={form.title} onChange={set("title")} required maxLength={200} placeholder="e.g. National Hackathon 2027" />
        </Field>
        <Field label="One-line summary" hint="Shown on the event card (max 300 chars).">
          <Input value={form.summary} onChange={set("summary")} maxLength={300} />
        </Field>
        <Field label="Description" required>
          <Textarea value={form.description} onChange={set("description")} placeholder="What is it, who's it for, what will people do…" />
        </Field>
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
          <Field label="Category" required>
            <Select value={form.categoryId} onChange={set("categoryId")}>
              <option value="">Select category</option>
              {(cats.data?.content || cats.data || []).map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </Select>
          </Field>
          <Field label="Mode" required>
            <Select value={form.mode} onChange={set("mode")}>
              <option value="OFFLINE">On campus</option>
              <option value="ONLINE">Online</option>
              <option value="HYBRID">Hybrid</option>
            </Select>
          </Field>
        </div>
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
          <Field label="Starts" required><Input type="datetime-local" value={form.startsAt} onChange={set("startsAt")} /></Field>
          <Field label="Ends" required><Input type="datetime-local" value={form.endsAt} onChange={set("endsAt")} /></Field>
          <Field label="Registration deadline" required><Input type="datetime-local" value={form.registrationDeadline} onChange={set("registrationDeadline")} /></Field>
        </div>
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
          <Field label="Venue"><Input value={form.venue} onChange={set("venue")} placeholder="Auditorium, Block C" /></Field>
          <Field label="City"><Input value={form.city} onChange={set("city")} /></Field>
          <Field label="State"><Input value={form.state} onChange={set("state")} /></Field>
          <Field label="Poster URL" hint="Public image URL — uploads come later."><Input value={form.posterUrl} onChange={set("posterUrl")} /></Field>
        </div>

        {/* registration */}
        <h3 className="border-t border-line pt-5 text-sm font-extrabold uppercase tracking-wide text-ink-soft">Registration</h3>
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
          <Field label="Mode" required>
            <Select value={form.registrationMode} onChange={set("registrationMode")}>
              <option value="INTERNAL">Register on EventNest</option>
              <option value="EXTERNAL">External link</option>
            </Select>
          </Field>
          {form.registrationMode === "EXTERNAL" && (
            <Field label="External URL" required>
              <Input value={form.externalRegistrationUrl} onChange={set("externalRegistrationUrl")} placeholder="https://forms.example.com/…" />
            </Field>
          )}
          <Field label="Capacity" hint="Leave empty for unlimited.">
            <Input type="number" min="1" value={form.maxParticipants} onChange={set("maxParticipants")} />
          </Field>
          <Field label="Team size" hint="Min–max if a team event.">
            <div className="flex items-center gap-2">
              <Input type="number" min="1" value={form.teamSizeMin} onChange={set("teamSizeMin")} placeholder="Min" />
              <span className="text-ink-faint">–</span>
              <Input type="number" min="1" value={form.teamSizeMax} onChange={set("teamSizeMax")} placeholder="Max" />
            </div>
          </Field>
        </div>

        {/* prize */}
        <h3 className="border-t border-line pt-5 text-sm font-extrabold uppercase tracking-wide text-ink-soft">Prize & details</h3>
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
          <Field label="Prize title"><Input value={form.prizeTitle} onChange={set("prizeTitle")} placeholder="Winner · ₹50,000" /></Field>
          <Field label="Prize amount">
            <div className="flex items-center gap-2">
              <Input type="number" value={form.prizeAmount} onChange={set("prizeAmount")} placeholder="50000" />
              <Input value={form.prizeCurrency} onChange={set("prizeCurrency")} className="w-20" maxLength={3} />
            </div>
          </Field>
          <Field label="Prize details" className="sm:col-span-2">
            <Textarea value={form.prizeDescription} onChange={set("prizeDescription")} />
          </Field>
        </div>
        <Field label="Eligibility">
          <Textarea value={form.eligibility} onChange={set("eligibility")} placeholder="Who can join — e.g. any college student, teams of 2–4." />
        </Field>
        <Field label="Rules">
          <Textarea value={form.rules} onChange={set("rules")} />
        </Field>
        <Field label="Schedule" hint="One line per item: “time § label”">
          <Textarea value={form.scheduleText} onChange={set("scheduleText")} placeholder={"09:00 § Check-in\n10:00 § Problem statement\n12:00 § Lunch"} />
        </Field>
        <Field label="Tags" hint="Comma-separated">
          <Input value={form.tags} onChange={set("tags")} placeholder="React, AI, Figma" />
        </Field>

        {/* coordinator */}
        <h3 className="border-t border-line pt-5 text-sm font-extrabold uppercase tracking-wide text-ink-soft">Coordinator (shown to participants)</h3>
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
          <Field label="Name"><Input value={form.coordinatorName} onChange={set("coordinatorName")} /></Field>
          <Field label="Email"><Input type="email" value={form.coordinatorEmail} onChange={set("coordinatorEmail")} /></Field>
          <Field label="Phone"><Input value={form.coordinatorPhone} onChange={set("coordinatorPhone")} /></Field>
        </div>

        {saveError && <AlertBanner>{saveError}</AlertBanner>}

        <div className="flex flex-wrap justify-end gap-3 border-t border-line pt-5">
          <Button type="button" variant="secondary" onClick={() => navigate(-1)}>Cancel</Button>
          <Button type="button" variant="secondary" isLoading={busy === "Save"} onClick={() => save(false)}>Save draft</Button>
          {canGoLive && (
            <Button type="button" isLoading={busy === "Publish"} onClick={() => save(true)}>
              Publish now →
            </Button>
          )}
        </div>
      </Card>
    </div>
  );
}