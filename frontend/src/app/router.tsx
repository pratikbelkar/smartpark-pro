import { createBrowserRouter, Navigate } from "react-router";
import Navbar from "../components/layout/Navbar";
import Footer from "../components/layout/Footer";
import RequireRole from "./RequireRole";
import ConsoleShell from "../components/layout/ConsoleShell";
import Login from "../pages/Login";
import AdminDashboard from "../pages/admin/Dashboard";
import AdminOwners from "../pages/admin/Owners";
import AdminBookings from "../pages/admin/Bookings";
import AdminUsers from "../pages/admin/Users";
import AdminLocations from "../pages/admin/Locations";
import AdminPayments from "../pages/admin/Payments";
import AdminRevenue from "../pages/admin/Revenue";
import AdminSettlements from "../pages/admin/Settlements";
import { Reports, Reviews, Audit, Support, FeeSettings } from "../pages/admin/More";
import { useAuth } from "../lib/auth";

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col">
      <Navbar />
      <main className="flex-1">{children}</main>
      <Footer />
    </div>
  );
}

/** Root: admins go to the console, everyone else signs in (customers use the mobile apps). */
function Index() {
  const { user, loading } = useAuth();
  if (loading) return <div className="p-16 text-center text-slate-500">Loading…</div>;
  if (user && user.roles.includes("SUPER_ADMIN")) return <Navigate to="/admin/dashboard" replace />;
  return <Navigate to="/login" replace />;
}

const admin = (el: React.ReactNode) => (
  <Shell><RequireRole roles={["SUPER_ADMIN"]}>
    <ConsoleShell section="admin" title="Admin console">{el}</ConsoleShell>
  </RequireRole></Shell>
);

const router = createBrowserRouter([
  { path: "/", element: <Shell><Index /></Shell> },
  { path: "/login", element: <Shell><Login /></Shell> },
  { path: "/admin", element: admin(<AdminDashboard />) },
  { path: "/admin/dashboard", element: admin(<AdminDashboard />) },
  { path: "/admin/owners", element: admin(<AdminOwners />) },
  { path: "/admin/locations", element: admin(<AdminLocations />) },
  { path: "/admin/bookings", element: admin(<AdminBookings />) },
  { path: "/admin/bookings/:reference", element: admin(<AdminBookings />) },
  { path: "/admin/users", element: admin(<AdminUsers />) },
  { path: "/admin/payments", element: admin(<AdminPayments />) },
  { path: "/admin/revenue", element: admin(<AdminRevenue />) },
  { path: "/admin/settlements", element: admin(<AdminSettlements />) },
  { path: "/admin/reports", element: admin(<Reports />) },
  { path: "/admin/reviews", element: admin(<Reviews />) },
  { path: "/admin/audit", element: admin(<Audit />) },
  { path: "/admin/settings", element: admin(<FeeSettings />) },
  { path: "/admin/support", element: admin(<Support />) },
]);

export default router;
