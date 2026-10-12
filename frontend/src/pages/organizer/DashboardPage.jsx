import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { useAsync } from "../../hooks/useAsync";
import { fmtDateTime } from "../../utils/format";
import { Badge, Button, Card, EmptyState, ErrorState, Skeleton, StatCard, useToast } from "../../components/ui";
import { EventCard } from "../../components/EventCard";

export default function OrganizerDashboardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => api.organizer.dashboard(), []);

  if (error) return <ErrorState message={error.message} onRetry={reload} />;
  if (loading || !data) return <Skeleton className="h-56 rounded-card" />;

  const pending = user.organizer?.verificationStatus !== "APPROVED";
  const canPublish = user.organizer?.canPublish;

  return (
    <div className="mx-auto max-w-5xl">
      <h1 className="text-2xl font-extrabold">Organizer dashboard</h1>
      <p className="mt-1 text-sm text-ink-soft">Welcome back, {user.fullName.split(" ")[0]}.</p>

      {pending && (
        <div className="mt-4 rounded-ctl border border-amber/30 bg-amber-soft p-4 text-sm text-amber">
          {canPublish
            ? "Your college can publish events right away — happy to have you aboard."
            : "Your college account is awaiting admin approval. You can draft events now and publish once approved."}
        </div>
      )}

      <div className="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Total events" value={data.totalEvents} tone="primary" />
        <StatCard label="Published" value={data.publishedEvents} tone="success" />
        <StatCard label="Drafts" value={data.draftEvents} />
        <StatCard label="In review" value={data.pendingReviewEvents} tone="amber" />
      </div>

      <div className="mt-8 flex items-center justify-between">
        <h2 className="text-lg font-extrabold">Upcoming</h2>
        <Button onClick={() => navigate("/organizer/events/create")}>＋ New event</Button>
      </div>

      {data.upcoming?.length ? (
        <div className="mt-4 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {data.upcoming.map((e) => <EventCard key={e.id} event={e} />)}
        </div>
      ) : (
        <EmptyState icon="🗒" title="No events yet" message="Create your first event — it takes about two minutes." actionTo="/organizer/events/create" actionLabel="Create event" />
      )}
    </div>
  );
}

export function EventRow({ event, onChanged }) {
  const navigate = useNavigate();
  const toast = useToast();
  const [busy, setBusy] = useState("");
  const action = async (id, payload, label) => {
    setBusy(label);
    try {
      await api.organizer.changeStatus(id, payload);
      toast.success(label + "d");
      onChanged?.();
    } catch (err) { toast.error(err.message); } finally { setBusy(""); }
  };

  return (
    <Card className="flex flex-wrap items-center justify-between gap-3 p-4">
      <div className="flex min-w-0 items-center gap-3">
        <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-ctl bg-primary-soft text-xl">
          {event.posterUrl ? <img src={event.posterUrl} alt="" className="h-full w-full rounded-ctl object-cover" /> : "🗒"}
        </span>
        <div className="min-w-0">
          <Link to={`/organizer/events/${event.id}/edit`} className="block truncate font-bold hover:text-primary">{event.title}</Link>
          <p className="tnum text-xs text-ink-soft">📅 {fmtDateTime(event.startsAt)} · <span className="tnum">{event.registrationCount} registered</span></p>
          <div className="mt-1 flex gap-1.5">
            <Badge tone={event.status}>{event.status}</Badge>
            {event.featured && <Badge tone="FEATURED">★ Featured</Badge>}
          </div>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <Button size="sm" variant="secondary" onClick={() => navigate(`/organizer/events/${event.id}/edit`)}>Edit</Button>
        <Button size="sm" variant="secondary" onClick={() => navigate(`/organizer/events/${event.id}/registrations`)}>Roster</Button>
        {event.status === "DRAFT" && (
          <>
            <Button size="sm" isLoading={busy === "PUBLISH"} onClick={() => action(event.id, { action: "PUBLISH" }, "PUBLISH")}>Publish</Button>
            <Button size="sm" variant="dangerGhost" isLoading={busy === "cancel"} onClick={() => action(event.id, { action: "CANCEL", reason: "Removed by organizer" }, "cancel")}>Remove</Button>
          </>
        )}
        {event.status === "PENDING_REVIEW" && (
          <Button size="sm" variant="dangerGhost" isLoading={busy === "cancel"} onClick={() => action(event.id, { action: "CANCEL", reason: "Withdrawn by organizer" }, "cancel")}>Withdraw</Button>
        )}
        {event.status === "PUBLISHED" && (
          <>
            <Button size="sm" variant="secondary" onClick={() => navigate(`/events/${event.slug}`)}>View</Button>
            <Button size="sm" variant="dangerGhost" isLoading={busy === "CANCEL"} onClick={() => action(event.id, { action: "CANCEL", reason: "Cancelled by organizer" }, "CANCEL")}>Cancel</Button>
          </>
        )}
        {event.status === "COMPLETED" && <Badge tone="COMPLETED">Finished</Badge>}
      </div>
    </Card>
  );
}
