import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { Card, EmptyState, ErrorState, Pagination, Skeleton } from "../../components/ui";

export default function CollegesPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get("q") || "";
  const page = Number(params.get("page") || 0);
  const { data, loading, error, reload } = useAsync(() => api.colleges.list({ q, page, size: 12 }), [q, page]);

  const setP = (k, v) => { const n = new URLSearchParams(params); if (v) n.set(k, v); else n.delete(k); setParams(n); };

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold">Colleges</h1>
          <p className="mt-1 text-sm text-ink-soft">Every campus hosting events on EventNest.</p>
        </div>
        <input
          value={q}
          onChange={(e) => setP("q", e.target.value)}
          placeholder="Search colleges…"
          className="w-56 rounded-ctl border border-line bg-surface px-3 py-2 text-sm outline-none focus:border-primary"
        />
      </div>

      <div className="mt-6">
        {error && <ErrorState message={error.message} onRetry={reload} />}
        {loading && <Skeleton className="h-48 rounded-card" />}
        {!loading && !error && data?.content?.length === 0 && (
          <EmptyState icon="🎓" title="No colleges found" message="Try a different search." />
        )}
        {!loading && data?.content?.length > 0 && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {data.content.map((c) => (
              <Link key={c.id} to={`/colleges/${c.slug || c.id}`}>
                <Card className="h-full p-5 transition-shadow hover:shadow-sticky">
                  <div className="flex items-center gap-3">
                    <span className="flex h-12 w-12 items-center justify-center rounded-ctl bg-primary-soft text-xl">
                      {c.logoUrl ? <img src={c.logoUrl} alt="" className="h-full w-full rounded-ctl object-cover" /> : "🎓"}
                    </span>
                    <div className="min-w-0">
                      <p className="truncate font-bold">{c.name}</p>
                      <p className="text-xs text-ink-soft">{c.city}{c.city && c.state ? ", " : ""}{c.state}</p>
                    </div>
                  </div>
                  <div className="mt-3 flex items-center justify-between text-xs text-ink-soft">
                    <span>{c.eventCount ?? 0} events</span>
                    {c.verified && <span className="font-semibold text-success">✓ Verified</span>}
                  </div>
                </Card>
              </Link>
            ))}
          </div>
        )}
      </div>
      <Pagination page={page} totalPages={data?.totalPages || 0} onChange={(p) => setP("page", String(p))} className="mt-8" />
    </div>
  );
}