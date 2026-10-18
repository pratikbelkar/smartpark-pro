import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import api, { apiErrorMessage } from "../../lib/api";
import { Badge, EmptyState, ErrorState, Skeleton, TextInput } from "../../components/ui/primitives";
import { Table } from "../../components/ui/console";
import type { Page } from "../../types";

interface AdminUser { id: string; fullName: string; email: string; phone: string | null; accountStatus: string; roles: string[]; createdAt: string }

export default function Users() {
  const [search, setSearch] = useState("");
  const [role, setRole] = useState("");
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["admin-users", search, role],
    queryFn: async () => {
      const q = new URLSearchParams({ size: "30" });
      if (search.trim()) q.set("search", search.trim());
      if (role) q.set("role", role);
      return (await api.get(`/api/v1/admin/users?${q.toString()}`)).data.data as Page<AdminUser>;
    },
  });

  async function setStatus(id: string, status: string) {
    if (!window.confirm(`${status} this user?`)) return;
    try {
      await api.put(`/api/v1/admin/users/${id}/status`, { status });
      toast.success("User updated.");
      refetch();
    } catch (err) {
      toast.error(apiErrorMessage(err));
    }
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-extrabold tracking-tight">Customers &amp; users</h1>
        <div className="flex gap-2">
          <TextInput value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search name/email…" />
          <select value={role} onChange={(e) => setRole(e.target.value)} className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm">
            <option value="">All roles</option>
            <option value="CUSTOMER">Customer</option>
            <option value="PARKING_OWNER">Owner</option>
            <option value="PARKING_STAFF">Staff</option>
            <option value="SUPER_ADMIN">Admin</option>
          </select>
        </div>
      </div>
      <div className="mt-4">
        {isLoading && <Skeleton className="h-40" />}
        {isError && <ErrorState message="Could not load users." onRetry={() => refetch()} />}
        {data && data.content.length === 0 && <EmptyState title="No users" />}
        {data && data.content.length > 0 && (
          <Table
            head={["User", "Roles", "Status", "Actions"]}
            rows={data.content.map((u) => [
              <span key="u"><b>{u.fullName}</b><br /><span className="text-slate-500">{u.email}</span></span>,
              <span key="r" className="flex flex-wrap gap-1">{u.roles.map((r) => <Badge key={r} status={r} />)}</span>,
              <Badge key="s" status={u.accountStatus} />,
              <span key="a" className="flex gap-2 text-xs font-bold">
                <button className="text-emerald-600 hover:underline" onClick={() => setStatus(u.id, "ACTIVE")}>Activate</button>
                <button className="text-red-600 hover:underline" onClick={() => setStatus(u.id, "SUSPENDED")}>Suspend</button>
              </span>,
            ])}
          />
        )}
      </div>
    </div>
  );
}
