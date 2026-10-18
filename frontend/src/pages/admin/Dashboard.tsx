import { useQuery } from "@tanstack/react-query";
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import api from "../../lib/api";
import { rupees } from "../../lib/format";
import { StatCard } from "../../components/ui/console";
import { ErrorState, Skeleton } from "../../components/ui/primitives";

const PIE_COLORS = ["#10B981", "#22D3EE", "#F59E0B", "#EF4444", "#94A3B8", "#6366F1", "#111827"];

interface Dashboard {
  cards: Record<string, number>;
  bookingsDaily: { date: string; bookings: number }[];
  revenueDaily: { date: string; grossPaise: number }[];
  statusDistribution: { status: string; count: number }[];
  vehicleSplit: { type: string; count: number }[];
  topLocations: { locationId: string; locationName: string; grossPaise: number }[];
  peakHours: { hour: number; bookings: number }[];
}

export default function AdminDashboard() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["admin-dashboard"],
    queryFn: async () => (await api.get("/api/v1/admin/dashboard")).data.data as Dashboard,
  });

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <h1 className="text-3xl font-extrabold tracking-tight">Platform dashboard</h1>
      {isLoading && <div className="mt-4 grid gap-4 md:grid-cols-4"><Skeleton className="h-28" /><Skeleton className="h-28" /><Skeleton className="h-28" /><Skeleton className="h-28" /></div>}
      {isError && <div className="mt-4"><ErrorState message="Could not load dashboard." onRetry={() => refetch()} /></div>}
      {data && (
        <>
          <div className="mt-4 grid gap-4 md:grid-cols-4">
            <StatCard label="Customers" value={String(data.cards.totalCustomers)} />
            <StatCard label="Active owners" value={String(data.cards.activeOwners)} />
            <StatCard label="Locations" value={String(data.cards.totalLocations)} />
            <StatCard label="Slots" value={`${data.cards.availableSlots}/${data.cards.totalSlots}`} sub="available / total" />
            <StatCard label="Parked now" value={String(data.cards.activeSessions)} />
            <StatCard label="Reserved today" value={String(data.cards.reservedSlots)} />
            <StatCard label="Bookings today" value={String(data.cards.todayBookings)} />
            <StatCard label="Maintenance" value={String(data.cards.maintenanceSlots)} />
            <StatCard label="Today GBV" value={rupees(data.cards.todayGrossPaise)} />
            <StatCard label="Today platform" value={rupees(data.cards.todayPlatformPaise)} />
            <StatCard label="Month platform" value={rupees(data.cards.monthPlatformPaise)} />
            <StatCard label="Pending refunds" value={String(data.cards.pendingRefunds)} />
          </div>

          <div className="mt-6 grid gap-4 lg:grid-cols-2">
            <div className="rounded-2xl border border-slate-200 bg-white p-5">
              <h2 className="font-extrabold">Bookings · 14 days</h2>
              <div className="mt-3 h-64">
                <ResponsiveContainer>
                  <AreaChart data={data.bookingsDaily}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="date" tick={{ fontSize: 10 }} interval={2} />
                    <YAxis allowDecimals={false} />
                    <Tooltip />
                    <Area type="monotone" dataKey="bookings" stroke="#10B981" fill="#A7F3D0" />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-5">
              <h2 className="font-extrabold">Revenue · 30 days</h2>
              <div className="mt-3 h-64">
                <ResponsiveContainer>
                  <BarChart data={data.revenueDaily}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="date" tick={{ fontSize: 10 }} interval={6} />
                    <YAxis tickFormatter={(v: number) => `₹${Math.round(v / 100)}`} />
                    <Tooltip formatter={(v) => (typeof v === "number" ? rupees(v) : v)} />
                    <Bar dataKey="grossPaise" fill="#0B1220" name="Gross" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-5">
              <h2 className="font-extrabold">Booking status · 30d</h2>
              <div className="mt-3 h-64">
                <ResponsiveContainer>
                  <PieChart>
                    <Pie data={data.statusDistribution} dataKey="count" nameKey="status" outerRadius={90} label>
                      {data.statusDistribution.map((_, i) => (
                        <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-5">
              <h2 className="font-extrabold">Peak hours · 30d</h2>
              <div className="mt-3 h-64">
                <ResponsiveContainer>
                  <BarChart data={data.peakHours}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="hour" tick={{ fontSize: 10 }} interval={2} />
                    <YAxis allowDecimals={false} />
                    <Tooltip />
                    <Bar dataKey="bookings" fill="#22D3EE" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          <div className="mt-6 grid gap-4 lg:grid-cols-2">
            <div className="rounded-2xl border border-slate-200 bg-white p-5">
              <h2 className="font-extrabold">Car vs two-wheeler · 30d</h2>
              <ul className="mt-3 space-y-2 text-sm">
                {data.vehicleSplit.map((v) => (
                  <li key={v.type} className="flex justify-between rounded-xl bg-slate-50 px-3 py-2">
                    <b>{v.type}</b><span>{v.count} bookings</span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-5">
              <h2 className="font-extrabold">Most profitable locations</h2>
              <ul className="mt-3 space-y-2 text-sm">
                {data.topLocations.map((l) => (
                  <li key={l.locationId} className="flex justify-between rounded-xl bg-slate-50 px-3 py-2">
                    <b>{l.locationName}</b><span>{rupees(l.grossPaise)}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
