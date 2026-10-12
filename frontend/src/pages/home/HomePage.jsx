import { Link } from "react-router-dom";
import { useAsync } from "../../hooks/useAsync";
import { api } from "../../services/api";
import { useAuth } from "../../context/AuthContext";
import { ErrorState, PageLoader, Skeleton } from "../../components/ui";
import { EventCard, EventCardSkeleton } from "../../components/EventCard";

export function EventRail({ title, events, to, empty }) {
  return (
    <section className="mt-10">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-xl font-extrabold">{title}</h2>
        {to && <Link to={to} className="text-sm font-semibold text-primary hover:underline">View all →</Link>}
      </div>
      {!events || events.length === 0 ? (
        <p className="text-sm text-ink-soft">{empty || "Nothing here yet — check back soon."}</p>
      ) : (
        <div className="rail -mx-4 flex gap-4 overflow-x-auto px-4 pb-2">
          {events.slice(0, 8).map((ev) => (
            <div key={ev.id} className="w-72 shrink-0 sm:w-80">
              <EventCard event={ev} />
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

export default function HomePage() {
  const { user } = useAuth();
  const { data, loading, error, reload } = useAsync(() => api.events.home(), []);

  if (error) return <ErrorState message={error.message} onRetry={reload} />;
  if (loading) return <HomeSkeleton />;
  if (!data) return null;

  const rails = [];
  if (user?.role === "STUDENT" && data.recommended?.length) rails.push({ title: "Picked for you", events: data.recommended });
  if (data.closingSoon?.length) rails.push({ title: "⏳ Closing soon — don't miss the deadline", events: data.closingSoon });
  if (data.trending?.length) rails.push({ title: "Trending on campus", events: data.trending });
  if (data.nearYou?.length) rails.push({ title: "Near you", events: data.nearYou, empty: user?.role === "STUDENT" ? "Complete your profile's city to see events near you." : "" });
  if (data.newlyAdded?.length) rails.push({ title: "Freshly added", events: data.newlyAdded });
  Object.entries(data.byCategory || {}).forEach(([slug, list]) => {
    if (list?.length) rails.push({ title: `${cap(slug)}`, events: list, to: `/events?category=${slug}` });
  });

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <div className="rounded-card bg-primary-soft px-6 py-8 sm:px-10">
        <h1 className="text-2xl font-extrabold text-ink sm:text-3xl">
          {user ? `Hey, ${user.fullName.split(" ")[0]} 👋` : "Every campus event, one inbox."}
        </h1>
        <p className="mt-2 max-w-xl text-sm text-ink-soft sm:text-base">
          Hackathons, fests and workshops with real deadlines. Register once, get reminders, never miss out.
        </p>
        <div className="mt-5 flex gap-3">
          <Link to="/events" className="rounded-ctl bg-primary px-5 py-2.5 text-sm font-bold text-white hover:bg-primary-ink">Browse events</Link>
          <Link to="/colleges" className="rounded-ctl border border-line bg-surface px-5 py-2.5 text-sm font-semibold text-ink hover:bg-canvas">Explore colleges</Link>
        </div>
      </div>
      {rails.map((r) => <EventRail key={r.title} {...r} />)}
    </div>
  );
}

function cap(s) { return s.charAt(0).toUpperCase() + s.slice(1); }

function HomeSkeleton() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <Skeleton className="h-40 rounded-card" />
      {[1, 2, 3].map((i) => (
        <section key={i} className="mt-10">
          <Skeleton className="mb-3 h-6 w-48" />
          <div className="flex gap-4 overflow-hidden">
            {[1, 2, 3, 4].map((j) => <div key={j} className="w-72 shrink-0"><EventCardSkeleton /></div>)}
          </div>
        </section>
      ))}
    </div>
  );
}