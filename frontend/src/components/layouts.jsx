import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { api } from "../services/api";
import { useAuth } from "../context/AuthContext";
import { Avatar, Badge } from "./ui";
import { useLocation } from "react-router-dom";

/* ----------------------------------------------------------------- Navbar - */

const publicLinks = (isTop) => {
  if (isTop) {
    return [
      { to: "/events", label: "Events" },
      { to: "/colleges", label: "Colleges" },
    ];
  }
  return [];
};

function AppBrand() {
  return (
    <Link to="/" className="flex items-center gap-2">
      <img src="/favicon.svg" alt="" className="h-8 w-8" />
      <span className="text-lg font-extrabold tracking-tight text-ink">
        Event<span className="text-primary">Nest</span>
      </span>
    </Link>
  );
}

export function PublicNavbar() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [q, setQ] = useState("");
  const submit = (e) => {
    e.preventDefault();
    navigate(q.trim() ? `/events?q=${encodeURIComponent(q.trim())}` : "/events");
  };
  return (
    <header className="sticky top-0 z-40 border-b border-line bg-surface/90 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-6 px-4">
        <AppBrand />
        <nav className="hidden items-center gap-1 md:flex">
          <NavLink to="/events" className={({ isActive }) => `rounded-ctl px-3 py-1.5 text-sm font-semibold ${isActive ? "text-primary" : "text-ink-soft hover:text-ink"}`}>
            Events
          </NavLink>
          <NavLink to="/colleges" className={({ isActive }) => `rounded-ctl px-3 py-1.5 text-sm font-semibold ${isActive ? "text-primary" : "text-ink-soft hover:text-ink"}`}>
            Colleges
          </NavLink>
        </nav>
        <form onSubmit={submit} className="ml-auto hidden w-64 sm:block lg:w-80">
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search tech, hackathons, fests…"
            className="w-full rounded-ctl border border-line bg-canvas px-3 py-1.5 text-sm outline-none focus:border-primary"
          />
        </form>
        <div className="ml-2 flex items-center gap-2 lg:ml-0">
          {user ? (
            <>
              <NotificationBell />
              <a
                href={roleHome(user.role)}
                className="hidden rounded-ctl px-3 py-1.5 text-sm font-semibold text-primary hover:bg-primary-soft sm:block"
              >
                Dashboard
              </a>
              <ProfileMenuLabel user={user} />
            </>
          ) : (
            <>
              <Link to="/login"><span className="px-3 py-1.5 text-sm font-semibold text-ink-soft hover:text-ink">Sign in</span></Link>
              <Link to="/register" className="rounded-ctl bg-primary px-4 py-2 text-sm font-semibold text-white hover:bg-primary-ink">
                Register
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}

function roleHome(role) {
  if (role === "ADMIN") return "/admin/dashboard";
  if (role === "ORGANIZER") return "/organizer/dashboard";
  return "/dashboard";
}

export function ProfileMenuLabel({ user }) {
  const { logout } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  useEffect(() => {
    const onDoc = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);
  const items = [
    { to: roleHome(user.role), label: "Dashboard" },
    ...(user.role === "STUDENT" ? [{ to: "/profile", label: "My profile" }] : []),
    ...(user.role === "ORGANIZER" ? [{ to: "/organizer/profile", label: "College profile" }] : []),
  ];
  return (
    <div className="relative" ref={ref}>
      <button onClick={() => setOpen((o) => !o)} className="flex items-center gap-2 rounded-ctl p-1 hover:bg-canvas">
        <Avatar name={user.fullName} url={user.avatarUrl} />
      </button>
      {open && (
        <div className="en-fade absolute right-0 top-12 w-64 rounded-ctl border border-line bg-surface p-1 shadow-sticky">
          <div className="border-b border-line px-3 py-2">
            <p className="truncate text-sm font-bold">{user.fullName}</p>
            <p className="truncate text-xs text-ink-soft">{user.email}</p>
          </div>
          {user.role === "ORGANIZER" && user.organizer && (
            <div className="px-3 py-2">
              <Badge tone={user.organizer.verificationStatus || "PENDING"}>{user.organizer.verificationStatus || "PENDING"}</Badge>
              {user.organizer.verificationStatus === "PENDING" && (
                <p className="mt-1 text-xs text-ink-soft">Your college is awaiting admin approval.</p>
              )}
            </div>
          )}
          {items.map((i) => (
            <Link key={i.to} to={i.to} onClick={() => setOpen(false)} className="block rounded-ctl px-3 py-2 text-sm font-medium text-ink hover:bg-canvas">
              {i.label}
            </Link>
          ))}
          <button onClick={() => logout()} className="block w-full rounded-ctl px-3 py-2 text-left text-sm font-medium text-danger hover:bg-danger-soft">
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------ NotificationBell */

export function NotificationBell() {
  const [count, setCount] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const ref = useRef(null);
  const { user } = useAuth();
  const location = useLocation();

  const poll = async () => {
    if (!user) return;
    try {
      const u = await api.me.unreadCount();
      setCount(u.count);
    } catch { /* ignore */ }
  };

  useEffect(() => { poll(); const t = setInterval(poll, 60000); return () => clearInterval(t); }, [user]);
  useEffect(() => {
    const onFocus = () => poll();
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, []);
  useEffect(() => {
    const onDoc = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);
  useEffect(() => { setOpen(false); }, [location.key]);

  const toggle = async () => {
    const next = !open;
    setOpen(next);
    if (next && !items.length) {
      setLoading(true);
      try {
        const r = await api.me.notifications({ size: 12 });
        setItems(r.content);
        setCount(0);
      } finally { setLoading(false); }
      poll();
    }
  };

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={toggle}
        className="relative flex h-9 w-9 items-center justify-center rounded-ctl text-ink-soft hover:bg-canvas hover:text-ink"
        aria-label="Notifications"
      >
        <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
          <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.7 21a2 2 0 0 1-3.4 0" />
        </svg>
        {count > 0 && (
          <span className="tnum absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-amber px-1 text-xs font-bold text-white">
            {count > 99 ? "99+" : count}
          </span>
        )}
      </button>
      {open && (
        <div className="en-fade absolute right-0 top-11 w-80 overflow-hidden rounded-ctl border border-line bg-surface shadow-sticky">
          <div className="flex items-center justify-between border-b border-line px-4 py-2.5">
            <span className="text-sm font-bold">Notifications</span>
            <Link to="/notifications" className="text-xs font-semibold text-primary hover:underline">View all</Link>
          </div>
          <div className="max-h-80 overflow-y-auto">
            {loading && <p className="px-4 py-8 text-center text-sm text-ink-soft">Loading…</p>}
            {!loading && items.length === 0 && <p className="px-4 py-8 text-center text-sm text-ink-soft">Nothing here yet.</p>}
            {items.map((n) => (
              <a
                key={n.id}
                href={n.actionUrl || (n.eventId ? `/events/${n.eventId}` : "/notifications")}
                className="block border-b border-line px-4 py-3 hover:bg-canvas"
              >
                <p className={`text-sm ${n.read ? "text-ink-soft" : "font-semibold text-ink"}`}>{n.title}</p>
                {n.body && <p className="mt-0.5 line-clamp-2 text-xs text-ink-soft">{n.body}</p>}
              </a>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/* ---------------------------------------------------------------- Layouts --- */

export function PublicLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <PublicNavbar />
      <main className="flex-1">
        <Outlet />
      </main>
      <footer className="border-t border-line py-8">
        <div className="mx-auto flex max-w-7xl flex-col items-center justify-between gap-3 px-4 text-sm text-ink-soft sm:flex-row">
          <span>© {new Date().getFullYear()} EventNest — every campus event, one inbox.</span>
          <span className="flex gap-4">
            <Link className="hover:text-ink" to="/events">Browse events</Link>
            <Link className="hover:text-ink" to="/colleges">Colleges</Link>
            <Link className="hover:text-ink" to="/register">Register</Link>
          </span>
        </div>
      </footer>
    </div>
  );
}

export function AuthLayout({ children }) {
  return (
    <div className="flex min-h-screen">
      <div className="hidden w-1/2 flex-col justify-between bg-primary p-12 text-white lg:flex">
        <Link to="/" className="flex items-center gap-2">
          <img src="/favicon.svg" alt="" className="h-9 w-9" />
          <span className="text-xl font-extrabold">EventNest</span>
        </Link>
        <div>
          <h1 className="text-3xl font-extrabold leading-tight">Every campus event,<br />one inbox.</h1>
          <p className="mt-4 max-w-md text-white/80">
            Hackathons, fests, workshops and interviews — find them, register, and never miss a deadline again.
          </p>
        </div>
        <p className="text-sm text-white/60">Built for students and college event organisers.</p>
      </div>
      <div className="flex w-full items-center justify-center bg-canvas p-6 lg:w-1/2">
        <div className="w-full max-w-md">{children}</div>
      </div>
    </div>
  );
}