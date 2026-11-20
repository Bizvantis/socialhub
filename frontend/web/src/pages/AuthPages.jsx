import { useState } from "react";
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Auth } from "../api/endpoints";
import { ErrorBox } from "../components/ui";

function Shell({ title, subtitle, children }) {
  return (
    <div className="grid min-h-screen place-items-center bg-[radial-gradient(60rem_30rem_at_10%_-10%,rgb(124_58_237/0.18),transparent),radial-gradient(50rem_30rem_at_110%_110%,rgb(249_115_22/0.15),transparent)] p-4">
      <main className="card w-full max-w-sm p-7 shadow-xl">
        <Link to="/" className="mb-5 flex items-center justify-center gap-2 text-lg font-extrabold">
          <img src={`${import.meta.env.BASE_URL}logo.svg`} alt="" className="h-9 w-9" /> SocialHub
        </Link>
        <h1 className="text-center text-2xl font-bold">{title}</h1>
        {subtitle && <p className="mb-5 mt-1 text-center text-sm text-zinc-500">{subtitle}</p>}
        {children}
      </main>
    </div>
  );
}

function Field({ label, error, ...props }) {
  return (
    <label className="grid gap-1.5 text-sm font-semibold">
      {label}
      <input className="input font-normal" aria-invalid={!!error} {...props} />
      {error && <span className="text-xs font-normal text-red-500" role="alert">{error}</span>}
    </label>
  );
}

// Public demo account for visitors (set at build time on the demo server; absent elsewhere).
const DEMO_USER = import.meta.env.VITE_DEMO_USERNAME;
const DEMO_PASSWORD = import.meta.env.VITE_DEMO_PASSWORD;

export function LoginPage() {
  const { login, status } = useAuth();
  const navigate = useNavigate();
  const from = useLocation().state?.from || "/";
  const [form, setForm] = useState({ id: "", password: "" });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (status === "authenticated") return <Navigate to={from} replace />;

  async function submit(e, demo = false) {
    e.preventDefault();
    const id = demo ? DEMO_USER : form.id.trim();
    const password = demo ? DEMO_PASSWORD : form.password;
    if (!id || !password) { setError(new Error("Enter your email/username and password.")); return; }
    setBusy(true);
    setError(null);
    try {
      await login(id, password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.status === 401 ? new Error("Wrong email/username or password.") : err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell title="Welcome back" subtitle="Log in to see your feed.">
      {DEMO_USER && DEMO_PASSWORD && (
        <div className="mb-5 grid gap-2 rounded-xl border border-violet-200 bg-violet-50 p-4 text-sm dark:border-violet-900 dark:bg-violet-950/40">
          <p><span className="font-semibold">Just looking around?</span> Use the shared demo account: no sign-up needed.</p>
          <button type="button" className="btn-primary w-full py-2.5" disabled={busy} onClick={(e) => submit(e, true)}>
            {busy ? "Logging in..." : "Continue as demo user"}
          </button>
        </div>
      )}
      <form onSubmit={submit} className="grid gap-4" noValidate>
        <Field label="Email or username" autoComplete="username" value={form.id} onChange={(e) => setForm({ ...form, id: e.target.value })} />
        <Field label="Password" type="password" autoComplete="current-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
        <ErrorBox error={error} />
        <button className="btn-primary w-full py-2.5" disabled={busy}>{busy ? "Logging in..." : "Log in"}</button>
      </form>
      <p className="mt-4 text-center text-sm"><Link to="/forgot-password" className="text-violet-brand hover:underline">Forgot password?</Link></p>
      <p className="mt-2 text-center text-sm text-zinc-500">New here? <Link to="/register" className="text-violet-brand hover:underline">Create an account</Link></p>
    </Shell>
  );
}

export function RegisterPage() {
  const { register, status } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: "", username: "", password: "" });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (status === "authenticated") return <Navigate to="/" replace />;

  function validate() {
    const e = {};
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) e.email = "Enter a valid email address.";
    if (!/^[A-Za-z0-9_]{3,30}$/.test(form.username)) e.username = "3-30 letters, digits or underscores.";
    if (form.password.length < 8) e.password = "Use at least 8 characters.";
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function submit(e) {
    e.preventDefault();
    if (!validate()) return;
    setBusy(true);
    setError(null);
    try {
      await register(form.email.trim(), form.username.trim(), form.password);
      navigate("/settings?welcome=1", { replace: true });
    } catch (err) {
      if (err.status === 409) setErrors(/email/i.test(err.message) ? { email: err.message } : { username: err.message });
      else setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell title="Create your account" subtitle="It takes less than a minute.">
      <form onSubmit={submit} className="grid gap-4" noValidate>
        <Field label="Email" type="email" autoComplete="email" value={form.email} error={errors.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
        <Field label="Username" autoComplete="username" value={form.username} error={errors.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
        <Field label="Password" type="password" autoComplete="new-password" value={form.password} error={errors.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
        <ErrorBox error={error} />
        <button className="btn-primary w-full py-2.5" disabled={busy}>{busy ? "Creating account..." : "Create account"}</button>
      </form>
      <p className="mt-4 text-center text-sm text-zinc-500">Already have an account? <Link to="/login" className="text-violet-brand hover:underline">Log in</Link></p>
    </Shell>
  );
}

export function ForgotPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState(null);
  async function submit(e) {
    e.preventDefault();
    try {
      await Auth.forgot(email.trim());
      setSent(true);
    } catch (err) {
      setError(err);
    }
  }
  return (
    <Shell title="Forgot your password?" subtitle="We'll email you a reset link.">
      {sent ? <p className="rounded-xl bg-green-500/10 p-3 text-sm text-green-600">If an account exists for that email, a reset link is on its way.</p> : (
        <form onSubmit={submit} className="grid gap-4">
          <Field label="Email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          <ErrorBox error={error} />
          <button className="btn-primary w-full py-2.5">Send reset link</button>
        </form>
      )}
      <p className="mt-4 text-center text-sm"><Link to="/login" className="text-violet-brand hover:underline">Back to log in</Link></p>
    </Shell>
  );
}

export function ResetPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  async function submit(e) {
    e.preventDefault();
    if (password.length < 8) { setError(new Error("Use at least 8 characters.")); return; }
    try {
      await Auth.reset(params.get("token") || "", password);
      navigate("/login", { replace: true });
    } catch (err) {
      setError(err);
    }
  }
  return (
    <Shell title="Choose a new password">
      <form onSubmit={submit} className="mt-4 grid gap-4">
        <Field label="New password" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        <ErrorBox error={error} />
        <button className="btn-primary w-full py-2.5">Update password</button>
      </form>
    </Shell>
  );
}
