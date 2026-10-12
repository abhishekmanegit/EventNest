import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { initials } from "../utils/format";

/* ----------------------------------------------------------------- Button - */

const buttonSizes = {
  sm: "h-8 px-3 text-sm",
  md: "h-10 px-4 text-sm",
  lg: "h-12 px-6 text-base",
};

const buttonVariants = {
  primary: "bg-primary text-white hover:bg-primary-ink focus-visible:outline-primary",
  secondary: "bg-surface text-ink border border-line hover:bg-canvas",
  ghost: "text-ink-soft hover:text-ink hover:bg-black/5",
  danger: "bg-danger text-white hover:bg-danger/90",
  dangerGhost: "text-danger border border-danger/30 hover:bg-danger-soft",
};

export function Button({ variant = "primary", size = "md", isLoading, className = "", children, disabled, ...props }) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-ctl font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${buttonSizes[size]} ${buttonVariants[variant]} ${className}`}
      disabled={disabled || isLoading}
      {...props}
    >
      {isLoading && <Spinner className="h-4 w-4" light={variant === "primary" || variant === "danger"} />}
      {children}
    </button>
  );
}

/* --------------------------------------------------------- form controls - */

const controlCls =
  "w-full rounded-ctl border border-line bg-surface px-3 py-2 text-sm text-ink placeholder:text-ink-faint focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20";

export function Input(props) {
  return <input className={controlCls} {...props} />;
}

export function Textarea(props) {
  return <textarea className={`${controlCls} min-h-[96px]`} {...props} />;
}

export function Select({ children, ...props }) {
  return <select className={controlCls} {...props}>{children}</select>;
}

/** Label + control + hint + per-field error — reused by every form. */
export function Field({ label, hint, error, required, name, children }) {
  return (
    <label className="block">
      {label && (
        <span className="mb-1 block text-sm font-semibold text-ink">
          {label}
          {required && <span className="ml-0.5 text-danger">*</span>}
        </span>
      )}
      {children}
      {error && <span className="mt-1 block text-xs text-danger">{error}</span>}
      {!error && hint && <span className="mt-1 block text-xs text-ink-soft">{hint}</span>}
    </label>
  );
}

/* ------------------------------------------------------------------ Badge - */

const statusColors = {
  PUBLISHED: "bg-success-soft text-success",
  PENDING_REVIEW: "bg-amber-soft text-amber",
  DRAFT: "bg-zinc-100 text-ink-soft",
  CANCELLED: "bg-danger-soft text-danger",
  COMPLETED: "bg-zinc-100 text-ink-soft",
  APPROVED: "bg-success-soft text-success",
  REJECTED: "bg-danger-soft text-danger",
  PENDING: "bg-amber-soft text-amber",
  SUSPENDED: "bg-danger-soft text-danger",
  ACTIVE: "bg-success-soft text-success",
  CONFIRMED: "bg-success-soft text-success",
  WAITLISTED: "bg-amber-soft text-amber",
  ATTENDED: "bg-success-soft text-success",
  DISQUALIFIED: "bg-danger-soft text-danger",
  VERIFIED: "bg-success-soft text-success",
  UNVERIFIED: "bg-zinc-100 text-ink-soft",
  OPEN: "bg-danger-soft text-danger",
  RESOLVED: "bg-success-soft text-success",
  DISMISSED: "bg-zinc-100 text-ink-soft",
  FEATURED: "bg-primary-soft text-primary",
};

export function Badge({ children, tone, className = "" }) {
  const cls = statusColors[tone] || (tone === "primary" ? "bg-primary-soft text-primary" : "bg-zinc-100 text-ink-soft");
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ${cls} ${className}`}>
      {children}
    </span>
  );
}

export function Chip({ active, onClick, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`inline-flex shrink-0 items-center rounded-full border px-3 py-1 text-sm transition-colors ${
        active
          ? "border-primary bg-primary text-white"
          : "border-line bg-surface text-ink-soft hover:border-primary/50 hover:text-ink"
      }`}
    >
      {children}
    </button>
  );
}

/* ------------------------------------------------------------------- Card - */

export function Card({ className = "", children, as = "div" }) {
  const Tag = as;
  return <Tag className={`rounded-card border border-line bg-surface shadow-card ${className}`}>{children}</Tag>;
}

/* --------------------------------------------------------------- Feedback - */

export function Spinner({ className = "h-5 w-5", light }) {
  return (
    <svg className={`animate-spin ${className} ${light ? "text-white/70" : "text-primary"}`} viewBox="0 0 24 24" fill="none">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
    </svg>
  );
}

export function PageLoader({ label = "Loading…" }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 py-24 text-ink-soft">
      <Spinner className="h-6 w-6" />
      <span className="text-sm">{label}</span>
    </div>
  );
}

export function EmptyState({ icon = "🗒️", title, message, action, actionTo, actionLabel }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-card border border-dashed border-line bg-surface/50 px-6 py-16 text-center">
      <span className="text-3xl">{icon}</span>
      <h3 className="text-base font-bold text-ink">{title}</h3>
      {message && <p className="max-w-sm text-sm text-ink-soft">{message}</p>}
      {action && actionLabel && (
        <div className="mt-3">
          {actionTo ? (
            <Link to={actionTo}><Button>{actionLabel}</Button></Link>
          ) : (
            <Button onClick={action}>{actionLabel}</Button>
          )}
        </div>
      )}
    </div>
  );
}

export function ErrorState({ title = "Couldn't load this.", message, onRetry }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-card border border-line bg-surface px-6 py-16 text-center">
      <span className="text-3xl">😕</span>
      <h3 className="text-base font-bold text-ink">{title}</h3>
      {message && <p className="max-w-sm text-sm text-ink-soft">{message}</p>}
      {onRetry && <Button variant="secondary" onClick={onRetry}>Retry</Button>}
    </div>
  );
}

export function Skeleton({ className = "" }) {
  return <div className={`animate-pulse rounded-ctl bg-zinc-200/70 ${className}`} />;
}

/* ------------------------------------------------------------ Notification - */

function ToastStack() {
  const { toasts, dismiss } = useToastState();
  return (
    <div className="pointer-events-none fixed bottom-4 right-4 z-[70] flex w-80 flex-col gap-2">
      {toasts.map((t) => (
        <div
          key={t.id}
          className={`en-toast pointer-events-auto flex items-start justify-between gap-3 rounded-ctl border px-4 py-3 text-sm shadow-sticky ${
            t.kind === "error" ? "border-danger/30 bg-danger text-white" : "border-line bg-ink text-white"
          }`}
        >
          <span>{t.message}</span>
          <button onClick={() => dismiss(t.id)} className="shrink-0 opacity-60 hover:opacity-100">✕</button>
        </div>
      ))}
    </div>
  );
}

const ToastCtx = createContext(null);
const toastStateCtx = createContext(null);

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const push = useCallback((kind, message) => {
    const id = Math.random().toString(36).slice(2);
    setToasts((prev) => [...prev, { id, kind, message }]);
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 4200);
  }, []);
  const dismiss = useCallback((id) => setToasts((prev) => prev.filter((t) => t.id !== id)), []);
  return (
    <ToastCtx.Provider value={push}>
      <toastStateCtx.Provider value={{ toasts, dismiss }}>
        {children}
        <ToastStack />
      </toastStateCtx.Provider>
    </ToastCtx.Provider>
  );
}

export function useToast() {
  const push = useContext(ToastCtx);
  if (!push) throw new Error("useToast must be used inside <ToastProvider>");
  return {
    success: (m) => push("success", m),
    error: (m) => push("error", m),
  };
}

function useToastState() {
  return useContext(toastStateCtx);
}

/* ------------------------------------------------------------------ Modal - */

export function Modal({ open, onClose, title, children, width = "max-w-md" }) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => { window.removeEventListener("keydown", onKey); document.body.style.overflow = ""; };
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <Card className={`en-fade relative w-full ${width} p-6`}>
        <div className="mb-4 flex items-start justify-between gap-4">
          <h2 className="text-lg font-bold">{title}</h2>
          <button onClick={onClose} className="text-ink-faint hover:text-ink" aria-label="Close">✕</button>
        </div>
        {children}
      </Card>
    </div>
  );
}

export function ConfirmDialog({ open, onClose, onConfirm, title, message, confirmLabel = "Confirm", danger }) {
  return (
    <Modal open={open} onClose={onClose} title={title} width="max-w-sm">
      <p className="mb-5 text-sm text-ink-soft">{message}</p>
      <div className="flex justify-end gap-3">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button variant={danger ? "danger" : "primary"} onClick={onConfirm}>{confirmLabel}</Button>
      </div>
    </Modal>
  );
}

/* ------------------------------------------------------------- Pagination - */

export function Pagination({ page, totalPages, onChange, className = "" }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <div className={`flex items-center justify-center gap-2 ${className}`}>
      <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>← Prev</Button>
      <span className="tnum text-sm text-ink-soft">Page {page + 1} of {totalPages}</span>
      <Button size="sm" variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next →</Button>
    </div>
  );
}

/* ------------------------------------------------------------------ Tabs --- */

export function Tabs({ tabs, active, onChange }) {
  return (
    <div className="flex gap-1 overflow-x-auto border-b border-line">
      {tabs.map((t) => (
        <button
          key={t.key}
          onClick={() => onChange(t.key)}
          className={`whitespace-nowrap border-b-2 px-4 py-2.5 text-sm font-semibold transition-colors ${
            active === t.key ? "border-primary text-primary" : "border-transparent text-ink-soft hover:text-ink"
          }`}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}

/* ----------------------------------------------------------------- Avatar - */

export function Avatar({ name, url, size = "h-9 w-9", className = "" }) {
  if (url) return <img src={url} alt={name} className={`${size} rounded-full object-cover ${className}`} />;
  return (
    <span className={`${size} inline-flex items-center justify-center rounded-full bg-primary-soft text-xs font-bold text-primary ${className}`}>
      {initials(name)}
    </span>
  );
}

/* --------------------------------------------------------------- StatCard - */

export function StatCard({ label, value, sub, tone = "ink" }) {
  const tones = {
    ink: "text-ink",
    primary: "text-primary",
    amber: "text-amber",
    success: "text-success",
    danger: "text-danger",
  };
  return (
    <Card className="p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-ink-soft">{label}</p>
      <p className={`tnum mt-1 text-2xl font-extrabold ${tones[tone]}`}>{value ?? "—"}</p>
      {sub && <p className="mt-0.5 text-xs text-ink-soft">{sub}</p>}
    </Card>
  );
}

/* -------------------------------------------------------- Data table header */

export function Th({ children, className = "" }) {
  return <th className={`px-4 py-3 text-left text-xs font-bold uppercase tracking-wide text-ink-soft ${className}`}>{children}</th>;
}

export function Td({ children, className = "" }) {
  return <td className={`px-4 py-3 align-middle text-sm text-ink ${className}`}>{children}</td>;
}