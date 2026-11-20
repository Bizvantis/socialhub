// Small reusable UI primitives.
import { useEffect } from "react";
import { Link } from "react-router";
import { useMedia, useUsers } from "../lib/hooks";

export function Spinner({ className = "" }) {
  return (
    <div className={`flex justify-center py-8 ${className}`} role="status" aria-label="Loading">
      <span className="h-7 w-7 animate-spin rounded-full border-[3px] border-zinc-300 border-t-brand-500 dark:border-zinc-700 dark:border-t-brand-500" />
    </div>
  );
}

export function EmptyState({ icon = "✨", title, children }) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-16 text-center">
      <div className="text-4xl" aria-hidden="true">{icon}</div>
      <h3 className="text-lg font-semibold">{title}</h3>
      {children && <div className="max-w-sm text-sm text-zinc-500">{children}</div>}
    </div>
  );
}

export function ErrorBox({ error, onRetry }) {
  if (!error) return null;
  return (
    <div role="alert" className="flex items-center justify-between gap-4 rounded-xl bg-red-500/10 px-4 py-3 text-sm text-red-600 dark:text-red-400">
      <span>{error.message || String(error)}</span>
      {onRetry && <button className="btn-secondary" onClick={onRetry}>Retry</button>}
    </div>
  );
}

/** Image by media id (pre-signed URL resolved through the batching media cache). */
export function MediaImage({ id, alt = "", className = "", fallback = null }) {
  const get = useMedia([id]);
  const m = id ? get(id) : null;
  const url = m?.url || m?.thumbnailUrl;
  if (!url) return fallback ?? <div className={`animate-pulse bg-zinc-200 dark:bg-zinc-800 ${className}`} />;
  return <img src={url} alt={alt} className={`object-cover ${className}`} loading="lazy" />;
}

export function Avatar({ userId, size = 40, ring = false, className = "" }) {
  const get = useUsers([userId]);
  const u = userId ? get(userId) : null;
  const initials = (u?.displayName || u?.username || "?").slice(0, 1).toUpperCase();
  const inner = (
    <MediaImage
      id={u?.avatarMediaId}
      alt=""
      className="h-full w-full rounded-full"
      fallback={<div className="grid h-full w-full place-items-center rounded-full bg-linear-to-br from-violet-brand to-brand-500 font-semibold text-white" style={{ fontSize: size * 0.42 }}>{initials}</div>}
    />
  );
  return (
    <div className={`shrink-0 ${ring ? "gradient-ring" : ""} ${className}`} style={{ width: size, height: size }}>
      <div className={`h-full w-full overflow-hidden rounded-full ${ring ? "border-2 border-white dark:border-zinc-950" : ""}`}>{inner}</div>
    </div>
  );
}

export function UserName({ userId, className = "", link = true }) {
  const get = useUsers([userId]);
  const u = get(userId);
  const label = u ? u.username : "…";
  return link && u ? <Link to={`/u/${u.username}`} className={`font-semibold hover:underline ${className}`}>{label}</Link>
    : <span className={`font-semibold ${className}`}>{label}</span>;
}

export function Modal({ open, onClose, title, children, wide = false }) {
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => { document.removeEventListener("keydown", onKey); document.body.style.overflow = ""; };
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onMouseDown={onClose} role="dialog" aria-modal="true" aria-label={title}>
      <div className={`card max-h-[90vh] w-full overflow-auto p-5 ${wide ? "max-w-3xl" : "max-w-md"}`} onMouseDown={(e) => e.stopPropagation()}>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">{title}</h2>
          <button className="btn-ghost px-2" onClick={onClose} aria-label="Close">✕</button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Tabs({ tabs, value, onChange }) {
  return (
    <div className="flex gap-1 overflow-x-auto border-b border-zinc-200 dark:border-zinc-800" role="tablist">
      {tabs.map((t) => (
        <button key={t.value} role="tab" aria-selected={value === t.value} onClick={() => onChange(t.value)}
          className={`whitespace-nowrap border-b-2 px-4 py-2.5 text-sm font-semibold transition ${value === t.value ? "border-brand-500 text-zinc-900 dark:text-white" : "border-transparent text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200"}`}>
          {t.label}
        </button>
      ))}
    </div>
  );
}

export function LoadMore({ list, sentinelRef }) {
  if (list.error) return <ErrorBox error={list.error} onRetry={list.loadMore} />;
  return (
    <div ref={sentinelRef} className="py-4">
      {list.loading && <Spinner className="py-2" />}
      {!list.loading && !list.done && <div className="flex justify-center"><button className="btn-secondary" onClick={list.loadMore}>Load more</button></div>}
    </div>
  );
}
