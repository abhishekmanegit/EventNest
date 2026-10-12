import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { Button, EmptyState, ErrorState, Skeleton, Tabs } from "../../components/ui";
import { EventRow } from "./DashboardPage";

const TABS = [
  { key: "", label: "All" },
  { key: "PUBLISHED", label: "Published" },
  { key: "DRAFT", label: "Drafts" },
  { key: "PENDING_REVIEW", label: "In review" },
  { key: "CANCELLED", label: "Cancelled" },
];

export default function OrganizerEventsPage() {
  const [params, setParams] = useSearchParams();
  const status = params.get("status") || "";
  const [tick, setTick] = useState(0);
  const { data, loading, error, reload } = useAsync(() => api.organizer.events({ status, size: 50 }), [status, tick]);

  return (
    <div className="mx-auto max-w-5xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold">My events</h1>
        <Button onClick={reload}>↻</Button>
      </div>
      <div className="mt-4"><Tabs tabs={TABS} active={status} onChange={(k) => setParams(k ? { status: k } : {})} /></div>
      <div className="mt-4 space-y-3">
        {loading && <Skeleton className="h-24 rounded-card" />}
        {error && <ErrorState message={error.message} onRetry={reload} />}
        {!loading && !error && data?.length === 0 && (
          <EmptyState icon="🗒" title="No events here" message="Create one and publish it in under two minutes." actionTo="/organizer/events/create" actionLabel="Create event" />
        )}
        {!loading && data?.map((e) => <EventRow key={e.id} event={e} onChanged={() => { setTick((t) => t + 1); reload(); }} />)}
      </div>
    </div>
  );
}