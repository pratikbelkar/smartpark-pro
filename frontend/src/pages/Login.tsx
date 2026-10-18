import { useState } from "react";
import { useNavigate, useLocation } from "react-router";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { useAuth } from "../lib/auth";
import { apiErrorMessage } from "../lib/api";
import { Button, Card, Field, TextInput } from "../components/ui/primitives";

const schema = z.object({
  email: z.string().email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

type Form = z.infer<typeof schema>;

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation() as { state?: { from?: string } };
  const [busy, setBusy] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Form>({ resolver: zodResolver(schema) });

  return (
    <div className="mx-auto max-w-md px-4 py-12">
      <Card>
        <h1 className="text-2xl font-extrabold tracking-tight">Admin sign in</h1>
        <p className="mt-1 text-sm text-slate-500">
          Platform administrators only. Customers, owners and staff use the mobile apps.
        </p>
        <form
          className="mt-6 space-y-4"
          onSubmit={handleSubmit(async (values) => {
            setBusy(true);
            try {
              await login(values.email, values.password);
              toast.success("Signed in successfully.");
              navigate(location.state?.from ?? "/admin/dashboard");
            } catch (err) {
              toast.error(apiErrorMessage(err, "Incorrect email or password."));
            } finally {
              setBusy(false);
            }
          })}
        >
          <Field label="Email" error={errors.email?.message}>
            <TextInput type="email" autoComplete="email" {...register("email")} />
          </Field>
          <Field label="Password" error={errors.password?.message}>
            <div className="relative">
              <TextInput
                type={showPassword ? "text" : "password"}
                autoComplete="current-password"
                {...register("password")}
                className="pr-16"
              />
              <button
                type="button"
                onClick={() => setShowPassword((v) => !v)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-xs font-bold text-emerald-600 hover:text-emerald-700"
              >
                {showPassword ? "Hide" : "Show"}
              </button>
            </div>
          </Field>
          <Button type="submit" disabled={busy} className="w-full">
            {busy ? "Signing in…" : "Sign in"}
          </Button>
        </form>
      </Card>
    </div>
  );
}
