import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { useAuth } from "../../context/AuthContext";
import { fmtDateTime, timeUntil } from "../../utils/format";
import { Badge, Card, EmptyState, ErrorState, Skeleton, StatCard, useToast } from "../../components/ui";
import { EventCard, EventGridSkeleton } from "../../components/EventCard";

export default function StudentDashboardPage() {
  const { user } = useAuth();
  const regs = useAsync(() => api.me.registrations({ size: 20 }), []);
  const saved = useAsync(() => api.me.bookmarks({ size: 8 }), []);
  const unread = useAsync(() => api.me.unreadCount(), []);
  const [startedIds, setStartedIds] = useState(new Set());

  useEffect(() => {
    const t = setInterval(() => {
      setStartedIds(new Set((regs.data?.content || []).filter((r) => r.event?.startsAt && new Date(r.event.startsAt).getTime() < Date.now()).map((r) => r.id)));
    }, 30000);
    return () => clearInterval(t);
  }, [regs.data]);

  const joined = (regs.data?.content || []).filter((r) => r.status === "CONFIRMED" || r.status === "WAITLISTED");
  const soon = joined.filter((r) => r.event?.registrationDeadline && !startedIds.has(r.id)).sort((a, b) => new Date(a.event.registrationDeadline) - new Date(b.event.registrationDeadline)).slice(0, 3);

  if (regs.loading && !regs.data) return <Skeleton className="h-40 rounded-card" />;
  if (regs.error) return <ErrorState message={regs.error.message} onRetry={regs.reload} />;

  return (
    <div className="mx-auto max-w-5xl">
      <h1 className="text-2xl font-extrabold">Hey, {user.fullName.split(" ")[0]} 👋</h1>
      <p className="mt-1 text-sm text-ink-soft">Here's your campus, at a glance.</p>

      <div className="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Going" value={regs.data?.content?.filter((r) => r.status === "CONFIRMED").length || 0} tone="primary" />
        <StatCard label="Waitlisted" value={regs.data?.content?.filter((r) => r.status === "WAITLISTED").length || 0} tone="amber" />
        <StatCard label="Saved events" value={saved.data?.content?.length || 0} />
        <StatCard label="Unread alerts" value={unread.data?.count || 0} tone={unread.data?.count > 0 ? "amber" : "ink"} />
      </div>

      {soon.length > 0 && (
        <Card className="mt-6 p-4">
          <div className="mb-2 flex items-center justify-between">
            <h2 className="font-bold">⏳ Deadlines on your radar</h2>
            <Link to="/me/registrations" className="text-sm font-semibold text-primary hover:underline">All →</Link>
          </div>
          <div className="divide-y divide-line">
            {soon.map((r) => (
              <Link key={r.id} to={`/events/${r.event.slug}`} className="flex items-center justify-between gap-3 py-2.5">
                <span className="truncate font-semibold">{r.event.title}</span>
                <span className="tnum shrink-0 text-sm font-semibold text-amber">
                  {timeUntil(r.event.registrationDeadline) || "today"}
                </span>
              </Link>
            ))}
          </div>
        </Card>
      )}

      <h2 className="mt-8 mb-3 text-lg font-extrabold">Registered</h2>
      {regs.data?.content?.length ? (
        <div className="space-y-3">
          {regs.data.content.slice(0, 5).map((r) => <RegistrationRow key={r.id} row={r} />)}
        </div>
      ) : (
        <EmptyState icon="🎟" title="No registrations yet" message="Browse events and grab a spot before the deadline." actionTo="/events" actionLabel="Explore events" />
      )}

      {saved.data?.content?.length > 0 && (
        <>
          <div className="mt-8 flex items-center justify-between">
            <h2 className="text-lg font-extrabold">Saved for later</h2>
            <Link to="/me/saved" className="text-sm font-semibold text-primary hover:underline">All →</Link>
          </div>
          <div className="mt-3 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {saved.data.content.map((e) => <EventCard key={e.id} event={e} />)}
          </div>
        </>
      )}
    </div>
  );
}

export function RegistrationRow({ row, showCancel = true }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const ev = row.event;
  const started = ev.startsAt && new Date(ev.startsAt).getTime() < Date.now();

  const cancel = async () => {
    setBusy(true);
    try {
      await api.events.cancelRegistration(ev.id);
      toast.success("Registration cancelled");
      window.location.reload();
    } catch (err) { toast.error(err.message); } finally { setBusy(false); }
  };

  return (
    <Card className="flex flex-wrap items-center justify-between gap-3 p-4">
      <div className="flex min-w-0 items-center gap-3">
        <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-ctl bg-primary-soft text-xl">
          {ev.posterUrl ? <img src={ev.posterUrl} alt="" className="h-full w-full rounded-ctl object-cover" /> : "🎟"}
        </span>
        <div className="min-w-0">
          <Link to={`/events/${ev.slug}`} className="block truncate font-bold hover:text-primary">{ev.title}</Link>
          <p className="tnum text-xs text-ink-soft">📅 {fmtDateTime(ev.startsAt)}{ev.city ? ` · ${ev.city}` : ""}</p>
          <div className="mt-1 flex gap-2">
            <Badge tone={row.status}>{row.status}</Badge>
            <span className="tnum text-xs text-ink-soft">{row.registrationNumber}</span>
          </div>
        </div>
      </div>
      <div className="flex items-center gap-2">
        {showCancel && row.status !== "CANCELLED" && !started && (
          <button onClick={cancel} disabled={busy} className="rounded-ctl border border-danger/30 px-3 py-1.5 text-xs font-semibold text-danger hover:bg-danger-soft disabled:opacity-50">
            Cancel
          </button>
        )}
        {started && row.status !== "CANCELLED" && <Badge tone="primary">Happening now</Badge>}
      </div>
    </Card>
  );
}