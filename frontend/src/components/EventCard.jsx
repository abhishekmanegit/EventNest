import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { api } from "../services/api";
import { fmtDateTime, money, timeUntil } from "../utils/format";
import { Badge, Button, Skeleton, useToast } from "./ui";

const modeIcon = { OFFLINE: "📍", ONLINE: "💻", HYBRID: "🌐" };

export function EventCard({ event, onBookmarkChange }) {
  const { user, isAuthed: authed } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const left = timeUntil(event.registrationDeadline);
  const started = event.startsAt && new Date(event.startsAt).getTime() <= Date.now();
  const closed = started || event.status === "CANCELLED" || event.status === "COMPLETED" || !event.registrationOpen;

  const toggleSave = async (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (!authed()) return navigate("/login");
    const nowSaved = !event.bookmarked;
    try {
      if (event.bookmarked) { await api.events.unbookmark(event.id); event.bookmarked = false; }
      else { await api.events.bookmark(event.id); event.bookmarked = true; }
      toast.success(nowSaved ? "Saved" : "Removed");
      onBookmarkChange?.(event.id, nowSaved);
    } catch (err) { toast.error(err.message); }
  };

  return (
    <Link
      to={`/events/${event.slug}`}
      className="group flex flex-col overflow-hidden rounded-card border border-line bg-surface shadow-card transition-shadow hover:shadow-sticky"
    >
      <div className="relative aspect-[3/2] overflow-hidden bg-primary-soft">
        {event.posterUrl ? (
          <img src={event.posterUrl} alt={event.title} className="h-full w-full object-cover" loading="lazy" />
        ) : (
          <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-primary-soft to-primary/20 text-4xl opacity-80">
            {modeIcon[event.mode] || "🎟"}
          </div>
        )}
        {event.category && (
          <span className="absolute left-3 top-3 rounded-full bg-ink/80 px-2.5 py-0.5 text-xs font-semibold text-white backdrop-blur">
            {event.category.name}
          </span>
        )}
        {event.status === "CANCELLED" && <div className="absolute inset-0 flex items-center justify-center bg-black/50"><Badge tone="CANCELLED">CANCELLED</Badge></div>}
      </div>
      <div className="flex flex-1 flex-col p-4">
        <h3 className="clamp-2 text-base font-bold leading-snug group-hover:text-primary">{event.title}</h3>
        <p className="mt-1 flex items-center gap-1 text-xs text-ink-soft">
          <span>🎓</span>
          <span className="truncate">{event.college?.name}</span>
          {event.college?.state && <Badge tone="VERIFIED" className="ml-1 shrink-0">✓</Badge>}
        </p>
        <div className="tnum mt-2 space-y-1 text-sm">
          <p className="text-ink-soft">📅 {fmtDateTime(event.startsAt)}</p>
          <p className="text-ink-soft">📍 {event.city || event.mode || "TBA"}</p>
          {!closed && left && <p className="font-semibold text-amber">⏳ {left}</p>}
          {!closed && !left && <p className="font-semibold text-danger">⏳ Closes today</p>}
          {closed && <p className="font-semibold text-danger">Closed</p>}
        </div>
        {event.prizeTitle && (
          <p className="mt-2 text-sm font-semibold text-primary">
            🏆 {event.prizeTitle}
            {event.prizeAmount && <span className="tnum"> · {money(event.prizeAmount, event.prizeCurrency)}</span>}
          </p>
        )}
        <div className="mt-auto flex items-center justify-between pt-3">
          <span className="tnum text-xs text-ink-soft">{event.registrationCount} registered</span>
          <div className="flex items-center gap-1.5" onClick={(e) => e.preventDefault()}>
            <button
              onClick={toggleSave}
              aria-label={event.bookmarked ? "Unsave" : "Save"}
              className={`flex h-8 w-8 items-center justify-center rounded-ctl text-lg transition-colors ${
                event.bookmarked ? "text-amber" : "text-ink-faint hover:text-amber"
              }`}
            >
              {event.bookmarked ? "★" : "☆"}
            </button>
            {event.registered ? (
              <Badge tone="CONFIRMED">Registered</Badge>
            ) : (
              <span className="rounded-ctl bg-primary px-3 py-1.5 text-xs font-bold text-white">Register</span>
            )}
          </div>
        </div>
      </div>
    </Link>
  );
}

export function EventCardSkeleton() {
  return (
    <div className="rounded-card border border-line bg-surface p-0 shadow-card">
      <Skeleton className="aspect-[3/2] rounded-t-card bg-zinc-200" />
      <div className="space-y-2.5 p-4">
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-3 w-1/2" />
        <Skeleton className="h-3 w-full" />
        <Skeleton className="h-8 w-full" />
      </div>
    </div>
  );
}

export function EventGridSkeleton({ count = 6 }) {
  return (
    <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: count }).map((_, i) => <EventCardSkeleton key={i} />)}
    </div>
  );
}