import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { ApiError } from "../../services/apiClient";
import { homeFor } from "../../components/guards";
import { AuthLayout } from "../../components/layouts";
import { AlertBanner } from "./shared";
import { Button, Field, Input } from "../../components/ui";

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const next = location.state?.next || "/";

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const user = await login(email.trim(), password);
      navigate(user.role === "ADMIN" ? "/admin/dashboard" : user.role === "ORGANIZER" ? "/organizer/dashboard" : next, { replace: true });
    } catch (err) {
      setError(err instanceof ApiError && err.code === "EMAIL_ALREADY_REGISTERED" ? "Please sign in below." : err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout>
      <h1 className="text-2xl font-extrabold">Welcome back</h1>
      <p className="mt-1 text-sm text-ink-soft">Sign in to see what's happening on campus.</p>
      {error && <AlertBanner kind="error">{error}</AlertBanner>}
      <form onSubmit={submit} className="mt-6 space-y-4">
        <Field label="Email" required>
          <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@college.edu.in" required autoFocus />
        </Field>
        <Field label="Password" required hint="Or the demo account: admin@eventnest.dev / Admin@12345">
          <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" required />
        </Field>
        <Button type="submit" size="lg" className="w-full" isLoading={busy}>Sign in</Button>
      </form>
      <p className="mt-6 text-center text-sm text-ink-soft">
        New to EventNest? <Link to="/register" className="font-semibold text-primary hover:underline">Create an account</Link>
      </p>
    </AuthLayout>
  );
}