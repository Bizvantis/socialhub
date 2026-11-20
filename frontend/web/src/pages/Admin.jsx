import { useState } from "react";
import { Link } from "react-router";
import { Moderation } from "../api/endpoints";
import { ErrorBox, Spinner, Tabs, UserName } from "../components/ui";
import { timeAgo, useQuery } from "../lib/hooks";

function targetLink(type, id) {
  return { VIDEO: `/watch/${id}`, POST: `/p/${id}` }[type];
}

/** Moderation dashboard (ADMIN role; the API enforces it too). */
export default function Admin() {
  const [tab, setTab] = useState("queue");
  const stats = useQuery(() => Moderation.stats(), []);
  return (
    <div className="grid gap-5">
      <h1 className="text-2xl font-bold">Moderation</h1>
      {stats.data && (
        <div className="grid gap-3 sm:grid-cols-3">
          <Stat label="Open reports" value={stats.data.openReports} />
          <Stat label="Actions in the last 24 h" value={stats.data.actionsLast24h} />
          <Stat label="Top reason" value={Object.keys(stats.data.openByReason)[0]?.toLowerCase() ?? "-"} />
        </div>
      )}
      <Tabs value={tab} onChange={setTab} tabs={[{ value: "queue", label: "Report queue" }, { value: "log", label: "Audit log" }]} />
      {tab === "queue" ? <Queue onChanged={stats.reload} /> : <AuditLog />}
    </div>
  );
}

function Stat({ label, value }) {
  return <div className="card p-4"><p className="text-xs uppercase tracking-wide text-zinc-500">{label}</p><p className="text-2xl font-bold">{value}</p></div>;
}

function Queue({ onChanged }) {
  const q = useQuery(() => Moderation.queue(), []);
  const [error, setError] = useState(null);
  if (q.loading) return <Spinner />;
  if (q.error) return <ErrorBox error={q.error} onRetry={q.reload} />;
  if (!q.data.length) return <p className="py-10 text-center text-sm text-zinc-500">The queue is empty. 🎉</p>;

  async function act(fn) {
    setError(null);
    try {
      await fn();
      q.reload();
      onChanged();
    } catch (e) {
      setError(e);
    }
  }

  return (
    <div className="grid gap-3">
      <ErrorBox error={error} />
      {q.data.map((item) => {
        const reason = () => window.prompt("Reason (shown to the owner)", item.reasons[0]?.toLowerCase());
        const to = targetLink(item.targetType, item.targetId);
        return (
          <div key={`${item.targetType}-${item.targetId}`} className="card flex flex-wrap items-center gap-4 p-4">
            <div className="min-w-0 flex-1 text-sm">
              <p className="font-semibold">
                {item.targetType} #{item.targetId} {to && <Link to={to} className="ml-1 text-violet-brand">open ↗</Link>}
              </p>
              <p className="text-zinc-500">Owner <UserName userId={item.targetOwnerId} /> · {item.openReports} report{item.openReports > 1 ? "s" : ""} · {item.reasons.join(", ").toLowerCase()} · first {timeAgo(item.firstReportedAt)} ago</p>
            </div>
            <div className="flex flex-wrap gap-2">
              <button className="btn-secondary" onClick={() => act(() => Moderation.dismiss(item.targetType, item.targetId, "No violation"))}>Dismiss</button>
              {item.targetType !== "USER" && (
                <button className="btn bg-red-600 text-white hover:bg-red-700" onClick={() => { const r = reason(); if (r !== null) act(() => Moderation.remove(item.targetType, item.targetId, r)); }}>Remove</button>
              )}
              <button className="btn bg-zinc-900 text-white dark:bg-white dark:text-zinc-900" onClick={() => {
                const r = window.prompt("Suspend the owner? Reason:");
                if (r !== null) act(() => Moderation.suspend(item.targetOwnerId, r));
              }}>Suspend owner</button>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function AuditLog() {
  const log = useQuery(() => Moderation.actions(), []);
  if (log.loading) return <Spinner />;
  return (
    <div className="card overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="text-xs uppercase text-zinc-500"><tr><th className="p-3">When</th><th className="p-3">Admin</th><th className="p-3">Action</th><th className="p-3">Target</th><th className="p-3">Reason</th><th className="p-3" /></tr></thead>
        <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
          {log.data?.items?.map((a) => (
            <tr key={a.id}>
              <td className="p-3 whitespace-nowrap text-zinc-500">{timeAgo(a.createdAt)} ago</td>
              <td className="p-3"><UserName userId={a.adminId} /></td>
              <td className="p-3 font-mono text-xs">{a.action}</td>
              <td className="p-3">{a.targetType} #{a.targetId}</td>
              <td className="p-3 text-zinc-500">{a.reason}</td>
              <td className="p-3">
                {a.action === "REMOVE_CONTENT" && <button className="btn-ghost text-xs" onClick={async () => { await Moderation.restore(a.targetType, a.targetId, "Restored"); log.reload(); }}>Restore</button>}
                {a.action === "SUSPEND_USER" && <button className="btn-ghost text-xs" onClick={async () => { await Moderation.reinstate(a.targetId); log.reload(); }}>Reinstate</button>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
