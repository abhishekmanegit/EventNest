import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { fmtDate, fmtDateTime } from "../../utils/format";
import { Badge, Button, Card, ErrorState, EmptyState, Pagination, Select, Skeleton, Td, Th, useToast } from "../../components/ui";
import { getAuth } from "../../services/apiClient";

export default function RosterPage() {
  const { id } = useParams();
  const [status, setStatus] = useState("");
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(
    () => api.organizer.roster(id, { status, q, page, size: 25 }),
    [id, status, q, page],
  );
  const ev = useAsync(() => api.organizer.event(id), [id]);

  const mark = async (rid, result) => {
    try {
      await api.organizer.updateRoster(rid, { status: result });
      toast.success(`Marked ${result.toLowerCase()}`);
      reload();
    } catch (err) { toast.error(err.message); }
  };

  const exportCsv = async () => {
    try {
      const { accessToken } = getAuth();
      const res = await fetch(api.organizer.rosterExportUrl(id), {
        headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
      });
      if (!res.ok) throw new Error(`Export failed (${res.status})`);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `registrations-${id}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) { toast.error(err.message); }
  };

  return (
    <div className="mx-auto max-w-6xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold">Registrations</h1>
          <p className="mt-1 text-sm text-ink-soft">
            {ev.data?.summary?.title ? <Link to={`/events/${ev.data.summary.slug}`} className="text-primary hover:underline">{ev.data.summary.title}</Link> : "Roster"}
          </p>
        </div>
        <button
          onClick={exportCsv}
          className="rounded-ctl border border-line bg-surface px-4 py-2 text-sm font-semibold text-ink hover:bg-canvas"
        >
          ⬇ Export CSV
        </button>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} className="w-44">
          <option value="">All statuses</option>
          <option value="CONFIRMED">Confirmed</option>
          <option value="WAITLISTED">Waitlisted</option>
          <option value="ATTENDED">Attended</option>
          <option value="DISQUALIFIED">Disqualified</option>
          <option value="CANCELLED">Cancelled</option>
        </Select>
        <input
          value={q}
          onChange={(e) => { setQ(e.target.value); setPage(0); }}
          placeholder="Search name or email…"
          className="w-60 rounded-ctl border border-line bg-surface px-3 py-2 text-sm outline-none focus:border-primary"
        />
        <span className="tnum ml-auto text-sm text-ink-soft">{data?.totalElements || 0} total</span>
      </div>

      <Card className="mt-4 overflow-hidden">
        {loading && <Skeleton className="h-64" />}
        {error && <div className="p-6"><ErrorState message={error.message} onRetry={reload} /></div>}
        {!loading && !error && data?.content?.length === 0 && (
          <div className="p-6"><EmptyState icon="📋" title="No registrations" message="When students register, they'll appear here." /></div>
        )}
        {!loading && data?.content?.length > 0 && (
          <table className="w-full">
            <thead className="border-b border-line bg-canvas">
              <tr>
                <Th>Student</Th>
                <Th className="hidden sm:table-cell">Course · Year</Th>
                <Th>Reg number</Th>
                <Th className="hidden md:table-cell">Registered</Th>
                <Th>Status</Th>
                <Th className="text-right">Actions</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {data.content.map((r) => (
                <tr key={r.id} className="hover:bg-canvas/60">
                  <Td>
                    <p className="font-semibold">{r.studentName}</p>
                    <p className="text-xs text-ink-soft">{r.studentEmail}</p>
                  </Td>
                  <Td className="hidden sm:table-cell">
                    <span className="text-sm">{r.course || "—"}</span>
                    {r.graduationYear && <span className="tnum ml-1 text-xs text-ink-soft">({r.graduationYear})</span>}
                  </Td>
                  <Td className="tnum text-xs">{r.registrationNumber}</Td>
                  <Td className="tnum hidden text-sm text-ink-soft md:table-cell">{fmtDate(r.registeredAt)}</Td>
                  <Td><Badge tone={r.status}>{r.status}</Badge></Td>
                  <Td className="text-right">
                    {(r.status === "CONFIRMED" || r.status === "WAITLISTED") && (
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="secondary" onClick={() => mark(r.id, "ATTENDED")}>Attended</Button>
                        <Button size="sm" className="!bg-danger-soft !text-danger hover:!bg-danger/10" onClick={() => mark(r.id, "DISQUALIFIED")}>Disq</Button>
                      </div>
                    )}
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
      <Pagination page={page} totalPages={data?.totalPages || 0} onChange={setPage} className="mt-6" />
    </div>
  );
}