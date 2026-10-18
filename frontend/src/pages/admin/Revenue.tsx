import { useQuery } from "@tanstack/react-query";
import api from "../../lib/api";
import { rupees } from "../../lib/format";
import { StatCard } from "../../components/ui/console";
import { ErrorState, Skeleton } from "../../components/ui/primitives";

interface Revenue {
  todayGrossPaise: number;
  todayPlatformPaise: number;
  monthGrossPaise: number;
  monthPlatformPaise: number;
  ownerPayablePaise: number;
}

export default function Revenue() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["admin-revenue"],
    queryFn: async () => (await api.get("/api/v1/admin/revenue")).data.data as Revenue,
  });

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <h1 className="text-3xl font-extrabold tracking-tight">Revenue</h1>
      {isLoading && <div className="mt-4 grid gap-4 md:grid-cols-3"><Skeleton className="h-28" /><Skeleton className="h-28" /><Skeleton className="h-28" /></div>}
      {isError && <div className="mt-4"><ErrorState message="Could not load revenue." onRetry={() => refetch()} /></div>}
      {data && (
        <div className="mt-4 grid gap-4 md:grid-cols-3">
          <StatCard label="Today · gross booking value" value={rupees(data.todayGrossPaise)} />
          <StatCard label="Today · platform revenue" value={rupees(data.todayPlatformPaise)} sub="Convenience fees in window" />
          <StatCard label="Month · gross" value={rupees(data.monthGrossPaise)} />
          <StatCard label="Month · platform" value={rupees(data.monthPlatformPaise)} />
          <StatCard label="Owner payable (ledger)" value={rupees(data.ownerPayablePaise)} sub="Across all owners" />
        </div>
      )}
      <p className="mt-4 text-xs text-slate-500">
        Platform revenue = convenience fees on captured bookings in the window. Owner shares and commissions live in the immutable ledger (see Settlements).
      </p>
    </div>
  );
}
