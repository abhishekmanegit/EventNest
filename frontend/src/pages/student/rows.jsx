import { Skeleton } from "../../components/ui";
import { RegistrationRow as RR } from "./DashboardPage";

/** Skeleton rows for the registrations list. */
export function RowSkeleton({ count = 4 }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="flex items-center gap-3 rounded-card border border-line bg-surface p-4">
          <Skeleton className="h-12 w-12 rounded-ctl" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-3 w-1/2" />
          </div>
          <Skeleton className="h-8 w-20 rounded-ctl" />
        </div>
      ))}
    </div>
  );
}

export { RR as RegistrationRow };