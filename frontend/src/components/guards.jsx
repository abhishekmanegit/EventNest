import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { PageLoader } from "./ui";

/** Not authed → redirect to /login keeping the intended destination. */
export function ProtectedRoute() {
  const { user, ready } = useAuth();
  const location = useLocation();
  if (!ready) return <PageLoader label="Restoring your session…" />;
  if (!user) return <Navigate to="/login" state={{ next: location.pathname + location.search }} replace />;
  return <Outlet />;
}

/** Authed but wrong role → a visible /403, never a silent redirect (docs §6.3). */
export function StudentOnly() {
  const { user } = useAuth();
  return user?.role === "STUDENT" ? <Outlet /> : <Navigate to="/403" replace />;
}

export function OrganizerOnly() {
  const { user } = useAuth();
  return user?.role === "ORGANIZER" ? <Outlet /> : <Navigate to="/403" replace />;
}

export function AdminOnly() {
  const { user } = useAuth();
  return user?.role === "ADMIN" ? <Outlet /> : <Navigate to="/403" replace />;
}

/** Redirects already-authed visitors away from /login and /register. */
export function GuestOnly() {
  const { user, ready } = useAuth();
  if (!ready) return <PageLoader label="…" />;
  if (user) return <Navigate to={homeFor(user.role)} replace />;
  return <Outlet />;
}

export function homeFor(role) {
  if (role === "ADMIN") return "/admin/dashboard";
  if (role === "ORGANIZER") return "/organizer/dashboard";
  return "/dashboard";
}