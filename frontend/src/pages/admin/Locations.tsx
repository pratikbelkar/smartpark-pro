import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import api, { apiErrorMessage } from "../../lib/api";
import { Badge, ErrorState, Skeleton } from "../../components/ui/primitives";
import { Table } from "../../components/ui/console";
import type { Page } from "../../types";

interface AdminLocation { id: string; parkingName: string; city: string; fullAddress: string; operationalStatus: string; hourlyRates: Record<string, number>; freeSlots: number | null }

export default function AdminLocations() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["admin-locations"],
    queryFn: async () => (await api.get("/api/v1/admin/parking-locations?size=50")).data.data as Page<AdminLocation>,
  });

  async function setStatus(id: string, status: string) {
    if (!window.confirm(`Set location ${status}?`)) return;
    try {
      await api.put(`/api/v1/admin/parking-locations/${id}/status`, { status });
      toast.success("Location updated.");
      refetch();
    } catch (err) {
      toast.error(apiErrorMessage(err));
    }
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <h1 className="text-3xl font-extrabold tracking-tight">Parking locations</h1>
      <div className="mt-4">
        {isLoading && <Skeleton className="h-40" />}
        {isError && <ErrorState message="Could not load locations." onRetry={() => refetch()} />}
        {data && (
          <Table
            head={["Parking", "City", "Status", "Actions"]}
            rows={data.content.map((l) => [
              <span key="p"><b>{l.parkingName}</b><br /><span className="text-slate-500">{l.fullAddress}</span></span>,
              <span key="c">{l.city}</span>,
              <Badge key="s" status={l.operationalStatus} />,
              <span key="a" className="flex gap-2 text-xs font-bold">
                {(["OPEN", "CLOSED", "HIDDEN"] as const).map((s) => (
                  <button key={s} className="text-navy-950 hover:underline" onClick={() => setStatus(l.id, s)}>{s}</button>
                ))}
              </span>,
            ])}
          />
        )}
      </div>
    </div>
  );
}
