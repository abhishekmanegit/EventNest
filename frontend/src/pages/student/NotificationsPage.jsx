import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { fmtDateTime } from "../../utils/format";
import { Badge, Button, Card, EmptyState, ErrorState, Pagination, Skeleton, useToast } from "../../components/ui";
import { useAuth } from "../../context/AuthContext";

export default function NotificationsPage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const unreadOnly = params.get("unread") === "1";
  const page = Number(params.get("page") || 0);
  const toast = useToast();
  const [mutating, setMutating] = useState(false);

  const { data, loading, error, reload } = useAsync(() => api.me.notifications({ unreadOnly, page, size: 15 }), [unreadOnly, page]);

  const markAll = async () => {
    setMutating(true);
    try { await api.me.markAllRead(); toast.success("All read"); reload(); }
    catch (err) { toast.error(err.message); } finally { setMutating(false); }
  };

  const markOne = async (id) => {
    try { await api.me.markRead(id); reload(); } catch { /* ignore */ }
  };

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold">Notifications</h1>
          <p className="mt-1 text-sm text-ink-soft">Deadline reminders, confirmations and organizer updates.</p>
        </div>
        {data?.content?.some((n) => !n.read) && (
          <Button variant="secondary" size="sm" onClick={markAll} isLoading={mutating}>Mark all as read</Button>
        )}
      </div>
      <div className="mt-4 flex gap-2 text-sm">
        <button onClick={() => setParams({})} className={`rounded-ctl px-3 py-1.5 font-semibold ${!unreadOnly ? "bg-primary-soft text-primary" : "text-ink-soft hover:text-ink"}`}>All</button>
        <button onClick={() => setParams({ unread: "1" })} className={`rounded-ctl px-3 py-1.5 font-semibold ${unreadOnly ? "bg-primary-soft text-primary" : "text-ink-soft hover:text-ink"}`}>
          Unread {data && <span className="tnum">({data.content.filter((n) => !n.read).length})</span>}
        </button>
      </div>
      <div className="mt-4 space-y-2">
        {loading && <Skeleton className="h-20 rounded-card" />}
        {error && <ErrorState message={error.message} onRetry={reload} />}
        {!loading && !error && data?.content?.length === 0 && (
          <EmptyState icon="🔕" title="All caught up" message="New reminders and updates land here." />
        )}
        {!loading && data?.content?.map((n) => (
          <Card key={n.id} className={`p-4 ${n.read ? "" : "border-primary/30"}`}>
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className={`text-sm ${n.read ? "text-ink-soft" : "font-bold text-ink"}`}>
                  <Badge tone={!n.read ? "primary" : undefined} className="mr-2">{typeLabel(n.type)}</Badge>
                  {n.title}
                </p>
                {n.body && <p className="mt-1 whitespace-pre-wrap text-sm text-ink-soft">{n.body}</p>}
                <p className="tnum mt-1 text-xs text-ink-faint">{fmtDateTime(n.createdAt)}</p>
              </div>
              {!n.read && (
                <button onClick={() => markOne(n.id)} className="shrink-0 text-xs font-semibold text-primary hover:underline">Mark read</button>
              )}
            </div>
          </Card>
        ))}
      </div>
      <Pagination page={page} totalPages={data?.totalPages || 0} onChange={(p) => setParams({ ...Object.fromEntries(params), page: String(p) })} className="mt-6" />
    </div>
  );
}

export function typeLabel(t) {
  const map = {
    NEW_EVENT_MATCH: "New",
    REGISTRATION_CONFIRMED: "Confirmed",
    REGISTRATION_WAITLISTED: "Waitlisted",
    REGISTRATION_CANCELLED: "Cancelled",
    APPROVAL_DECISION: "Review",
    EVENT_STARTING: "Starting soon",
    DEADLINE_REMINDER: "Deadline",
    NEW_REGISTRATION: "Roster",
    PAYMENT_CONFIRMED: "Payment",
  };
  return map[t] || "Update";
}