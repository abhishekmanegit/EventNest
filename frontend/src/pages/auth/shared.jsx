export function AlertBanner({ kind = "error", children }) {
  return (
    <div className={`mt-4 rounded-ctl border px-3 py-2.5 text-sm ${kind === "error" ? "border-danger/30 bg-danger-soft text-danger" : "border-amber/30 bg-amber-soft text-amber"}`}>
      {children}
    </div>
  );
}

export const PASSWORD_HINT = "8–72 characters with an uppercase, a lowercase, a digit and a symbol.";

export function validatePassword(pw) {
  return /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$/.test(pw);
}