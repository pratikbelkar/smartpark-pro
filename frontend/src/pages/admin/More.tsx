import { useQuery } from "@tanstack/react-query";
import api from "../../lib/api";
import { Button } from "../../components/ui/primitives";
import { Table } from "../../components/ui/console";
import type { Page } from "../../types";

interface Review { id: number; rating: number; comment: string | null; locationName: string; customerName: string; createdAt: string }
interface AuditEntry { id: number; actorUserId: string | null; action: string; entityType: string; entityId: string; metadata: unknown; createdAt: string }
interface Ticket { id: number; subject: string; body: string; status: string; requesterEmail: string | null; createdAt: string }

export function Reports() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <h1 className="text-3xl font-extrabold tracking-tight">Reports</h1>
      <div className="mt-4 space-y-3">
        <a href="/api/v1/admin/reports/bookings.csv" className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 font-semibold hover:shadow-sm">
          Bookings report (CSV, last 30 days) <span>↓</span>
        </a>
        <a href="/api/v1/admin/reports/bookings.csv?status=COMPLETED" className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 font-semibold hover:shadow-sm">
          Completed bookings (CSV) <span>↓</span>
        </a>
        <a href="/api/v1/admin/reports/bookings.csv?status=CANCELLED" className="flex items-center justify-between rounded-2xl border border-slate-200 bg-white p-4 font-semibold hover:shadow-sm">
          Cancelled bookings (CSV) <span>↓</span>
        </a>
      </div>
      <p className="mt-3 text-xs text-slate-500">Downloads use your signed-in session. Owner CSV lives under Owner → Bookings.</p>
    </div>
  );
}

export function Reviews() {
  const { data } = useQuery({
    queryKey: ["admin-reviews"],
    queryFn: async () => (await api.get("/api/v1/admin/reviews?size=50")).data.data as Page<Review>,
  });
  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <h1 className="text-3xl font-extrabold tracking-tight">Reviews</h1>
      <div className="mt-4">
        <Table
          head={["Rating", "Comment", "Location", "Customer"]}
          rows={(data?.content ?? []).map((r) => [
            <b key="r">★ {r.rating}</b>,
            <span key="c">{r.comment ?? "—"}</span>,
            <span key="l">{r.locationName}</span>,
            <span key="u">{r.customerName}</span>,
          ])}
        />
      </div>
    </div>
  );
}

export function Audit() {
  const { data } = useQuery({
    queryKey: ["admin-audit"],
    queryFn: async () => (await api.get("/api/v1/admin/audit-logs?size=50")).data.data as Page<AuditEntry>,
  });
  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <h1 className="text-3xl font-extrabold tracking-tight">Audit logs</h1>
      <div className="mt-4">
        <Table
          head={["Action", "Entity", "Actor", "When"]}
          rows={(data?.content ?? []).map((a) => [
            <span key="a" className="font-mono text-xs font-bold">{a.action}</span>,
            <span key="e" className="font-mono text-xs">{a.entityType}/{a.entityId}</span>,
            <span key="u" className="font-mono text-xs">{a.actorUserId ?? "system"}</span>,
            <span key="w">{a.createdAt}</span>,
          ])}
        />
      </div>
    </div>
  );
}

export function Support() {
  const { data, refetch } = useQuery({
    queryKey: ["admin-support"],
    queryFn: async () => (await api.get("/api/v1/admin/support?size=50")).data.data as Page<Ticket>,
  });
  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <h1 className="text-3xl font-extrabold tracking-tight">Support tickets</h1>
      <div className="mt-4">
        <Table
          head={["Subject", "Body", "Requester", "Status", ""]}
          rows={(data?.content ?? []).map((t) => [
            <b key="s">{t.subject}</b>,
            <span key="b" className="max-w-xs truncate">{t.body}</span>,
            <span key="r">{t.requesterEmail ?? "—"}</span>,
            <span key="t">{t.status}</span>,
            <span key="a" className="flex gap-2 text-xs font-bold">
              {(["IN_PROGRESS", "RESOLVED", "CLOSED"] as const).map((s) => (
                <button key={s} className="text-navy-950 hover:underline" onClick={async () => {
                  await api.put(`/api/v1/admin/support/${t.id}`, { status: s });
                  refetch();
                }}>{s}</button>
              ))}
            </span>,
          ])}
        />
      </div>
    </div>
  );
}

export function FeeSettings() {
  const { data, refetch } = useQuery({
    queryKey: ["admin-fees"],
    queryFn: async () => (await api.get("/api/v1/admin/settings/fees")).data.data as { convenienceFeePaise: number; commissionPercent: number },
  });
  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <h1 className="text-3xl font-extrabold tracking-tight">Platform settings</h1>
      <p className="mt-1 text-sm text-slate-500">Changes open new effective rows — confirmed bookings keep their original prices.</p>
      <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-5 text-sm">
        <p>Convenience fee: <b>{data ? `₹${(data.convenienceFeePaise / 100).toFixed(2)}` : "…"}</b></p>
        <p className="mt-1">Commission: <b>{data ? `${data.commissionPercent}%` : "…"}</b></p>
        <div className="mt-3 flex gap-2">
          <Button onClick={async () => {
            const v = window.prompt("New convenience fee in paise?", String(data?.convenienceFeePaise ?? 1000));
            if (!v) return;
            await api.put("/api/v1/admin/settings/fees", { conveniencePaise: Number(v) });
            refetch();
          }}>Change fee</Button>
          <Button onClick={async () => {
            const v = window.prompt("New commission percent?", String(data?.commissionPercent ?? 15));
            if (!v) return;
            await api.put("/api/v1/admin/settings/fees", { commissionPercent: Number(v) });
            refetch();
          }}>Change commission</Button>
        </div>
      </div>
    </div>
  );
}
