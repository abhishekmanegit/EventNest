import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import { useAsync, useDebounced } from "../../hooks/useAsync";
import { Chip, EmptyState, ErrorState, Pagination, Select, Tabs } from "../../components/ui";
import { EventCard, EventGridSkeleton } from "../../components/EventCard";

const SORTS = [
  { key: "upcoming", label: "Upcoming" },
  { key: "newest", label: "Newest" },
  { key: "deadline", label: "Deadline" },
  { key: "popular", label: "Popular" },
];

export default function EventsPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get("q") || "";
  const category = params.get("category") || "";
  const location = params.get("location") || "";
  const mode = params.get("mode") || "";
  const sort = params.get("sort") || "upcoming";
  const page = Math.max(0, Number(params.get("page") || 0));

  const setP = (patch) => {
    const next = new URLSearchParams(params);
    patch.forEach(([k, v]) => { if (v) next.set(k, v); else next.delete(k); });
    setParams(next);
  };

  // search box: 300ms debounce into the URL
  const [rawQ, setRawQ] = useState(q);
  const debouncedQ = useDebounced(rawQ, 300);
  useEffect(() => {
    if (debouncedQ !== q) setP([["q", debouncedQ], ["page", ""]]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedQ]);

  const query = useMemo(() => {
    const o = { sort, page, size: 12 };
    if (q) o.q = q;
    if (category) o.category = category;
    if (mode) o.mode = mode;
    if (location) {
      const [city, state] = location.split("§");
      o.city = city;
      if (state) o.state = state;
    }
    return o;
  }, [q, category, location, mode, sort, page]);

  const { data, loading, error, reload } = useAsync(() => api.events.list(query), [JSON.stringify(query)]);
  const locRes = useAsync(() => api.locations(), []);
  const catRes = useAsync(() => api.categories(), []);

  const totalPages = data?.totalPages || 0;

  return (
    <div className="mx-auto max-w-7xl px-4 py-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold">Events</h1>
        <div className="flex gap-2">
          <input
            value={rawQ}
            onChange={(e) => setRawQ(e.target.value)}
            placeholder="Search events…"
            className="w-48 rounded-ctl border border-line bg-surface px-3 py-2 text-sm outline-none focus:border-primary sm:w-64"
          />
        </div>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <Chip active={!category} onClick={() => setP([["category", ""], ["page", ""]])}>All</Chip>
        {(catRes.data?.content || []).map((c) => (
          <Chip key={c.id} active={category === c.slug} onClick={() => setP([["category", c.slug], ["page", ""]])}>
            {c.name}
          </Chip>
        ))}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Select value={location} onChange={(e) => setP([["location", e.target.value], ["page", ""]])} className="w-52">
          <option value="">📍 All locations</option>
          {(locRes.data || []).map((l) => (
            <option key={l.state + l.city} value={`${l.city}§${l.state}`}>
              {l.city}
              {l.city && l.state ? `, ` : ""}{l.state}
            </option>
          ))}
        </Select>
        <Select value={mode} onChange={(e) => setP([["mode", e.target.value], ["page", ""]])} className="w-40">
          <option value="">Any mode</option>
          <option value="OFFLINE">On campus</option>
          <option value="ONLINE">Online</option>
          <option value="HYBRID">Hybrid</option>
        </Select>
        <div className="ml-auto flex items-center gap-1">
          <Tabs tabs={SORTS.map((s) => ({ key: s.key, label: s.label }))} active={sort} onChange={(k) => setP([["sort", k], ["page", ""]])} />
        </div>
      </div>

      <div className="mt-6">
        {loading && <EventGridSkeleton />}
        {error && <ErrorState message={error.message} onRetry={reload} />}
        {!loading && !error && (!data || data.content.length === 0) && (
          <EmptyState icon="🔍" title="No events match" message="Try widening your filters or clearing the search." actionOn={reload} actionLabel="Clear filters" />
        )}
        {!loading && !error && data.content.length > 0 && (
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {data.content.map((ev) => <EventCard key={ev.id} event={ev} />)}
          </div>
        )}
      </div>
      <Pagination page={page} totalPages={totalPages} onChange={(p) => setP([["page", String(p)]])} className="mt-8" />
    </div>
  );
}