import { useState } from "react";
import { useParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import api from "../../lib/api";
import { formatDateTime, rupees } from "../../lib/format";
import { Badge, Button, Card, EmptyState, ErrorState, Skeleton, TextInput } from "../../components/ui/primitives";
import { Table } from "../../components/ui/console";
import type { Booking, Page } from "../../types";

export default function AdminBookings() {
  const { reference: routeRef } = useParams();
  const [status, setStatus] = useState("");
  const [reference, setReference] = useState(routeRef ?? "");
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["admin-bookings", status],
    queryFn: async () =>
      (await api.get(`/api/v1/admin/bookings${status ? `?status=${status}` : ""}&size=50`)).data.data as Page<Booking>,
  });
  const lookup = useQuery({
    queryKey: ["admin-booking", reference],
    queryFn: async () => {
      const all = (await api.get("/api/v1/admin/bookings?size=100")).data.data as Page<Booking>;
      return all.content.find((b) => b.bookingReference === reference.trim().toUpperCase()) ?? null;
    },
    enabled: reference.trim().length > 0,
  });

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-extrabold tracking-tight">All bookings</h1>
        <div className="flex gap-2">
          <select value={status} onChange={(e) => setStatus(e.target.value)} className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm">
            <option value="">All statuses</option>
            {["PENDING_PAYMENT", "CONFIRMED", "CHECKED_IN", "COMPLETED", "CANCELLED", "EXPIRED", "NO_SHOW"].map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
          <TextInput value={reference} onChange={(e) => setReference(e.target.value.toUpperCase())} placeholder="Reference…" />
          <Button onClick={() => reference.trim() && window.location.assign(`/admin/bookings/${reference.trim()}`)}>Open</Button>
        </div>
      </div>
      {reference.trim() && (
        <Card className="mt-4 text-sm">
          {lookup.isLoading && "Looking up…"}
          {lookup.data === null && lookup.isFetched && <span>No booking with that reference in the latest 100.</span>}
          {lookup.data && (
            <p>
              <b className="font-mono">{lookup.data.bookingReference}</b> · {lookup.data.locationName} ·{" "}
              {lookup.data.slotNumber} · {formatDateTime(lookup.data.start)} → {formatDateTime(lookup.data.end)} ·{" "}
              {rupees(lookup.data.totalPaise)} <Badge status={lookup.data.bookingStatus} />{" "}
              <Badge status={lookup.data.paymentStatus} />
            </p>
          )}
        </Card>
      )}
      <div className="mt-4">
        {isLoading && <Skeleton className="h-40" />}
        {isError && <ErrorState message="Could not load bookings." onRetry={() => refetch()} />}
        {data && data.content.length === 0 && <EmptyState title="No bookings" />}
        {data && data.content.length > 0 && (
          <Table
            head={["Reference", "Location · Slot", "Window", "Total", "Booking", "Payment"]}
            rows={data.content.map((b) => [
              <span key="r" className="font-mono font-bold">{b.bookingReference}</span>,
              <span key="l">{b.locationName} · <b>{b.slotNumber}</b></span>,
              <span key="w">{formatDateTime(b.start)} → {formatDateTime(b.end)}</span>,
              <b key="t">{rupees(b.totalPaise)}</b>,
              <Badge key="b" status={b.bookingStatus} />,
              <Badge key="p" status={b.paymentStatus} />,
            ])}
          />
        )}
      </div>
    </div>
  );
}
