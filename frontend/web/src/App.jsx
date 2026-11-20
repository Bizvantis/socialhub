// Step 37 - React Router: one route per screen; protected routes need a signed-in user.
import { lazy, Suspense } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router";
import { useAuth } from "./auth/AuthContext";
import Layout from "./components/Layout";
import { Spinner } from "./components/ui";
import { ForgotPage, LoginPage, RegisterPage, ResetPage } from "./pages/AuthPages";

// Code splitting: each page is downloaded only when first visited.
const Home = lazy(() => import("./pages/Home"));
const Explore = lazy(() => import("./pages/Explore"));
const Videos = lazy(() => import("./pages/Videos"));
const Watch = lazy(() => import("./pages/Watch"));
const Channel = lazy(() => import("./pages/Channel"));
const Studio = lazy(() => import("./pages/Studio"));
const Profile = lazy(() => import("./pages/Profile"));
const PostPage = lazy(() => import("./pages/PostPage"));
const Hashtag = lazy(() => import("./pages/Hashtag"));
const Upload = lazy(() => import("./pages/Upload"));
const SearchPage = lazy(() => import("./pages/SearchPage"));
const NotificationsPage = lazy(() => import("./pages/NotificationsPage"));
const Library = lazy(() => import("./pages/Library"));
const PlaylistPage = lazy(() => import("./pages/PlaylistPage"));
const Subscriptions = lazy(() => import("./pages/Subscriptions"));
const Settings = lazy(() => import("./pages/Settings"));
const Admin = lazy(() => import("./pages/Admin"));

function RequireAuth({ children, admin = false }) {
  const { status, isAdmin } = useAuth();
  const location = useLocation();
  if (status === "loading") return <Spinner />;
  if (status !== "authenticated") return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (admin && !isAdmin) return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  return (
    <Suspense fallback={<Spinner />}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPage />} />
        <Route path="/reset-password" element={<ResetPage />} />

        <Route element={<Layout />}>
          {/* Public: anonymous browsing of videos, channels, hashtags, search */}
          <Route path="/videos" element={<Videos />} />
          <Route path="/watch/:id" element={<Watch />} />
          <Route path="/c/:handle" element={<Channel />} />
          <Route path="/tags/:tag" element={<Hashtag />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/playlist/:id" element={<PlaylistPage />} />

          {/* Signed-in only */}
          <Route path="/" element={<RequireAuth><Home /></RequireAuth>} />
          <Route path="/explore" element={<RequireAuth><Explore /></RequireAuth>} />
          <Route path="/u/:username" element={<RequireAuth><Profile /></RequireAuth>} />
          <Route path="/p/:id" element={<RequireAuth><PostPage /></RequireAuth>} />
          <Route path="/upload" element={<RequireAuth><Upload /></RequireAuth>} />
          <Route path="/studio" element={<RequireAuth><Studio /></RequireAuth>} />
          <Route path="/subscriptions" element={<RequireAuth><Subscriptions /></RequireAuth>} />
          <Route path="/library" element={<RequireAuth><Library /></RequireAuth>} />
          <Route path="/notifications" element={<RequireAuth><NotificationsPage /></RequireAuth>} />
          <Route path="/settings" element={<RequireAuth><Settings /></RequireAuth>} />
          <Route path="/admin" element={<RequireAuth admin><Admin /></RequireAuth>} />
          <Route path="*" element={<div className="py-20 text-center"><h1 className="font-serif text-3xl font-semibold tracking-tight">Page not found</h1></div>} />
        </Route>
      </Routes>
    </Suspense>
  );
}
