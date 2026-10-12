import { Link } from "react-router-dom";
import { api } from "../../services/api";
import { useAsync } from "../../hooks/useAsync";
import { fmtDate } from "../../utils/format";
import { Badge, Card, ErrorState, Skeleton, StatCard } from "../../components/ui";

export default function AdminDashboardPage() {
  const { data, loading, error, reload } = useAsync(() => api.admin.stats(), []);

  if (error) return <ErrorState message={error.message} onRetry={reload} />;
  if (loading || !data) return <Skeleton className="h-64 rounded-card" />;

  const trend = data.trend?.slice(-14) || [];
  const max = Math.max(1, ...trend.map((d) => Math.max(d.events, d.registrations, d.users)));

  return (
    <div className="mx-auto max-w-6xl">
      <h1 className="text-2xl font-extrabold">Platform overview</h1>
      <p className="mt-1 text-sm text-ink-soft">Everything the platform knows, at a glance.</p>

      <div className="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Users" value={data.users} tone="primary" sub={`${data.students} students · ${data.organizers} organizers`} />
        <StatCard label="Colleges" value={data.colleges} sub={`${data.verifiedColleges} verified`} />
        <StatCard label="Events" value={data.events} tone="success" sub={`${data.publishedEvents} published`} />
        <StatCard label="Registrations" value={data.registrations} sub={`${data.confirmedRegistrations} confirmed`} />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-3">
        <QueueCard to="/admin/organizers" tone="amber" label="Organizers pending approval" value={data.organizersPendingApproval} />
        <QueueCard to="/admin/colleges" label="Colleges pending verification" value={data.collegesPendingVerification} />
        <QueueCard to="/admin/events" tone="danger" label="Events pending review" value={data.eventsPendingReview} />
      </div>

      <Card className="mt-6 p-5">
        <h2 className="mb-4 font-bold">Last 14 days</h2>
        {trend.length === 0 ? (
          <p className="text-sm text-ink-soft">No data yet.</p>
        ) : (
          <div className="flex h-40 items-end gap-1.5">
            {trend.map((d) => (
              <div key={d.day} className="group relative flex h-full flex-1 flex-col justify-end gap-1">
                <div className="flex flex-col justify-end gap-0.5">
                  <div className="w-full rounded-t-sm bg-success/80" style={{ height: `${Math.max(2, (d.registrations / max) * 100)}%` }} title={`${d.day}: ${d.registrations} registrations`} />
                  <div className="w-full rounded-t-sm bg-primary/80" style={{ height: `${Math.max(2, (d.events / max) * 100)}%` }} title={`${d.day}: ${d.events} events`} />
                </div>
                <span className="tnum hidden text-center text-[10px] text-ink-faint group-hover:block">{String(d.day).slice(5)}</span>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}

function QueueCard({ to, label, value, tone = "amber" }) {
  return (
    <Link to={to} className="rounded-card border border-line bg-surface p-4 shadow-card hover:shadow-sticky">
      <p className="text-xs font-semibold uppercase tracking-wide text-ink-soft">{label}</p>
      <p className={`tnum mt-1 text-3xl font-extrabold ${tone === "danger" ? "text-danger" : tone === "amber" ? "text-amber" : "text-primary"}`}>
        {value}
      </p>
      <p className="mt-0.5 text-xs font-semibold text-primary">Open queue →</p>
    </Link>
  );
}