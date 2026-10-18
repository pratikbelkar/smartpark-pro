import type { ReactNode } from "react";
import { Navigate } from "react-router";
import { useAuth } from "../lib/auth";
import RequireAuth from "./RequireAuth";

export default function RequireRole({ roles, children }: { roles: string[]; children: ReactNode }) {
  return (
    <RequireAuth>
      <RoleCheck roles={roles}>{children}</RoleCheck>
    </RequireAuth>
  );
}

function RoleCheck({ roles, children }: { roles: string[]; children: ReactNode }) {
  const { user } = useAuth();
  const ok = user?.roles.some((r: string) => roles.includes(r));
  if (!ok) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16 text-center">
        <p className="text-xl font-extrabold">Access denied</p>
        <p className="mt-1 text-sm text-slate-500">Your account does not have access to this area.</p>
      </div>
    );
  }
  return <>{children}</>;
}

export function useRoles(): string[] {
  const { user } = useAuth();
  return user?.roles ?? [];
}

export function HomeForRole({ children }: { children: (roles: string[]) => ReactNode }) {
  const roles = useRoles();
  if (roles.length === 0) return <Navigate to="/login" replace />;
  return <>{children(roles)}</>;
}
