import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { fmtDate } from "../../utils/format";
import { Badge, Button, Card, EmptyState, ErrorState, Field, Modal, Pagination, Select, Skeleton, Td, Th, Textarea, useToast } from "../../components/ui";

export default function AdminReportsPage() {
  const [params, setParams] = useSearchParams();
  const status = params.get("status") || "";
  const page = Number(params.get("page") || 0);
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => api.admin.reports({ status, page, size: 20 }), [status, page]);
  const [acting, setActing] = useState(null);
  const [note, setNote] = useState("");
  const [takedown, setTakedown] = useState(false);
  const [busy, setBusy] = useState(false);

  const setP = (k, v) => { const n = new URLSearchParams(params); if (v) n.set(k, v); else n.delete(k); setParams(n); };

  const openAct = (row) => { setActing(row); setNote(""); setTakedown(false); };

  const decide = async (action) => {
    if (!note.trim()) return;
    setBusy(true);
    try {
      await api.admin.resolveReport(acting.id, { action, note, takedown });
      toast.success(action === "RESOLVE" ? "Marked resolved" : "Dismissed");
      setActing(null); reload();
    } catch (err) { toast.error(err.message); } finally { setBusy(false); }
  };

  return (
    <div className="mx-auto max-w-6xl">
      <h1 className="text-2xl font-extrabold">Reports</h1>
      <p className="mt-1 text-sm text-ink-soft">Flags filed by students against events and colleges.</p>
      <div className="mt-4">
        <Select value={status} onChange={(e) => setP("status", e.target.value)} className="w-44">
          <option value="">All reports</option>
          <option value="OPEN">Open</option>
          <option value="RESOLVED">Resolved</option>
          <option value="DISMISSED">Dismissed</option>
        </Select>
      </div>
      <div className="mt-4">
        {loading && <Skeleton className="h-72 rounded-card" />}
        {error && <ErrorState message={error.message} onRetry={reload} />}
        {!loading && !error && data?.content?.length === 0 && (
          <EmptyState icon="🚩" title="No reports" message="The queue is clear." />
        )}
        {!loading && data?.content?.length > 0 && (
          <>
            <Card className="overflow-hidden">
              <table className="w-full">
                <thead className="border-b border-line bg-canvas"><tr><Th>Report</Th><Th>Target</Th><Th>Reporter</Th><Th>Status</Th><Th className="hidden md:table-cell">Filed</Th><Th className="text-right">Action</Th></tr></thead>
                <tbody className="divide-y divide-line">
                  {data.content.map((row) => (
                    <tr key={row.id} className="hover:bg-canvas/60">
                      <Td>
                        <p className="font-semibold">#{row.id} · {row.reason}</p>
                        {row.details && <p className="max-w-xs truncate text-xs text-ink-soft">{row.details}</p>}
                      </Td>
                      <Td>
                        {row.eventTitle ? (
                          <span>
                            <Link to={`/events/${row.eventSlug}`} className="text-sm font-semibold text-primary hover:underline">{row.eventTitle}</Link>
                            <span className="block text-xs text-ink-soft">{row.collegeName}</span>
                          </span>
                        ) : (
                          <span className="text-sm">{row.collegeName || row.targetUserName}</span>
                        )}
                      </Td>
                      <Td className="text-sm text-ink-soft">{row.reporterName}</Td>
                      <Td><Badge tone={row.status}>{row.status}</Badge></Td>
                      <Td className="tnum hidden text-sm text-ink-soft md:table-cell">{fmtDate(row.createdAt)}</Td>
                      <Td className="text-right">
                        {row.status === "OPEN" && <Button size="sm" variant="secondary" onClick={() => openAct(row)}>Review</Button>}
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

      <Modal open={!!acting} onClose={() => setActing(null)} title={`Review report #${acting?.id || ""}`}>
        <div className="space-y-4">
          <div className="rounded-ctl bg-canvas p-3 text-sm">
            <p className="font-semibold">{acting?.reason}{acting?.eventTitle ? ` — ${acting.eventTitle}` : ""}</p>
            {acting?.details && <p className="mt-1 text-ink-soft">{acting.details}</p>}
          </div>
          <Field label="Note" required hint="Shown in the report thread / audit trail.">
            <Textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder="What did you find, and what did you do…" />
          </Field>
          {acting?.eventId && (
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={takedown} onChange={(e) => setTakedown(e.target.checked)} className="h-4 w-4 accent-danger" />
              Also take the event down (cancels it, notifies students)
            </label>
          )}
          <div className="flex justify-end gap-3 border-t border-line pt-4">
            <Button variant="dangerGhost" disabled={!note.trim()} isLoading={busy} onClick={() => decide("DISMISS")}>Dismiss</Button>
            <Button disabled={!note.trim()} isLoading={busy} onClick={() => decide("RESOLVE")}>✓ Resolve</Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}