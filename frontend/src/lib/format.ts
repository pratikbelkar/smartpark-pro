export function rupees(paise: number): string {
  return `₹${(paise / 100).toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
}

export function formatDateTime(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-IN", {
    day: "numeric",
    month: "short",
    hour: "numeric",
    minute: "2-digit",
  });
}

export function formatDate(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

export function toLocalInputValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(
    date.getMinutes(),
  )}`;
}

export const STATUS_STYLES: Record<string, string> = {
  CONFIRMED: "bg-emerald-100 text-emerald-800",
  CHECKED_IN: "bg-cyan-100 text-cyan-800",
  COMPLETED: "bg-slate-200 text-slate-700",
  PENDING_PAYMENT: "bg-amber-100 text-amber-800",
  CANCELLED: "bg-red-100 text-red-700",
  EXPIRED: "bg-slate-200 text-slate-500",
  NO_SHOW: "bg-red-100 text-red-700",
  CAPTURED: "bg-emerald-100 text-emerald-800",
  PENDING: "bg-amber-100 text-amber-800",
  AVAILABLE: "bg-emerald-100 text-emerald-800",
  RESERVED: "bg-amber-100 text-amber-800",
  OCCUPIED: "bg-red-100 text-red-700",
  MAINTENANCE: "bg-slate-200 text-slate-500",
};
