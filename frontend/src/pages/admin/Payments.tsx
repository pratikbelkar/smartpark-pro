import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import api, { apiErrorMessage } from "../../lib/api";
import { rupees, formatDateTime } from "../../lib/format";
import { Badge, Button, ErrorState, Skeleton } from "../../components/ui/primitives";
import { Section, Table } from "../../components/ui/console";
import type { Page } from "../../types";

interface AdminPayment { id: number; bookingReference: string; customerEmail: string; amountPaise: number; paymentStatus: string; paymentMethod: string | null; razorpayOrderId: string; createdAt: string }
interface Refund { id: number; bookingReference: string; amountPaise: number; status: string; razorpayRefundId: string | null; reason: string | null }

export default function Payments() {
  const payments = useQuery({
    queryKey: ["admin-payments"],
    queryFn: async () => (await api.get("/api/v1/admin/payments?size=50")).data.data as Page<AdminPayment>,
  });
  const refunds = useQuery({
    queryKey: ["admin-refunds"],
    queryFn: async () => (await api.get("/api/v1/admin/refunds")).data.data as Refund[],
  });

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <h1 className="text-3xl font-extrabold tracking-tight">Payments &amp; refunds</h1>

      <Section
        title="Refund queue"
        action={
          <Button onClick={async () => {
            try {
              const res = await api.post("/api/v1/admin/refunds/process", {});
              toast.success(`${res.data.data.processed} refunds processed.`);
              refunds.refetch(); payments.refetch();
            } catch (err) { toast.error(apiErrorMessage(err)); }
          }}>
            Process pending
          </Button>
        }
      >
        <Table
          head={["Booking", "Amount", "Status", "Gateway refund"]}
          rows={(refunds.data ?? []).map((r) => [
            <span key="b" className="font-mono font-bold">{r.bookingReference}</span>,
            <b key="a">{rupees(r.amountPaise)}</b>,
            <Badge key="s" status={r.status} />,
            <span key="g" className="font-mono text-xs">{r.razorpayRefundId ?? "—"}</span>,
          ])}
        />
      </Section>

      <Section title="Payments">
        {payments.isLoading && <Skeleton className="h-40" />}
        {payments.isError && <ErrorState message="Could not load payments." />}
        {payments.data && (
          <Table
            head={["Order", "Booking", "Customer", "Amount", "Status", "Method", "When"]}
            rows={payments.data.content.map((p) => [
              <span key="o" className="font-mono text-xs">{p.razorpayOrderId}</span>,
              <span key="b" className="font-mono font-bold">{p.bookingReference}</span>,
              <span key="c">{p.customerEmail}</span>,
              <b key="a">{rupees(p.amountPaise)}</b>,
              <Badge key="s" status={p.paymentStatus} />,
              <span key="m">{p.paymentMethod ?? "—"}</span>,
              <span key="w">{formatDateTime(p.createdAt)}</span>,
            ])}
          />
        )}
      </Section>
    </div>
  );
}
