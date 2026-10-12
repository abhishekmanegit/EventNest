import { useState } from "react";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { fmtDate } from "../../utils/format";
import { Badge, Button, ErrorState, Modal, Skeleton, Td, Th, Textarea, useToast } from "../../components/ui";
import { EmptyState, Pagination } from "../../components/ui";

export default function AdminOrganizersPage() {
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => api.admin.organizers({ status, page, size: 20 }), [status, page]);
  const [deciding, setDeciding] = useState(null); // row being approved/rejected
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  const decide = async (action) => {
    setBusy(true);
    try {
      await api.admin.organizerApproval(deciding.userId, action, action === "REJECT" ? reason : "");
      toast.success(action === "APPROVE" ? `${deciding.fullName} approved — can now publish.` : "Request rejected");
      setDeciding(null); setReason("");
      reload();
    } catch (err) { toast.error(err.message); } finally { setBusy(false); }
  };

  return (
    <div className="mx-auto max-w-6xl">
      <h1 className="text-2xl font-extrabold">Organizer approvals</h1>
      <p className="mt-1 text-sm text-ink-soft">Approve trust in your organizers; they get notified either way.</p>
      <div className="mt-4 flex gap-3">
        {[["", "All"], ["PENDING", "Pending"], ["APPROVED", "Approved"], ["REJECTED", "Rejected"], ["SUSPENDED", "Suspended"]].map(([v, l]) => (
          <button key={v} onClick={() => { setStatus(v); setPage(0); }}
            className={`rounded-ctl px-3 py-1.5 text-sm font-semibold ${status === v ? "bg-primary-soft text-primary" : "text-ink-soft hover:text-ink"}`}>
            {l}
          </button>
        ))}
      </div>
      <div className="mt-4">
        {loading && <Skeleton className="h-72 rounded-card" />}
        {error && <ErrorState message={error.message} onRetry={reload} />}
        {!loading && !error && data?.content?.length === 0 && (
          <EmptyState icon="🏛" title="Queue is clear" message="No organizers match this filter." />
        )}
        {!loading && data?.content?.length > 0 && (
          <>
            <div className="overflow-hidden rounded-card border border-line bg-surface shadow-card">
              <table className="w-full">
                <thead className="border-b border-line bg-canvas">
                  <tr>
                    <Th>Organizer</Th>
                    <Th className="hidden sm:table-cell">College</Th>
                    <Th>Status</Th>
                    <Th className="hidden md:table-cell">Requested</Th>
                    <Th className="text-right">Decision</Th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {data.content.map((row) => (
                    <tr key={row.id} className="hover:bg-canvas/60">
                      <Td>
                        <p className="font-semibold">{row.fullName}</p>
                        <p className="text-xs text-ink-soft">{row.email}{row.designation ? ` · ${row.designation}` : ""}</p>
                      </Td>
                      <Td className="hidden sm:table-cell">{row.collegeName}</Td>
                      <Td>
                        <Badge tone={row.verificationStatus}>{row.verificationStatus}</Badge>
                        {row.rejectionReason && <p className="mt-1 max-w-[200px] text-xs text-danger">{row.rejectionReason}</p>}
                      </Td>
                      <Td className="tnum hidden text-sm text-ink-soft md:table-cell">{fmtDate(row.createdAt)}</Td>
                      <Td className="text-right">
                        {row.verificationStatus === "PENDING" && (
                          <div className="flex justify-end gap-2">
                            <Button size="sm" onClick={() => setDeciding(row)}>Approve</Button>
                            <Button size="sm" className="!bg-danger-soft !text-danger hover:!bg-danger/10" onClick={() => setDeciding(row)}>Reject</Button>
                          </div>
                        )}
                      </Td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} totalPages={data?.totalPages || 0} onChange={setPage} className="mt-6" />
          </>
        )}
      </div>

      <Modal open={!!deciding} onClose={() => { setDeciding(null); setReason(""); }} title={`Decision for ${deciding?.fullName || ""}`}>
        <div className="space-y-4">
          <Button variant="secondary" className="w-full" isLoading={busy && !reason} onClick={() => decide("APPROVE")}>
            ✓ Approve — they can publish immediately
          </Button>
          <div className="border-t border-line pt-4">
            <label className="mb-1 block text-sm font-semibold">Reject with a reason (required for rejection)</label>
            <Textarea value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Email isn't on the college's verified domain…" />
            <Button variant="danger" className="mt-3 w-full" disabled={!reason.trim()} isLoading={busy && !!reason} onClick={() => decide("REJECT")}>
              ✕ Reject request
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}