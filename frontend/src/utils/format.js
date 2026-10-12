export function toQuery(params = {}) {
  const sp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === null || v === "" || v === false) return;
    sp.set(k, v);
  });
  const s = sp.toString();
  return s ? `?${s}` : "";
}

export function fmtDate(instant) {
  if (!instant) return "—";
  return new Intl.DateTimeFormat("en-IN", {
    day: "numeric", month: "short", year: "numeric",
  }).format(new Date(instant));
}

export function fmtDateTime(instant) {
  if (!instant) return "—";
  return new Intl.DateTimeFormat("en-IN", {
    day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", hour12: true,
  }).format(new Date(instant));
}

export function fmtTime(instant) {
  if (!instant) return "—";
  return new Intl.DateTimeFormat("en-IN", { hour: "2-digit", minute: "2-digit", hour12: true }).format(new Date(instant));
}

export function timeUntil(instant) {
  const diff = new Date(instant).getTime() - Date.now();
  if (diff <= 0) return null;
  const msPerDay = 86400000;
  const days = Math.floor(diff / msPerDay);
  const hours = Math.floor((diff % msPerDay) / 3600000);
  const minutes = Math.floor((diff % 3600000) / 60000);
  if (days > 0) return `${days}d ${hours}h left`;
  if (hours > 0) return `${hours}h ${minutes}m left`;
  if (minutes > 0) return `${minutes}m left`;
  return "any moment";
}

export function money(amount, currency = "INR") {
  if (!amount) return null;
  return new Intl.NumberFormat("en-IN", {
    style: "currency", currency: currency || "INR", maximumFractionDigits: 0,
  }).format(amount);
}

export function initials(name = "") {
  return name.split(/\s+/).map((w) => w[0]).filter(Boolean).slice(0, 2).join("").toUpperCase();
}