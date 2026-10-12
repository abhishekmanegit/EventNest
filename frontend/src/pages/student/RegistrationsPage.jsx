import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { EmptyState, ErrorState, Pagination, Tabs } from "../../components/ui";
import { EventGridSkeleton } from "../../components/EventCard";
import { RegistrationRow, RowSkeleton } from "./rows";

const STATUSES = [
  { key: "all", label: "All" },
  { key: "CONFIRMED", label: "Confirmed" },
  { key: "WAITLISTED", label: "Waitlisted" },
  { key: "CANCELLED", label: "Cancelled" },
];

export default function RegistrationsPage() {
  const [params, setParams] = useSearchParams();
  const status = params.get("status") || "all";
  const page = Number(params.get("page") || 0);

  const query = { page, size: 12 };
  if (status !== "all") query.status = status;

  const { data, loading, error, reload } = useAsync(() => api.me.registrations(query), [JSON.stringify(query)]);

  return (
    <div className="mx-auto max-w-4xl">
      <h1 className="text-2xl font-extrabold">My registrations</h1>
      <p className="mt-1 text-sm text-ink-soft">Everything you've signed up for, newest first.</p>
      <div className="mt-4">
        <Tabs tabs={STATUSES} active={status} onChange={(k) => setParams(k === "all" ? {} : { status: k })} />
      </div>
      <div className="mt-4 space-y-3">
        {loading && <RowSkeleton />}
        {error && <ErrorState message={error.message} onRetry={reload} />}
        {!loading && !error && data?.content?.length === 0 && (
          <EmptyState icon="🎟" title="Nothing here" message="Events you register for show up here with their deadline." actionTo="/events" actionLabel="Browse events" />
        )}
        {!loading && data?.content?.map((r) => <RegistrationRow key={r.id} row={r} />)}
      </div>
      <Pagination page={page} totalPages={data?.totalPages || 0} onChange={(p) => setParams({ ...Object.fromEntries(params), page: String(p) })} className="mt-6" />
    </div>
  );
}