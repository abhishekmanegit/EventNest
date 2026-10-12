import { useState } from "react";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { fmtDate } from "../../utils/format";
import { Badge, Button, Card, EmptyState, ErrorState, Field, Input, Modal, Pagination, Select, Skeleton, Td, Th, useToast } from "../../components/ui";

export default function AdminCollegesPage() {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => api.admin.colleges({ q, page, size: 20 }), [q, page]);
  const [editing, setEditing] = useState(null); // null | {} (new) | row
  const [form, setForm] = useState({ name: "", city: "", state: "", websiteUrl: "", moderationRequired: false, verificationStatus: "UNVERIFIED" });
  const [busy, setBusy] = useState(false);

  const openNew = () => { setForm({ name: "", city: "", state: "", websiteUrl: "", about: "", moderationRequired: false, verificationStatus: "UNVERIFIED" }); setEditing({}); };
  const openEdit = (row) => { setForm({ name: row.name, city: row.city, state: row.state, websiteUrl: row.websiteUrl || "", moderationRequired: row.moderationRequired, verificationStatus: row.verificationStatus }); setEditing(row); };

  const save = async (e) => {
    e.preventDefault();
    if (!form.name) return;
    setBusy(true);
    try {
      const payload = {
        name: form.name,
        city: form.city || null,
        state: form.state || null,
        websiteUrl: form.websiteUrl || null,
        moderationRequired: !!form.moderationRequired,
      };
      if (editing.id) { payload.verificationStatus = form.verificationStatus; await api.admin.updateCollege(editing.id, payload); toast.success("College updated"); }
      else { await api.admin.createCollege(payload); toast.success("College created"); }
      setEditing(null); reload();
    } catch (err) { toast.error(err.message); } finally { setBusy(false); }
  };

  return (
    <div className="mx-auto max-w-6xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold">Colleges</h1>
          <p className="mt-1 text-sm text-ink-soft">Verified colleges appears with a ✓ and a verified badge.</p>
        </div>
        <Button onClick={openNew}>＋ Add college</Button>
      </div>
      <div className="mt-4">
        <input value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} placeholder="Search colleges…" className="w-64 rounded-ctl border border-line bg-surface px-3 py-2 text-sm outline-none focus:border-primary" />
      </div>
      <div className="mt-4">
        {loading && <Skeleton className="h-72 rounded-card" />}
        {error && <ErrorState message={error.message} onRetry={reload} />}
        {!loading && !error && data?.content?.length === 0 && <EmptyState icon="🎓" title="No colleges" message="Add the first one." />}
        {!loading && data?.content?.length > 0 && (
          <>
            <Card className="overflow-hidden">
              <table className="w-full">
                <thead className="border-b border-line bg-canvas">
                  <tr><Th>College</Th><Th>Location</Th><Th>Verification</Th><Th>Moderation</Th><Th className="hidden md:table-cell">Events</Th><Th className="text-right">Actions</Th></tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {data.content.map((row) => (
                    <tr key={row.id} className="hover:bg-canvas/60">
                      <Td><p className="font-semibold">{row.name}</p></Td>
                      <Td className="text-sm text-ink-soft">{row.city}{row.city && row.state ? ", " : ""}{row.state}</Td>
                      <Td><Badge tone={row.verificationStatus}>{row.verificationStatus}</Badge></Td>
                      <Td>{row.moderationRequired ? <Badge tone="PENDING_REVIEW">Gate</Badge> : <span className="text-sm text-ink-soft">Direct</span>}</Td>
                      <Td className="tnum hidden text-sm md:table-cell">{row.eventCount}</Td>
                      <Td className="text-right"><Button size="sm" variant="secondary" onClick={() => openEdit(row)}>Edit</Button></Td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
            <Pagination page={page} totalPages={data?.totalPages || 0} onChange={setPage} className="mt-6" />
          </>
        )}
      </div>

      <Modal open={!!editing} onClose={() => setEditing(null)} title={editing?.id ? "Edit college" : "Add college"}>
        <form onSubmit={save} className="space-y-4">
          <Field label="Name" required><Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="City"><Input value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} /></Field>
            <Field label="State"><Input value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })} /></Field>
          </div>
          <Field label="Website"><Input value={form.websiteUrl} onChange={(e) => setForm({ ...form, websiteUrl: e.target.value })} placeholder="https://…" /></Field>
          {editing?.id && (
            <Field label="Verification status">
              <Select value={form.verificationStatus} onChange={(e) => setForm({ ...form, verificationStatus: e.target.value })}>
                <option value="UNVERIFIED">Unverified</option>
                <option value="VERIFIED">Verified</option>
                <option value="REJECTED">Rejected</option>
              </Select>
            </Field>
          )}
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={!!form.moderationRequired} onChange={(e) => setForm({ ...form, moderationRequired: e.target.checked })} className="h-4 w-4 accent-primary" />
            Require admin moderation before events publish
          </label>
          <div className="flex justify-end gap-3">
            <Button type="button" variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
            <Button type="submit" isLoading={busy}>Save</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}