import { useState, type ReactNode } from "react";
import { Link, useLocation } from "react-router";
import { AnimatePresence, motion } from "framer-motion";
import {
  LayoutDashboard, MapPin, CalendarDays, Wallet, Users, Star, Settings,
  Building2, CreditCard, TrendingUp, FileText, ShieldCheck,
  LifeBuoy, ScanLine, Menu, X, Car,
} from "lucide-react";

export interface ConsoleLink {
  to: string;
  label: string;
  icon: ReactNode;
}

const OWNER_LINKS: ConsoleLink[] = [
  { to: "/owner/dashboard", label: "Dashboard", icon: <LayoutDashboard size={17} /> },
  { to: "/owner/locations", label: "Locations", icon: <MapPin size={17} /> },
  { to: "/owner/bookings", label: "Bookings", icon: <CalendarDays size={17} /> },
  { to: "/owner/earnings", label: "Earnings", icon: <Wallet size={17} /> },
  { to: "/owner/staff", label: "Staff", icon: <Users size={17} /> },
  { to: "/owner/reviews", label: "Reviews", icon: <Star size={17} /> },
  { to: "/owner/settings", label: "Settings", icon: <Settings size={17} /> },
];

const ADMIN_LINKS: ConsoleLink[] = [
  { to: "/admin/dashboard", label: "Dashboard", icon: <LayoutDashboard size={17} /> },
  { to: "/admin/owners", label: "Owners", icon: <Building2 size={17} /> },
  { to: "/admin/locations", label: "Locations", icon: <MapPin size={17} /> },
  { to: "/admin/bookings", label: "Bookings", icon: <CalendarDays size={17} /> },
  { to: "/admin/users", label: "Users", icon: <Users size={17} /> },
  { to: "/admin/payments", label: "Payments", icon: <CreditCard size={17} /> },
  { to: "/admin/revenue", label: "Revenue", icon: <TrendingUp size={17} /> },
  { to: "/admin/settlements", label: "Settlements", icon: <Wallet size={17} /> },
  { to: "/admin/reports", label: "Reports", icon: <FileText size={17} /> },
  { to: "/admin/reviews", label: "Reviews", icon: <Star size={17} /> },
  { to: "/admin/audit", label: "Audit", icon: <ShieldCheck size={17} /> },
  { to: "/admin/settings", label: "Settings", icon: <Settings size={17} /> },
  { to: "/admin/support", label: "Support", icon: <LifeBuoy size={17} /> },
];

export default function ConsoleShell({
  section,
  title,
  children,
}: {
  section: "owner" | "admin" | "staff";
  title: string;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const links = section === "owner" ? OWNER_LINKS : section === "admin" ? ADMIN_LINKS : [];
  const accent = section === "admin" ? "text-amber-300" : "text-emerald-300";

  const nav = (
    <nav className="space-y-1 p-3">
      {section === "staff" ? (
        <Link
          to="/staff"
          className="flex items-center gap-3 rounded-xl bg-white/10 px-3 py-2.5 text-sm font-semibold text-white"
        >
          <ScanLine size={17} /> Gate scanner
        </Link>
      ) : (
        links.map((l) => {
          const active = location.pathname === l.to || location.pathname.startsWith(l.to + "/");
          return (
            <Link
              key={l.to}
              to={l.to}
              onClick={() => setOpen(false)}
              className={`relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${
                active ? "bg-white/10 text-white" : "text-slate-400 hover:bg-white/5 hover:text-white"
              }`}
            >
              {active && (
                <motion.span
                  layoutId={`console-active-${section}`}
                  className="absolute left-0 top-1/2 h-6 w-1 -translate-y-1/2 rounded-full bg-gradient-to-b from-emerald-400 to-cyan-400"
                />
              )}
              <span className={active ? accent : ""}>{l.icon}</span>
              {l.label}
            </Link>
          );
        })
      )}
    </nav>
  );

  return (
    <div className="mx-auto flex max-w-[1400px] items-start gap-6 px-4 py-6">
      <aside className="sticky top-20 hidden w-60 shrink-0 overflow-hidden rounded-2xl bg-navy-950 text-white shadow-lg lg:block">
        <div className="flex items-center gap-2 border-b border-white/10 p-4">
          <span className="grid h-8 w-8 place-items-center rounded-lg bg-gradient-to-br from-emerald-500 to-cyan-400 font-extrabold text-navy-950">
            <Car size={18} strokeWidth={2.5} />
          </span>
          <div>
            <p className="text-sm font-extrabold leading-tight">{title}</p>
            <p className="text-[11px] capitalize text-slate-400">{section} console</p>
          </div>
        </div>
        {nav}
      </aside>

      <div className="min-w-0 flex-1">
        <div className="mb-4 flex items-center gap-3 lg:hidden">
          <button
            onClick={() => setOpen((v) => !v)}
            className="rounded-xl border border-slate-200 bg-white p-2 shadow-sm"
            aria-label="Toggle menu"
          >
            {open ? <X size={20} /> : <Menu size={20} />}
          </button>
          <p className="font-extrabold">{title}</p>
        </div>
        <AnimatePresence>
          {open && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: "auto" }}
              exit={{ opacity: 0, height: 0 }}
              className="mb-4 overflow-hidden rounded-2xl bg-navy-950 text-white shadow-lg lg:hidden"
            >
              {nav}
            </motion.div>
          )}
        </AnimatePresence>
        <motion.div
          key={location.pathname}
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.25, ease: "easeOut" }}
        >
          {children}
        </motion.div>
      </div>
    </div>
  );
}
