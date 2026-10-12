import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { useAuth } from "../../context/AuthContext";
import { fmtDateTime, money, timeUntil } from "../../utils/format";
import { Badge, Button, ErrorState, Field, Modal, PageLoader, Textarea, useToast } from "../../components/ui";
import { EventCard, EventGridSkeleton } from "../../components/EventCard";

const modeLabel = { OFFLINE: "On campus", ONLINE: "Online", HYBRID: "Hybrid" };
const modeIcon = { OFFLINE: "📍", ONLINE: "💻", HYBRID: "🌐" };

export default function EventDetailPage() {
  const { idOrSlug } = useParams();
  const { data, loading, error, reload } = useAsync(() => api.events.detail(idOrSlug), [idOrSlug]);
  const related = useAsync(() => data?.summary?.id ? api.events.related(data.summary.id) : Promise.resolve([]), [data?.summary?.id]);

  if (error) return <div className="mx-auto max-w-5xl px-4 py-10"><ErrorState message={error.message} onRetry={reload} /></div>;
  if (loading || !data) return <div className="mx-auto max-w-5xl px-4 py-10"><PageLoader /></div>;

  const ev = data.summary;
  const [fresh, setFresh] = useState(data);

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <div className="overflow-hidden rounded-card border border-line bg-surface shadow-card">
        <div className="relative aspect-[3/1] bg-primary-soft">
          {ev.bannerUrl || ev.posterUrl ? (
            <img src={(fresh.bannerUrl || fresh.posterUrl) || ev.posterUrl} alt={ev.title} className="h-full w-full object-cover" />
          ) : (
            <div className="flex h-full w-full items-center justify-center text-6xl opacity-60">{modeIcon[ev.mode]}</div>
          )}
          {fresh.summary.category && (
            <span className="absolute left-4 top-4 rounded-full bg-ink/80 px-3 py-1 text-xs font-semibold text-white backdrop-blur">{fresh.summary.category.name}</span>
          )}
        </div>
        <div className="p-6 sm:p-8">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <h1 className="text-2xl font-extrabold leading-tight sm:text-3xl">{ev.title}</h1>
            <SaveButton ev={fresh.summary} onToggle={(bookmarked) => setFresh({ ...fresh, summary: { ...fresh.summary, bookmarked } })} />
          </div>
          <p className="mt-2 flex flex-wrap items-center gap-2 text-sm text-ink-soft">
            <Link to={`/colleges/${ev.college?.slug || ev.college?.id}`} className="font-semibold text-primary hover:underline">
              🎓 {ev.college?.name}
            </Link>
            {ev.college?.city && <span>· {ev.college.city}</span>}
            {ev.organizerName && <span>· organised by {ev.organizerName}</span>}
          </p>
          {ev.summary && <p className="mt-3 text-ink-soft">{ev.summary}</p>}

          <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Meta title="Starts" value={fmtDateTime(ev.startsAt)} />
            <Meta title="Ends" value={fmtDateTime(ev.endsAt)} />
            <Meta title="Mode" value={ev.mode ? `${modeIcon[ev.mode]} ${modeLabel[ev.mode]}` : "—"} />
            <Meta title="Location" value={ev.venue || ev.city || "—"} />
          </div>

          <RegistrationPanel fresh={fresh} setFresh={setFresh} />

          <EventSections detail={fresh} />

          <ReportButton eventId={ev.id} />
        </div>
      </div>

      {related.data?.length > 0 && (
        <section className="mt-10">
          <h2 className="mb-3 text-xl font-extrabold">You might also like</h2>
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {related.data.map((e) => <EventCard key={e.id} event={e} />)}
          </div>
        </section>
      )}
    </div>
  );
}

function Meta({ title, value }) {
  return (
    <div className="rounded-ctl bg-canvas p-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-ink-soft">{title}</p>
      <p className="tnum mt-0.5 text-sm font-semibold">{value}</p>
    </div>
  );
}

function SaveButton({ ev, onToggle }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  if (!user) return null;
  const toggle = async () => {
    try {
      if (ev.bookmarked) { await api.events.unbookmark(ev.id); onToggle(false); toast.success("Removed from saved"); }
      else { await api.events.bookmark(ev.id); onToggle(true); toast.success("Saved for later"); }
    } catch (err) { toast.error(err.message); }
  };
  return (
    <Button variant={ev.bookmarked ? "secondary" : "primary"} onClick={toggle} className="shrink-0">
      {ev.bookmarked ? "★ Saved" : "☆ Save"}
    </Button>
  );
}

function RegistrationPanel({ fresh, setFresh }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const ev = fresh.summary;
  const detail = fresh;
  const myReg = fresh.myRegistration;

  const refreshDetail = async () => {
    try { setFresh(await api.events.detail(ev.slug || ev.id)); } catch { /* keep last known */ }
  };

  if (!user) {
    return (
      <div className="mt-6 flex items-center justify-between gap-4 rounded-ctl border border-primary/20 bg-primary-soft p-4">
        <p className="text-sm font-semibold">Sign in to register for this event.</p>
        <Link to="/login"><Button>Sign in</Button></Link>
      </div>
    );
  }

  if (user.role === "ORGANIZER" || user.role === "ADMIN") {
    return (
      <div className="mt-6 flex items-center gap-3 rounded-ctl bg-canvas p-4 text-sm text-ink-soft">
        <span>You're signed in as {user.role.toLowerCase()}.</span>
        {user.role === "ORGANIZER" && <Link to={`/organizer/events/${ev.id}/edit`} className="font-semibold text-primary hover:underline">Edit this event →</Link>}
        {user.role === "ADMIN" && <Link to="/admin/events" className="font-semibold text-primary hover:underline">Open moderation →</Link>}
      </div>
    );
  }

  const closed = ev.status !== "PUBLISHED" || !ev.registrationOpen || (ev.registrationDeadline && new Date(ev.registrationDeadline).getTime() < Date.now());

  const register = async () => {
    setBusy(true);
    try {
      const r = await api.events.register(ev.id);
      toast.success(`Registered! Number ${r.registrationNumber}`);
      await refreshDetail();
    } catch (err) {
      if (err.code === "EVENT_FULL") toast.error("This event is full — you've been waitlisted if a spot opens.");
      else toast.error(err.message);
      refreshDetail();
    } finally { setBusy(false); }
  };

  const cancel = async () => {
    setBusy(true);
    try {
      await api.events.cancelRegistration(ev.id);
      toast.success("Registration cancelled");
      await refreshDetail();
    } catch (err) { toast.error(err.message); } finally { setBusy(false); }
  };

  const deadline = ev.registrationDeadline;
  const left = timeUntil(deadline);

  return (
    <div className="mt-6 rounded-ctl border border-line bg-canvas p-4">
      {myReg ? (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="flex items-center gap-2 font-bold"><Badge tone={myReg.status}>{myReg.status}</Badge> You're going</p>
            <p className="tnum mt-1 text-xs text-ink-soft">Reg. {myReg.registrationNumber} · {fmtDateTime(myReg.registeredAt)}</p>
          </div>
          {myReg.status !== "CANCELLED" && (
            <Button variant="dangerGhost" isLoading={busy} onClick={cancel}>Cancel registration</Button>
          )}
        </div>
      ) : closed ? (
        <p className="flex items-center gap-2 font-semibold text-danger">
          ⌛ {ev.status === "CANCELLED" ? "This event was cancelled." : ev.registrationDeadline && new Date(ev.registrationDeadline).getTime() < Date.now()
            ? `Registration closed ${fmtDateTime(ev.registrationDeadline)}`
            : "Registration is currently closed"}
        </p>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm font-semibold text-amber">⏳ {left ? `Deadline in ${left}` : "Closes today"} · {ev.maxParticipants ? `spots: ${ev.maxParticipants - ev.registrationCount}` : "open spots"}</p>
          <Button size="lg" isLoading={busy} onClick={register}>Register now →</Button>
        </div>
      )}
    </div>
  );
}

function EventSections({ detail }) {
  const d = detail;
  const sections = [];
  if (d.summary?.prizeTitle || d.prizeDescription) sections.push({
    title: "🏆 Prizes",
    body: (
      <div>
        <p className="font-bold">{d.summary?.prizeTitle}</p>
        {d.summary?.prizeAmount && <p className="tnum mt-1 text-lg font-extrabold text-primary">{money(d.summary.prizeAmount, d.summary.prizeCurrency)}</p>}
        {d.prizeDescription && <p className="mt-2 whitespace-pre-wrap text-sm text-ink-soft">{d.prizeDescription}</p>}
      </div>
    ),
  });
  if (d.schedule && d.schedule != null && Object.keys(d.schedule).length) sections.push({
    title: "🗓 Schedule",
    body: <ScheduleTimeline schedule={d.schedule} />,
  });
  if (d.eligibility) sections.push({ title: "📋 Who can join", body: <p className="whitespace-pre-wrap text-sm text-ink-soft">{d.eligibility}</p> });
  if (d.rules) sections.push({ title: "📌 Rules", body: <p className="whitespace-pre-wrap text-sm text-ink-soft">{d.rules}</p> });

  if (sections.length === 0) return null;
  return (
    <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
      {sections.map((s) => (
        <div key={s.title} className="rounded-ctl border border-line p-4">
          <h3 className="mb-2 text-sm font-extrabold uppercase tracking-wide text-ink-soft">{s.title}</h3>
          {s.body}
        </div>
      ))}
    </div>
  );
}

function ScheduleTimeline({ schedule }) {
  const entries = [];
  Object.entries(schedule).forEach(([time, label]) => entries.push({ time, label }));
  entries.sort((a, b) => a.time.localeCompare(b.time));
  return (
    <ol className="relative space-y-3 border-l border-line pl-4">
      {entries.map((e) => (
        <li key={e.time} className="relative">
          <span className="absolute -left-[21px] top-1 h-2 w-2 rounded-full bg-primary" />
          <p className="tnum text-xs font-bold text-primary">{e.time}</p>
          <p className="text-sm text-ink">{e.label}</p>
        </li>
      ))}
    </ol>
  );
}

function ReportButton({ eventId }) {
  const { user } = useAuth();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [details, setDetails] = useState("");
  const [busy, setBusy] = useState(false);
  if (!user) return null;
  const submit = async () => {
    if (!reason) return;
    setBusy(true);
    try {
      await api.events.report(eventId, { reason, details: details || null });
      toast.success("Reported — our team reviews every flag.");
      setOpen(false); setReason(""); setDetails("");
    } catch (err) { toast.error(err.message); } finally { setBusy(false); }
  };
  return (
    <>
      <div className="mt-8 flex justify-end">
        <button onClick={() => setOpen(true)} className="text-xs text-ink-faint hover:text-danger">🚩 Report this event</button>
      </div>
      <Modal open={open} onClose={() => setOpen(false)} title="Report this event">
        <div className="space-y-4">
          <Field label="Reason" required>
            <Textarea value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Wrong dates, offensive content, spam…" />
          </Field>
          <Field label="Details" hint="Anything that helps us act faster">
            <Textarea value={details} onChange={(e) => setDetails(e.target.value)} />
          </Field>
          <Button onClick={submit} variant="danger" isLoading={busy} disabled={!reason}>Submit report</Button>
        </div>
      </Modal>
    </>
  );
}