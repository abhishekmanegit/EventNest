import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { fmtDate, fmtDateTime } from "../../utils/format";
import { Badge, Button, Card, EmptyState, ErrorState, Field, Modal, Pagination, Select, Skeleton, Td, Textarea, Th, useToast } from "../../components/ui";

export default function AdminEventsPage() {
  const [params, setParams] = useSearchParams();
  const status = params.get("status") || "";
  const q = params.get("q") || "";
  const page = Number(params.get("page") || 0);
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => api.admin.events({ status, q, page, size: 20 }), [status, q, page]);
  const [acting, setActing] = useState(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  const setP = (k, v) => { const n = new URLSearchParams(params); if (v) n.set(k, v); else n.delete(k); setParams(n); };

  const act = async (action, payload = {}) => {
    setBusy(true);
    try {
      await api.admin.eventModeration(acting.id, payload);
      toast.success(action === "REMOVE" ? "Taken down — students were notified." : action === "FEATURE" || action === "UNFEATURE" ? "Discovery updated." : "Approved.");
      setActing(null); setReason(""); reload();
    } catch (err) { toast.error(err.message); } finally { setBusy(false); }
  };

  return (
    <div className="mx-auto max-w-6xl">
      <h1 className="text-2xl font-extrabold">Events</h1>
      <p className="mt-1 text-sm text-ink-soft">Moderation queue and takedowns.</p>
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Select value={status} onChange={(e) => setP("status", e.target.value)} className="w-44">
          <option value="">All statuses</option>
          <option value="PENDING_REVIEW">Pending review</option>
          <option value="PUBLISHED">Published</option>
          <option value="DRAFT">Draft</option>
          <option value="CANCELLED">Cancelled</option>
        </Select>
        <input value={q} onChange={(e) => setP("q", e.target.value)} placeholder="Search title…" className="w-60 rounded-ctl border border-line bg-surface px-3 py-2 text-sm outline-none focus:border-primary" />
        <span className="tnum ml-auto text-sm text-ink-soft">{data?.totalElements || 0} events</span>
      </div>
      <div className="mt-4">
        {loading && <Skeleton className="h-72 rounded-card" />}
        {error && <ErrorState message={error.message} onRetry={reload} />}
        {!loading && !error && data?.content?.length === 0 && <EmptyState icon="🗒" title="Nothing to review" message="No events match this filter." />}
        {!loading && data?.content?.length > 0 && (
          <>
            <Card className="overflow-hidden">
              <table className="w-full">
                <thead className="border-b border-line bg-canvas">
                  <tr><Th>Event</Th><Th>College</Th><Th className="hidden lg:table-cell">Starts</Th><Th>Status</Th><Th className="text-right">Actions</Th></tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {data.content.map((row) => (
                    <tr key={row.id} className="hover:bg-canvas/60">
                      <Td>
                        <p className="font-semibold">{row.title}</p>
                        <p className="text-xs text-ink-soft">{row.categoryName} · {row.registrationCount} regs</p>
                      </Td>
                      <Td><p className="text-sm">{row.collegeName}</p><p className="text-xs text-ink-soft">{row.organizerName}</p></Td>
                      <Td className="tnum hidden text-sm text-ink-soft lg:table-cell">{fmtDateTime(row.startsAt)}</Td>
                      <Td>
                        <div className="flex flex-col gap-1">
                          <Badge tone={row.status}>{row.status}</Badge>
                          {row.featured && <Badge tone="FEATURED">Featured</Badge>}
                        </div>
                      </Td>
                      <Td className="text-right">
                        <div className="flex justify-end gap-1.5">
                          <Button size="sm" variant="secondary" onClick={() => setActing(row)}>Manage</Button>
                        </div>
                      </Td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
            <Pagination page={page} totalPages={data?.totalPages || 0} onChange={(p) => setP("page", String(p))} className="mt-6" />
          </>
        )}
      </div>

      <Modal open={!!acting} onClose={() => { setActing(null); setReason(""); }} title={`Manage — ${acting?.title || ""}`}>
        <div className="space-y-3">
          {acting?.moderationRequired && acting?.status === "PENDING_REVIEW" && (
            <Button className="w-full" isLoading={busy} onClick={() => act("APPROVE", { action: "APPROVE" })}>✓ Approve to publish</Button>
          )}
          {acting?.moderationRequired && acting?.status === "PENDING_REVIEW" && (
            <label className="block">
              <span className="mb-1 block text-xs font-semibold text-ink-soft">Reject (why not?)</span>
              <Textarea value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Tell the organizer why…" />
              <Button variant="danger" className="mt-2 w-full" disabled={!reason.trim()} isLoading={busy} onClick={() => act("REJECT", { action: "REJECT", reason })}>✕ Reject</Button>
            </label>
          )}
          {acting?.status === "PUBLISHED" && (
            <Button variant="secondary" className="w-full" isLoading={busy} onClick={() => act(acting.featured ? "UNFEATURE" : "FEATURE", { action: acting.featured ? "UNFEATURE" : "FEATURE" })}>
              {acting.featured ? "Remove from featured" : "★ Feature on discovery"}
            </Button>
          )}
          <div className="border-t border-line pt-3">
            <label className="block">
              <span className="mb-1 block text-xs font-semibold text-ink-soft">Takedown reason (students are notified)</span>
              <Textarea value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Required to take down…" />
              <Button variant="danger" className="mt-2 w-full" disabled={!reason.trim()} isLoading={busy} onClick={() => act("REMOVE", { action: "REMOVE", reason })}>🚫 Take down event</Button>
            </label>
          </div>
        </div>
      </Modal>
    </div>
  );
}