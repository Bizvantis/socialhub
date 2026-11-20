import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Notifications, Search } from "../api/endpoints";
import { useEventStream } from "../lib/hooks";
import { Avatar } from "./ui";
import { Icon } from "./icons";

const NAV = [
  { to: "/", label: "Home", icon: "home", end: true },
  { to: "/explore", label: "Explore", icon: "compass" },
  { to: "/videos", label: "Videos", icon: "video" },
  { to: "/subscriptions", label: "Subscriptions", icon: "subscriptions" },
  { to: "/library", label: "Library", icon: "library" },
];

export default function Layout() {
  const { user, isAdmin } = useAuth();
  const [unread, setUnread] = useState(0);
  const [toast, setToast] = useState(null);

  useEffect(() => {
    if (user) Notifications.unread().then((r) => setUnread(r.unread)).catch(() => {});
  }, [user]);

  // Real-time: every new notification bumps the badge and shows a toast.
  useEventStream("/api/notifications/stream", !!user, (event, data) => {
    if (event !== "notification") return;
    setUnread((n) => n + 1);
    setToast(describe(data));
    setTimeout(() => setToast(null), 4000);
  });

  return (
    <div className="min-h-screen">
      <DemoNotice />
      <TopBar unread={unread} />
      <div className="mx-auto flex max-w-7xl gap-6 px-3 sm:px-5">
        <aside className="sticky top-16 hidden h-[calc(100vh-4rem)] w-52 shrink-0 py-4 lg:block">
          <nav className="flex flex-col gap-1" aria-label="Sections">
            {NAV.map((n) => <SideLink key={n.to} {...n} />)}
            {user && <SideLink to="/notifications" label="Notifications" icon="bell" badge={unread} />}
            {user && <SideLink to="/upload" label="Create" icon="create" />}
            {isAdmin && <SideLink to="/admin" label="Moderation" icon="shield" />}
          </nav>
        </aside>
        <main className="min-w-0 flex-1 pb-24 pt-4 lg:pb-10">
          <Outlet context={{ setUnread }} />
        </main>
      </div>
      <MobileNav user={user} />
      {toast && (
        <div role="status" className="fixed bottom-20 left-1/2 z-50 -translate-x-1/2 rounded-full bg-zinc-900 px-5 py-2.5 text-sm text-white shadow-lg lg:bottom-6 dark:bg-white dark:text-zinc-900">
          {toast}
        </div>
      )}
    </div>
  );
}

function describe(n) {
  return {
    FOLLOW: "Someone started following you",
    LIKE: "Someone liked your content",
    COMMENT: "New comment on your content",
    REPLY: "Someone replied to your comment",
    MENTION: "You were mentioned in a post",
    NEW_VIDEO: `New video: ${n.preview ?? ""}`,
    NEW_SUBSCRIBER: "You have a new subscriber",
    CONTENT_REMOVED: "Some of your content was removed",
  }[n.type] ?? "New notification";
}

function SideLink({ to, label, icon, end, badge }) {
  return (
    <NavLink to={to} end={end} className={({ isActive }) =>
      `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition ${isActive ? "bg-white font-semibold text-zinc-900 shadow-[0_1px_2px_rgba(0,0,0,0.06)] ring-1 ring-zinc-200 dark:bg-zinc-900 dark:text-white dark:ring-zinc-800" : "text-zinc-600 hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-900 dark:hover:text-zinc-100"}`}>
      <Icon name={icon} size={19} />
      <span className="flex-1">{label}</span>
      {badge > 0 && <span className="rounded-full bg-heart px-1.5 text-[11px] font-semibold leading-5 text-white">{badge > 99 ? "99+" : badge}</span>}
    </NavLink>
  );
}

function TopBar({ unread }) {
  const { user, logout } = useAuth();
  const [menu, setMenu] = useState(false);
  const navigate = useNavigate();
  return (
    <header className="sticky top-0 z-40 border-b border-zinc-200 bg-white/90 backdrop-blur dark:border-zinc-800 dark:bg-zinc-950/90">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-3 px-3 sm:px-5">
        <Link to="/" className="flex shrink-0 items-center gap-2">
          <img src={`${import.meta.env.BASE_URL}logo.svg`} alt="" className="h-7 w-7" />
          <span className="hidden font-serif text-[1.35rem] font-semibold tracking-tight sm:inline">SocialHub</span>
        </Link>
        <SearchBox />
        {user ? (
          <div className="flex items-center gap-1">
            <Link to="/upload" className="btn-primary hidden sm:inline-flex"><Icon name="plus" size={16} strokeWidth={2} />Create</Link>
            <Link to="/notifications" className="btn-ghost relative px-2.5" aria-label={`Notifications${unread ? `, ${unread} unread` : ""}`}>
              <Icon name="bell" />{unread > 0 && <span className="absolute -right-0.5 -top-0.5 rounded-full bg-heart px-1.5 text-[10px] font-semibold leading-4 text-white">{unread > 99 ? "99+" : unread}</span>}
            </Link>
            <div className="relative">
              <button onClick={() => setMenu((m) => !m)} aria-haspopup="menu" aria-expanded={menu} className="rounded-full">
                <Avatar userId={user.userId} size={34} />
              </button>
              {menu && (
                <div role="menu" className="card absolute right-0 mt-2 w-52 overflow-hidden py-1 shadow-xl" onMouseLeave={() => setMenu(false)}>
                  <MenuItem onClick={() => { setMenu(false); navigate(`/u/${user.username}`); }}>Your profile</MenuItem>
                  <MenuItem onClick={() => { setMenu(false); navigate("/studio"); }}>Your channel</MenuItem>
                  <MenuItem onClick={() => { setMenu(false); navigate("/library"); }}>Library</MenuItem>
                  <MenuItem onClick={() => { setMenu(false); navigate("/settings"); }}>Settings</MenuItem>
                  <MenuItem onClick={async () => { setMenu(false); await logout(); navigate("/login"); }}>Log out</MenuItem>
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="flex gap-2">
            <Link to="/login" className="btn-ghost">Log in</Link>
            <Link to="/register" className="btn-primary">Sign up</Link>
          </div>
        )}
      </div>
    </header>
  );
}

function MenuItem({ children, onClick }) {
  return <button role="menuitem" onClick={onClick} className="block w-full px-4 py-2 text-left text-sm hover:bg-zinc-100 dark:hover:bg-zinc-800">{children}</button>;
}

/** Search with debounced autocomplete (videos, users, hashtags). */
function SearchBox() {
  const [value, setValue] = useState("");
  const [items, setItems] = useState([]);
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const timer = useRef(null);

  function onChange(v) {
    setValue(v);
    clearTimeout(timer.current);
    if (v.trim().length < 2) { setItems([]); return; }
    // Debounce: wait until the user pauses typing for 200 ms before calling the API.
    timer.current = setTimeout(() => Search.autocomplete(v.trim()).then((r) => { setItems(r); setOpen(true); }).catch(() => {}), 200);
  }

  function go(item) {
    setOpen(false);
    if (!item) { if (value.trim()) navigate(`/search?q=${encodeURIComponent(value.trim())}`); return; }
    if (item.type === "VIDEO") navigate(`/watch/${item.id}`);
    else if (item.type === "USER") navigate(`/u/${item.text.replace("@", "")}`);
    else navigate(`/tags/${item.id}`);
  }

  return (
    <form className="relative mx-auto w-full max-w-xl" role="search" onSubmit={(e) => { e.preventDefault(); go(null); }}>
      <input type="search" value={value} onChange={(e) => onChange(e.target.value)} onFocus={() => items.length && setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)} placeholder="Search videos, people, #hashtags"
        className="input rounded-full bg-zinc-100/80 pl-10 dark:bg-zinc-900" aria-label="Search" aria-autocomplete="list" />
      <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-zinc-400"><Icon name="search" size={17} /></span>
      {open && items.length > 0 && (
        <ul className="card absolute left-0 right-0 top-12 z-50 overflow-hidden py-1 shadow-xl" role="listbox">
          {items.map((it) => (
            <li key={`${it.type}-${it.id}`}>
              <button type="button" onMouseDown={() => go(it)} className="flex w-full items-center gap-3 px-4 py-2 text-left text-sm hover:bg-zinc-100 dark:hover:bg-zinc-800">
                <Icon name={it.type === "VIDEO" ? "video" : it.type === "USER" ? "user" : "hash"} size={16} className="text-zinc-400" />
                <span className="truncate">{it.text}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </form>
  );
}

function MobileNav({ user }) {
  const items = [NAV[0], NAV[1], { to: user ? "/upload" : "/login", label: "Create", icon: "create" }, NAV[2], { to: user ? `/u/${user.username}` : "/login", label: "You", icon: "user" }];
  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 flex justify-around border-t border-zinc-200 bg-white/95 py-1.5 backdrop-blur lg:hidden dark:border-zinc-800 dark:bg-zinc-950/95" aria-label="Mobile">
      {items.map((n) => (
        <NavLink key={n.label} to={n.to} end={n.end} className={({ isActive }) => `flex flex-col items-center gap-0.5 px-3 py-1 text-[11px] ${isActive ? "font-semibold text-zinc-900 dark:text-white" : "text-zinc-500"}`}>
          <Icon name={n.icon} size={22} />{n.label}
        </NavLink>
      ))}
    </nav>
  );
}

/** One-line note that the content is sample data (shown on the public demo; dismissible). */
function DemoNotice() {
  const [hidden, setHidden] = useState(() => {
    try { return localStorage.getItem("demo-notice-dismissed") === "1"; } catch { return false; }
  });
  if (!import.meta.env.VITE_DEMO_NOTICE || hidden) return null;
  return (
    <div className="flex items-center justify-center gap-3 border-b border-zinc-200 bg-zinc-100 px-4 py-1.5 text-center text-xs text-zinc-600 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-400">
      <span>Portfolio demo: accounts and posts are sample data. Photos from Unsplash via Lorem Picsum, avatars by DiceBear.</span>
      <button className="font-medium text-zinc-900 underline-offset-2 hover:underline dark:text-zinc-100" onClick={() => {
        setHidden(true);
        try { localStorage.setItem("demo-notice-dismissed", "1"); } catch { /* private mode */ }
      }}>Dismiss</button>
    </div>
  );
}
