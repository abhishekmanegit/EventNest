import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import { ToastProvider } from "./components/ui";
import { PublicLayout } from "./components/layouts";
import { AppLayout } from "./components/layouts/AppLayout";
import { AdminOnly, GuestOnly, OrganizerOnly, ProtectedRoute, StudentOnly } from "./components/guards";

import HomePage from "./pages/home/HomePage";
import LoginPage from "./pages/auth/LoginPage";
import RegisterPage from "./pages/auth/RegisterPage";
import EventsPage from "./pages/events/EventsPage";
import EventDetailPage from "./pages/events/EventDetailPage";
import CollegesPage from "./pages/colleges/CollegesPage";
import CollegeDetailPage from "./pages/colleges/CollegeDetailPage";
import { ForbiddenPage, NotFoundPage } from "./pages/StatusPages";

import StudentDashboardPage from "./pages/student/DashboardPage";
import RegistrationsPage from "./pages/student/RegistrationsPage";
import SavedPage from "./pages/student/SavedPage";
import StudentProfilePage from "./pages/student/ProfilePage";
import NotificationsPage from "./pages/student/NotificationsPage";

import OrganizerDashboardPage from "./pages/organizer/DashboardPage";
import OrganizerEventsPage from "./pages/organizer/EventsPage";
import EventFormPage from "./pages/organizer/EventFormPage";
import RosterPage from "./pages/organizer/RosterPage";
import OrganizerProfilePage from "./pages/organizer/ProfilePage";

import AdminDashboardPage from "./pages/admin/DashboardPage";
import AdminUsersPage from "./pages/admin/UsersPage";
import AdminOrganizersPage from "./pages/admin/OrganizersPage";
import AdminEventsPage from "./pages/admin/EventsPage";
import AdminCollegesPage from "./pages/admin/CollegesPage";
import AdminCategoriesPage from "./pages/admin/CategoriesPage";
import AdminReportsPage from "./pages/admin/ReportsPage";
import AiAssistant from "./components/ai/AiAssistant";

export default function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            {/* public */}
            <Route element={<PublicLayout />}>
              <Route path="/" element={<HomePage />} />
              <Route path="/events" element={<EventsPage />} />
              <Route path="/events/:idOrSlug" element={<EventDetailPage />} />
              <Route path="/colleges" element={<CollegesPage />} />
              <Route path="/colleges/:idOrSlug" element={<CollegeDetailPage />} />
              <Route path="/403" element={<ForbiddenPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Route>

            {/* auth */}
            <Route element={<GuestOnly />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
            </Route>

            {/* any signed-in role */}
            <Route element={<ProtectedRoute />}>
              <Route element={<AppLayout />}>
                <Route path="/notifications" element={<NotificationsPage />} />
              </Route>
            </Route>

            {/* student */}
            <Route element={<ProtectedRoute />}>
              <Route element={<StudentOnly />}>
                <Route element={<AppLayout />}>
                  <Route path="/dashboard" element={<StudentDashboardPage />} />
                  <Route path="/me/registrations" element={<RegistrationsPage />} />
                  <Route path="/me/saved" element={<SavedPage />} />
                  <Route path="/profile" element={<StudentProfilePage />} />
                </Route>
              </Route>
            </Route>

            {/* organizer */}
            <Route element={<ProtectedRoute />}>
              <Route element={<OrganizerOnly />}>
                <Route element={<AppLayout />}>
                  <Route path="/organizer/dashboard" element={<OrganizerDashboardPage />} />
                  <Route path="/organizer/events" element={<OrganizerEventsPage />} />
                  <Route path="/organizer/events/create" element={<EventFormPage />} />
                  <Route path="/organizer/events/:id/edit" element={<EventFormPage />} />
                  <Route path="/organizer/events/:id/registrations" element={<RosterPage />} />
                  <Route path="/organizer/profile" element={<OrganizerProfilePage />} />
                </Route>
              </Route>
            </Route>

            {/* admin */}
            <Route element={<ProtectedRoute />}>
              <Route element={<AdminOnly />}>
                <Route element={<AppLayout />}>
                  <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
                  <Route path="/admin/users" element={<AdminUsersPage />} />
                  <Route path="/admin/organizers" element={<AdminOrganizersPage />} />
                  <Route path="/admin/events" element={<AdminEventsPage />} />
                  <Route path="/admin/colleges" element={<AdminCollegesPage />} />
                  <Route path="/admin/categories" element={<AdminCategoriesPage />} />
                  <Route path="/admin/reports" element={<AdminReportsPage />} />
                </Route>
              </Route>
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
      <AiAssistant />
    </ToastProvider>
  );
}
