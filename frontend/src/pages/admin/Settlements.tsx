import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import api, { apiErrorMessage } from "../../lib/api";
import { rupees } from "../../lib/format";
import { Badge, Button, Field, TextInput } from "../../components/ui/primitives";
import { Table } from "../../components/ui/console";

interface Settlement { id: number; ownerId: string; businessName: string; settlementReference: string; amountPaise: number; status: string; settledAt: string | null; createdAt: string }

export default function Settlements() {
  const { data, refetch } = useQuery({
    queryKey: ["admin-settlements"],
    queryFn: async () => (await api.get("/api/v1/admin/settlements")).data.data as Settlement[],
  });
  const [ownerId, setOwnerId] = useState("");
  const [amount, setAmount] = useState("");

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <h1 className="text-3xl font-extrabold tracking-tight">Owner settlements</h1>
      <p className="mt-1 text-sm text-slate-500">
        Payouts are manual bank transfers recorded here (Razorpay Route only if the account is eligible).
      </p>
      <div className="mt-4 flex flex-wrap items-end gap-2">
        <Field label="Owner UUID"><TextInput value={ownerId} onChange={(e) => setOwnerId(e.target.value)} placeholder="Owner row id" /></Field>
        <Field label="Amount (paise)"><TextInput type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="e.g. 10500" /></Field>
        <Button onClick={async () => {
          try {
            await api.post("/api/v1/admin/settlements", { ownerId, amountPaise: Number(amount) });
            toast.success("Settlement created and ledger debited.");
            setOwnerId(""); setAmount("");
            refetch();
          } catch (err) { toast.error(apiErrorMessage(err)); }
        }}>Create payout</Button>
      </div>
      <div className="mt-4">
        <Table
          head={["Reference", "Owner", "Amount", "Status", ""]}
          rows={(data ?? []).map((s) => [
            <span key="r" className="font-mono font-bold">{s.settlementReference}</span>,
            <span key="o">{s.businessName}</span>,
            <b key="a">{rupees(s.amountPaise)}</b>,
            <Badge key="s" status={s.status} />,
            s.status === "PENDING" || s.status === "PROCESSING" ? (
              <button key="m" className="text-xs font-bold text-emerald-600 hover:underline" onClick={async () => {
                await api.post(`/api/v1/admin/settlements/${s.id}/settle`, {});
                toast.success("Marked settled after the bank transfer.");
                refetch();
              }}>Mark settled</button>
            ) : <span key="m">—</span>,
          ])}
        />
      </div>
    </div>
  );
}
