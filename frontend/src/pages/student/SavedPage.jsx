import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { EmptyState, ErrorState, Pagination } from "../../components/ui";
import { EventCard, EventGridSkeleton } from "../../components/EventCard";

export default function SavedPage() {
  const [params, setParams] = useSearchParams();
  const page = Number(params.get("page") || 0);
  const [removedIds, setRemovedIds] = useState([]);
  const { data, loading, error, reload } = useAsync(() => api.me.bookmarks({ page, size: 12 }), [page]);

  const visible = (data?.content || []).filter((e) => !removedIds.includes(e.id));

  return (
    <div className="mx-auto max-w-5xl">
      <h1 className="text-2xl font-extrabold">Saved events</h1>
      <p className="mt-1 text-sm text-ink-soft">Starred for later — they stay until you remove them.</p>
      <div className="mt-6">
        {loading && <EventGridSkeleton />}
        {error && <ErrorState message={error.message} onRetry={reload} />}
        {!loading && !error && visible.length === 0 && (
          <EmptyState icon="☆" title="No saved events" message="Tap the star on any event card to keep it here." actionTo="/events" actionLabel="Find events" />
        )}
        {!loading && visible.length > 0 && (
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {visible.map((e) => (
              <EventCard key={e.id} event={e} onBookmarkChange={(id, saved) => { if (!saved) setRemovedIds((r) => [...r, id]); }} />
            ))}
          </div>
        )}
      </div>
      <Pagination page={page} totalPages={data?.totalPages || 0} onChange={(p) => setParams({ page: String(p) })} className="mt-8" />
    </div>
  );
}