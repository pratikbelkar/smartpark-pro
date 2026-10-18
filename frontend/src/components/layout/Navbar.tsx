import { Link, useNavigate } from "react-router";
import { Car, LogOut, LayoutDashboard } from "lucide-react";
import { useAuth } from "../../lib/auth";
import { useRoles } from "../../app/RequireRole";

export default function Navbar() {
  const { user, logout } = useAuth();
  const roles = useRoles();
  const navigate = useNavigate();
  const isAdmin = roles.includes("SUPER_ADMIN");

  return (
    <header className="sticky top-0 z-40 border-b border-slate-800 bg-navy-950/95 text-white backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-6 px-4">
        <Link to={isAdmin ? "/admin" : "/"} className="flex items-center gap-2">
          <span className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-emerald-500 to-cyan-400 font-extrabold text-navy-950">
            <Car size={20} strokeWidth={2.5} />
          </span>
          <span className="text-lg font-extrabold tracking-tight">
            SmartPark <span className="text-emerald-400">Admin</span>
          </span>
        </Link>
        {isAdmin && (
          <nav className="ml-4 hidden items-center gap-5 text-sm font-medium text-slate-300 lg:flex">
            <Link to="/admin/dashboard" className="flex items-center gap-1 transition hover:text-white">
              <LayoutDashboard size={15} /> Dashboard
            </Link>
            <Link to="/admin/owners" className="transition hover:text-white">Owners</Link>
            <Link to="/admin/bookings" className="transition hover:text-white">Bookings</Link>
            <Link to="/admin/payments" className="transition hover:text-white">Payments</Link>
          </nav>
        )}
        <div className="ml-auto flex items-center gap-2">
          {user ? (
            <>
              <span className="hidden text-sm text-slate-300 sm:block">{user.fullName.split(" ")[0]}</span>
              <button
                onClick={() => logout().then(() => navigate("/login"))}
                className="rounded-lg p-2 text-slate-300 transition hover:bg-white/10 hover:text-white"
                aria-label="Sign out"
              >
                <LogOut size={18} />
              </button>
            </>
          ) : (
            <Link
              to="/login"
              className="rounded-xl bg-emerald-500 px-4 py-2 text-sm font-semibold text-white transition hover:bg-emerald-600"
            >
              Sign in
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
