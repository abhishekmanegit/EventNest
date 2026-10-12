import { useState } from "react";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { fmtDate } from "../../utils/format";
import { Badge, Button, Card, EmptyState, ErrorState, Input, Skeleton, Select, Td, Th, useToast } from "../../components/ui";
import { AdminTable, useAdminQuery } from "./table";

export default function AdminUsersPage() {
  const role = state("role");
  const status = state("status");
  const [q, setQ] = useState("");
  const toast = useToast();
  const r = useAdminQuery((query) => api.admin.users({ ...query, role: role.value, status: status.value, q }), [role.value, status.value, q]);

  const toggle = async (row) => {
    const next = row.status === "SUSPENDED" ? "ACTIVATE" : "SUSPEND";
    try {
      await api.admin.setUserStatus(row.id, next);
      toast.success(`${row.fullName} — ${next.toLowerCase()}d`);
      r.reload();
    } catch (err) { toast.error(err.message); }
  };

  return (
    <div className="mx-auto max-w-6xl">
      <h1 className="text-2xl font-extrabold">Users</h1>
      <p className="mt-1 text-sm text-ink-soft">Suspended users lose access everywhere, immediately.</p>
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search name or email…" className="w-64" />
        <Select value={role.value} onChange={role.set} className="w-40">
          <option value="">All roles</option>
          <option value="STUDENT">Students</option>
          <option value="ORGANIZER">Organizers</option>
          <option value="ADMIN">Admins</option>
        </Select>
        <Select value={status.value} onChange={status.set} className="w-40">
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="SUSPENDED">Suspended</option>
          <option value="REJECTED">Rejected</option>
        </Select>
      </div>
      <div className="mt-4">
        <AdminTable
          {...r}
          headers={<><Th>User</Th><Th>Role</Th><Th>Status</Th><Th className="hidden md:table-cell">Joined</Th><Th className="text-right">Action</Th></>}
          renderRow={(row) => (
            <tr key={row.id} className="hover:bg-canvas/60">
              <Td>
                <p className="font-semibold">{row.fullName}</p>
                <p className="text-xs text-ink-soft">{row.email}</p>
              </Td>
              <Td><Badge tone={row.role}>{row.role}</Badge></Td>
              <Td><Badge tone={row.status}>{row.status}</Badge></Td>
              <Td className="tnum hidden text-sm text-ink-soft md:table-cell">{fmtDate(row.createdAt)}</Td>
              <Td className="text-right">
                <Button size="sm" variant={row.status === "SUSPENDED" ? "secondary" : "dangerGhost"} onClick={() => toggle(row)}>
                  {row.status === "SUSPENDED" ? "Activate" : "Suspend"}
                </Button>
              </Td>
            </tr>
          )}
          empty={<EmptyState icon="👥" title="No users match" message="Try another filter." />}
        />
      </div>
    </div>
  );
}

function state(name) {
  const [value, setValue] = useState("");
  return { value, set: (e) => setValue(e.target.value) };
}