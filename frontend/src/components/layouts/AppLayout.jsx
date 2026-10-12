import { Link, NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { NotificationBell, ProfileMenuLabel } from "../layouts";

const nav = {
  STUDENT: [
    { to: "/dashboard", label: "Dashboard", icon: "◫" },
    { to: "/me/registrations", label: "My registrations", icon: "✓" },
    { to: "/me/saved", label: "Saved events", icon: "☆" },
    { to: "/notifications", label: "Notifications", icon: "🔔" },
    { to: "/profile", label: "My profile", icon: "👤" },
  ],
  ORGANIZER: [
    { to: "/organizer/dashboard", label: "Dashboard", icon: "◫" },
    { to: "/organizer/events", label: "My events", icon: "🗒" },
    { to: "/organizer/events/create", label: "Create event", icon: "＋" },
    { to: "/organizer/profile", label: "College profile", icon: "🏛" },
    { to: "/notifications", label: "Notifications", icon: "🔔" },
  ],
  ADMIN: [
    { to: "/admin/dashboard", label: "Dashboard", icon: "◫" },
    { to: "/admin/users", label: "Users", icon: "👥" },
    { to: "/admin/organizers", label: "Organizers", icon: "🏛" },
    { to: "/admin/events", label: "Events", icon: "🗒" },
    { to: "/admin/colleges", label: "Colleges", icon: "🎓" },
    { to: "/admin/categories", label: "Categories", icon: "🏷" },
    { to: "/admin/reports", label: "Reports", icon: "🚩" },
  ],
};

export function AppLayout() {
  const { user, logout } = useAuth();
  const items = nav[user.role] || [];
  return (
    <div className="flex min-h-screen">
      <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-r border-line bg-surface md:flex">
        <Link to="/" className="flex h-16 items-center gap-2 border-b border-line px-5">
          <img src="/favicon.svg" alt="" className="h-8 w-8" />
          <span className="text-lg font-extrabold tracking-tight">Event<span className="text-primary">Nest</span></span>
        </Link>
        <nav className="flex-1 space-y-0.5 overflow-y-auto p-3">
          {items.map((i) => (
            <NavLink
              key={i.to}
              to={i.to}
              end={i.to === "/dashboard" || i.to === "/organizer/dashboard" || i.to === "/admin/dashboard"}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-ctl px-3 py-2.5 text-sm font-semibold transition-colors ${
                  isActive ? "bg-primary-soft text-primary" : "text-ink-soft hover:bg-canvas hover:text-ink"
                }`
              }
            >
              <span className="w-5 text-center">{i.icon}</span>
              {i.label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-line p-3">
          <button onClick={() => logout()} className="block w-full rounded-ctl px-3 py-2 text-left text-sm font-semibold text-danger hover:bg-danger-soft">
            Sign out
          </button>
        </div>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-40 flex h-16 items-center gap-4 border-b border-line bg-surface/90 px-4 backdrop-blur md:px-8">
          <Link to="/" className="flex items-center gap-2 md:hidden">
            <img src="/favicon.svg" alt="" className="h-7 w-7" />
          </Link>
          <Link to="/events" className="ml-auto text-sm font-semibold text-ink-soft hover:text-ink">Browse events</Link>
          <NotificationBell />
          <ProfileMenuLabel user={user} />
        </header>
        <main className="flex-1 px-4 py-6 md:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}