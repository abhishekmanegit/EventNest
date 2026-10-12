import { useState } from "react";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { Badge, Button, Card, EmptyState, ErrorState, Field, Input, Modal, Skeleton, Td, Th, useToast } from "../../components/ui";

export default function AdminCategoriesPage() {
  const toast = useToast();
  const { data, loading, error, reload } = useAsync(() => api.admin.categories(), []);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState({ name: "", slug: "", icon: "", description: "", displayOrder: 0, active: true });
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(null);

  const openNew = () => { setForm({ name: "", slug: "", icon: "", description: "", displayOrder: (data?.length || 0) + 1, active: true }); setEditing({}); };
  const openEdit = (row) => { setForm({ name: row.name, slug: row.slug, icon: row.icon || "", description: row.description || "", displayOrder: 0, active: true }); setEditing(row); };

  const save = async (e) => {
    e.preventDefault();
    if (!form.name) return;
    setBusy(true);
    try {
      const payload = { ...form, displayOrder: Number(form.displayOrder || 0), slug: form.slug.trim() || form.name.toLowerCase().replace(/[^a-z0-9]+/g, "-") };
      if (editing.id) {
        delete payload.active;
        delete payload.displayOrder;
        await api.admin.updateCategory(editing.id, payload);
        toast.success("Category updated");
      } else {
        await api.admin.createCategory(payload);
        toast.success("Category created");
      }
      setEditing(null); reload();
    } catch (err) { toast.error(err.message); } finally { setBusy(false); }
  };

  const remove = async () => {
    setBusy(true);
    try {
      await api.admin.deleteCategory(confirmDelete.id);
      toast.success("Category deleted");
      setConfirmDelete(null); reload();
    } catch (err) { toast.error(err.message); } finally { setBusy(false); }
  };

  const list = data?.content || data || [];

  return (
    <div className="mx-auto max-w-4xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold">Categories</h1>
          <p className="mt-1 text-sm text-ink-soft">The taxonomy events attach to — used by the home grid and filters.</p>
        </div>
        <Button onClick={openNew}>＋ Add category</Button>
      </div>
      <div className="mt-4">
        {loading && <Skeleton className="h-64 rounded-card" />}
        {error && <ErrorState message={error.message} onRetry={reload} />}
        {!loading && !error && list.length === 0 && <EmptyState icon="🏷" title="No categories" message="Add the first one." />}
        {!loading && list.length > 0 && (
          <Card className="overflow-hidden">
            <table className="w-full">
              <thead className="border-b border-line bg-canvas"><tr><Th>Category</Th><Th>Slug</Th><Th>Events</Th><Th className="text-right">Actions</Th></tr></thead>
              <tbody className="divide-y divide-line">
                {list.map((row) => (
                  <tr key={row.id} className="hover:bg-canvas/60">
                    <Td>
                      <p className="font-semibold">{row.icon ? `${row.icon} ` : ""}{row.name}</p>
                      {row.description && <p className="text-xs text-ink-soft">{row.description}</p>}
                    </Td>
                    <Td className="tnum text-xs text-ink-soft">{row.slug}</Td>
                    <Td>
                      <span className="flex items-center gap-2">
                        {row.colorHex && <span className="h-3 w-3 rounded-full" style={{ background: row.colorHex }} />}
                        <span className="tnum text-sm">{row.eventCount}</span>
                      </span>
                    </Td>
                    <Td className="text-right">
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="secondary" onClick={() => openEdit(row)}>Edit</Button>
                        <Button size="sm" className="!bg-danger-soft !text-danger hover:!bg-danger/10" onClick={() => setConfirmDelete(row)}>Delete</Button>
                      </div>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
        )}
      </div>

      <Modal open={!!editing} onClose={() => setEditing(null)} title={editing?.id ? "Edit category" : "Add category"}>
        <form onSubmit={save} className="space-y-4">
          <Field label="Name" required hint="Shown on chips and filters."><Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></Field>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Slug" hint="URL — auto-derives from the name."><Input value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value })} placeholder="auto" /></Field>
            <Field label="Icon"><Input value={form.icon} onChange={(e) => setForm({ ...form, icon: e.target.value })} placeholder="💻" /></Field>
          </div>
          <Field label="Description"><Input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></Field>
          <div className="grid grid-cols-2 items-end gap-4">
            <Field label="Display order"><Input type="number" value={form.displayOrder} onChange={(e) => setForm({ ...form, displayOrder: e.target.value })} /></Field>
            <label className="flex items-center gap-2 pb-2 text-sm">
              <input type="checkbox" checked={!!form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} className="h-4 w-4 accent-primary" />
              Visible to students
            </label>
          </div>
          <div className="flex justify-end gap-3">
            <Button type="button" variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
            <Button type="submit" isLoading={busy}>Save</Button>
          </div>
        </form>
      </Modal>

      <Modal open={!!confirmDelete} onClose={() => setConfirmDelete(null)} title="Delete category?">
        <p className="mb-5 text-sm text-ink-soft">
          Deleting <b>{confirmDelete?.name}</b> is permanent. Events in this category may be left without a category.
        </p>
        <div className="flex justify-end gap-3">
          <Button variant="secondary" onClick={() => setConfirmDelete(null)}>Keep it</Button>
          <Button variant="danger" onClick={remove}>Delete permanently</Button>
        </div>
      </Modal>
    </div>
  );
}