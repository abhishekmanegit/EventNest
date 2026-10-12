import { useMemo, useState } from "react";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { fmtDate } from "../../utils/format";
import { Badge, Button, Card, EmptyState, ErrorState, Pagination, Select, Skeleton, Td, Th, useToast } from "../../components/ui";

export function useAdminQuery(urlFn, deps, { pageSize = 20 } = {}) {
  const [page, setPage] = useState(0);
  const [extra, setExtra] = useState({});
  const query = useMemo(() => ({ ...extra, page, size: pageSize }), [extra, page]);
  const r = useAsync(() => urlFn(query), [JSON.stringify(query)]);
  return { ...r, page, setPage, extra, setExtra };
}

/** Shared admin table shell: loading / error / empty / paged table. */
export function AdminTable({ loading, error, reload, data, headers, renderRow, empty, page, totalPages, setPage }) {
  if (loading) return <Skeleton className="h-72 rounded-card" />;
  if (error) return <ErrorState message={error.message} onRetry={reload} />;
  const rows = data?.content || [];
  if (rows.length === 0) return empty;
  return (
    <>
      <Card className="overflow-hidden">
        <table className="w-full">
          <thead className="border-b border-line bg-canvas">
            <tr>{headers}</tr>
          </thead>
          <tbody className="divide-y divide-line">{rows.map(renderRow)}</tbody>
        </table>
      </Card>
      <Pagination page={page} totalPages={data?.totalPages || 0} onChange={setPage} className="mt-6" />
    </>
  );
}

export function StatusFilter({ value, onChange, options }) {
  return (
    <Select value={value} onChange={(e) => onChange(e.target.value)} className="w-44">
      {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
    </Select>
  );
}