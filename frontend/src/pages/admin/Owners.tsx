import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import api, { apiErrorMessage } from "../../lib/api";
import { Badge, Button, EmptyState, ErrorState, Skeleton, TextInput } from "../../components/ui/primitives";
import { Table } from "../../components/ui/console";
import type { Page } from "../../types";

interface Owner { id: string; businessName: string; businessEmail: string | null; businessPhone: string | null; verificationStatus: string; createdAt: string }

export default function Owners() {
  const [status, setStatus] = useState("");
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["admin-owners", status],
    queryFn: async () =>
      (await api.get(`/api/v1/admin/owners${status ? `?status=${status}` : ""}&size=30`)).data.data as Page<Owner>,
  });

  async function setVerification(id: string, next: string) {
    if (!window.confirm(`${next} this owner?`)) return;
    try {
      await api.put(`/api/v1/admin/owners/${id}/status`, { status: next });
      toast.success(`Owner ${next.toLowerCase()}.`);
      refetch();
    } catch (err) {
      toast.error(apiErrorMessage(err));
    }
  }

  async function assignPlan(id: string) {
    const planCode = window.prompt("Plan code (FREE, STARTER, PROFESSIONAL, ENTERPRISE)?", "STARTER");
    if (!planCode) return;
    try {
      await api.post(`/api/v1/admin/owners/${id}/subscription`, { planCode });
      toast.success("Plan assigned.");
    } catch (err) {
      toast.error(apiErrorMessage(err));
    }
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-extrabold tracking-tight">Parking owners</h1>
        <select value={status} onChange={(e) => setStatus(e.target.value)} className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm">
          <option value="">All statuses</option>
          <option value="PENDING">Pending</option>
          <option value="APPROVED">Approved</option>
          <option value="SUSPENDED">Suspended</option>
          <option value="REJECTED">Rejected</option>
        </select>
      </div>
      <div className="mt-4">
        {isLoading && <Skeleton className="h-40" />}
        {isError && <ErrorState message="Could not load owners." onRetry={() => refetch()} />}
        {data && data.content.length === 0 && <EmptyState title="No owners" />}
        {data && data.content.length > 0 && (
          <Table
            head={["Business", "Contact", "Status", "Actions"]}
            rows={data.content.map((o) => [
              <span key="b"><b>{o.businessName}</b><br /><span className="text-slate-500">{o.businessEmail ?? "—"}</span></span>,
              <span key="c">{o.businessPhone ?? "—"}</span>,
              <Badge key="s" status={o.verificationStatus} />,
              <span key="a" className="flex flex-wrap gap-2 text-xs font-bold">
                <button className="text-emerald-600 hover:underline" onClick={() => setVerification(o.id, "APPROVED")}>Approve</button>
                <button className="text-amber-600 hover:underline" onClick={() => setVerification(o.id, "SUSPENDED")}>Suspend</button>
                <button className="text-red-600 hover:underline" onClick={() => setVerification(o.id, "REJECTED")}>Reject</button>
                <button className="text-navy-950 hover:underline" onClick={() => assignPlan(o.id)}>Plan</button>
              </span>,
            ])}
          />
        )}
      </div>
    </div>
  );
}

export function OwnerSearchBox({ onPick }: { onPick: (id: string) => void }) {
  const [q, setQ] = useState("");
  const { data } = useQuery({
    queryKey: ["admin-owner-search", q],
    queryFn: async () => (await api.get(`/api/v1/admin/owners?size=5`)).data.data as Page<Owner>,
    enabled: q.trim().length > 1,
  });
  return (
    <div className="flex gap-2">
      <TextInput value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search owner…" />
      <Button onClick={() => data?.content[0] && onPick(data.content[0].id)}>Use first</Button>
    </div>
  );
}
