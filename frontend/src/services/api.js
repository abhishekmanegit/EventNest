/**
 * Endpoint definitions, one small function per backend call (docs §7).
 * Components only ever import from here — they never construct a path or parse
 * a response shape themselves, so a backend contract change is one edit.
 */
import { apiClient } from "./apiClient";
import { toQuery } from "../utils/format";

export const api = {
  // ------------------------------------------------------------ auth (§7.1) --
  auth: {
    login: (email, password) => apiClient.post("/auth/login", { email, password }),
    registerStudent: (body) => apiClient.post("/auth/register/student", body),
    registerOrganizer: (body) => apiClient.post("/auth/register/organizer", body),
    logout: (refreshToken) => apiClient.post("/auth/logout", { refreshToken }, { skipRefresh: true }),
    me: () => apiClient.get("/auth/me"),
  },

  // ------------------------------------------------------------ discovery ---
  events: {
    home: () => apiClient.get("/events/home"),
    list: (params = {}) => apiClient.get(`/events${toQuery(params)}`),
    detail: (idOrSlug) => apiClient.get(`/events/${idOrSlug}`),
    related: (id, limit = 6) => apiClient.get(`/events/${id}/related?limit=${limit}`),
    report: (id, payload) => apiClient.post(`/events/${id}/report`, payload),
    register: (id) => apiClient.post(`/events/${id}/register`),
    cancelRegistration: (id) => apiClient.delete(`/events/${id}/register`),
    myRegistration: (id) => apiClient.get(`/events/${id}/registration`),
    bookmark: (id) => apiClient.post(`/events/${id}/bookmark`),
    unbookmark: (id) => apiClient.delete(`/events/${id}/bookmark`),
  },

  categories: (params = {}) => apiClient.get(`/categories${toQuery(params)}`),

  colleges: {
    list: (params = {}) => apiClient.get(`/colleges${toQuery(params)}`),
    detail: (idOrSlug) => apiClient.get(`/colleges/${idOrSlug}`),
    events: (id, params = {}) => apiClient.get(`/colleges/${id}/events${toQuery(params)}`),
    report: (id, payload) => apiClient.post(`/colleges/${id}/report`, payload),
    create: (body) => apiClient.post("/colleges", body),
  },

  locations: () => apiClient.get("/meta/locations"),

  // --------------------------------------------------------------- me §7.1 --
  me: {
    profile: () => apiClient.get("/me/profile"),
    updateProfile: (body) => apiClient.put("/me/profile", body),
    registrations: (params = {}) => apiClient.get(`/me/registrations${toQuery(params)}`),
    bookmarks: (params = {}) => apiClient.get(`/me/bookmarks${toQuery(params)}`),
    notifications: (params = {}) => apiClient.get(`/me/notifications${toQuery(params)}`),
    unreadCount: () => apiClient.get("/me/notifications/unread-count"),
    markRead: (id) => apiClient.patch(`/me/notifications/${id}/read`),
    markAllRead: () => apiClient.post("/me/notifications/read-all"),
  },

  // ---------------------------------------------------------- organizer §7.4 --
  organizer: {
    dashboard: () => apiClient.get("/organizer/events/dashboard"),
    events: (params = {}) => apiClient.get(`/organizer/events${toQuery(params)}`),
    event: (id) => apiClient.get(`/organizer/events/${id}`),
    createEvent: (body, publish = false) => apiClient.post(`/organizer/events?publish=${publish}`, body),
    updateEvent: (id, body) => apiClient.put(`/organizer/events/${id}`, body),
    changeStatus: (id, payload) => apiClient.patch(`/organizer/events/${id}/status`, payload),
    deleteEvent: (id) => apiClient.delete(`/organizer/events/${id}`),
    roster: (eventId, params = {}) => apiClient.get(`/organizer/events/${eventId}/registrations${toQuery(params)}`),
    rosterExportUrl: (eventId) => `/api/v1/organizer/events/${eventId}/registrations/export`,
    updateRoster: (registrationId, payload) => apiClient.patch(`/organizer/registrations/${registrationId}`, payload),
    profile: () => apiClient.get("/organizer/profile"),
    updateProfile: (body) => apiClient.put("/organizer/profile", body),
    uploadLogo: (file) => {
      const fd = new FormData();
      fd.append("file", file);
      return apiClient.post("/organizer/college/logo", fd);
    },
  },

  // -------------------------------------------------------------- admin §7.5 --
  admin: {
    stats: () => apiClient.get("/admin/stats"),
    users: (params = {}) => apiClient.get(`/admin/users${toQuery(params)}`),
    setUserStatus: (id, status) => apiClient.patch(`/admin/users/${id}/status`, { status }),
    organizers: (params = {}) => apiClient.get(`/admin/organizers${toQuery(params)}`),
    organizerApproval: (id, action, reason = "") => apiClient.patch(`/admin/organizers/${id}/approval`, { action, reason }),
    events: (params = {}) => apiClient.get(`/admin/events${toQuery(params)}`),
    eventModeration: (id, payload) => apiClient.patch(`/admin/events/${id}/moderation`, payload),
    reports: (params = {}) => apiClient.get(`/admin/reports${toQuery(params)}`),
    resolveReport: (id, payload) => apiClient.patch(`/admin/reports/${id}`, payload),
    categories: (params = {}) => apiClient.get(`/admin/categories${toQuery(params)}`),
    createCategory: (body) => apiClient.post("/admin/categories", body),
    updateCategory: (id, body) => apiClient.patch(`/admin/categories/${id}`, body),
    deleteCategory: (id) => apiClient.delete(`/admin/categories/${id}`),
    colleges: (params = {}) => apiClient.get(`/admin/colleges${toQuery(params)}`),
    createCollege: (body) => apiClient.post("/admin/colleges", body),
    updateCollege: (id, body) => apiClient.patch(`/admin/colleges/${id}`, body),
  },
};