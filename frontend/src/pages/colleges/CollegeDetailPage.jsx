import { useParams } from "react-router-dom";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { ErrorState, PageLoader } from "../../components/ui";
import { EventCard, EventGridSkeleton } from "../../components/EventCard";

export default function CollegeDetailPage() {
  const { idOrSlug } = useParams();
  const { data, loading, error, reload } = useAsync(() => api.colleges.detail(idOrSlug), [idOrSlug]);
  const events = useAsync(() => data?.id ? api.colleges.events(data.id) : Promise.resolve([]), [data?.id]);

  if (error) return <div className="mx-auto max-w-5xl px-4 py-10"><ErrorState message={error.message} onRetry={reload} /></div>;
  if (loading || !data) return <div className="mx-auto max-w-5xl px-4 py-10"><PageLoader /></div>;

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <div className="rounded-card border border-line bg-surface p-6 shadow-card sm:p-8">
        <div className="flex items-center gap-4">
          <span className="flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-ctl bg-primary-soft text-3xl">
            {data.logoUrl ? <img src={data.logoUrl} alt={data.name} className="h-full w-full object-cover" /> : "🎓"}
          </span>
          <div className="min-w-0">
            <h1 className="text-2xl font-extrabold">{data.name}</h1>
            <p className="text-sm text-ink-soft">
              {[data.city, data.district, data.state].filter(Boolean).join(", ")}
              {data.verified && <span className="ml-2 font-semibold text-success">✓ Verified</span>}
            </p>
            {data.websiteUrl && (
              <a href={data.websiteUrl} target="_blank" rel="noreferrer" className="mt-1 inline-block text-sm font-semibold text-primary hover:underline">
                Visit site ↗
              </a>
            )}
          </div>
          <div className="ml-auto hidden text-right sm:block">
            <p className="tnum text-2xl font-extrabold text-primary">{data.eventCount}</p>
            <p className="text-xs text-ink-soft">events</p>
          </div>
        </div>
        {data.about && <p className="mt-5 whitespace-pre-wrap text-sm text-ink-soft">{data.about}</p>}
      </div>

      <section className="mt-8">
        <h2 className="mb-3 text-lg font-extrabold">Upcoming events</h2>
        {events.loading && <EventGridSkeleton count={3} />}
        {events.error && <ErrorState message={events.error.message} onRetry={events.reload} />}
        {!events.loading && events.data?.length === 0 && (
          <p className="rounded-card border border-dashed border-line bg-surface/50 px-6 py-12 text-center text-sm text-ink-soft">
            No events listed yet — check back soon.
          </p>
        )}
        {events.data?.length > 0 && (
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {events.data.map((e) => <EventCard key={e.id} event={e} />)}
          </div>
        )}
      </section>
    </div>
  );
}