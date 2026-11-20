import { useEffect } from "react";
import { Link, useOutletContext } from "react-router";
import { Notifications } from "../api/endpoints";
import { Avatar, EmptyState, LoadMore, UserName } from "../components/ui";
import { timeAgo, useCursorList, useInView } from "../lib/hooks";
import { Icon } from "../components/icons";

const TEXT = {
  FOLLOW: "started following you",
  NEW_SUBSCRIBER: "subscribed to your channel",
  LIKE: "liked your",
  COMMENT: "commented on your",
  REPLY: "replied to your comment",
  MENTION: "mentioned you in a post",
  NEW_VIDEO: "uploaded a new video",
  CONTENT_REMOVED: "Your content was removed by moderators",
};

function link(n) {
  if (n.targetType === "VIDEO") return `/watch/${n.targetId}`;
  if (n.targetType === "POST") return `/p/${n.targetId}`;
  if (n.targetType === "CHANNEL") return "/studio";
  return null;
}

export default function NotificationsPage() {
  const { setUnread } = useOutletContext();
  const list = useCursorList((cursor) => Notifications.list(cursor), []);
  const sentinel = useInView(() => list.loadMore());

  // Opening the page marks everything read.
  useEffect(() => {
    Notifications.readAll().then(() => setUnread(0)).catch(() => {});
  }, [setUnread]);

  return (
    <div className="mx-auto grid max-w-2xl gap-4">
      <h1 className="font-serif text-3xl font-semibold tracking-tight">Notifications</h1>
      {list.done && list.items.length === 0 && <EmptyState icon="bell" title="Nothing yet">Likes, comments, follows and new uploads will show up here.</EmptyState>}
      <ul className="card divide-y divide-zinc-200 dark:divide-zinc-800">
        {list.items.map((n) => {
          const to = link(n);
          const body = (
            <div className={`flex items-start gap-3 p-4 ${n.readAt ? "" : "bg-accent-soft/60"}`}>
              {n.actorId ? <Avatar userId={n.actorId} size={40} /> : <div className="grid h-10 w-10 place-items-center rounded-full bg-zinc-200 dark:bg-zinc-800" aria-hidden="true"><Icon name="shield" size={18} /></div>}
              <div className="min-w-0 flex-1 text-sm">
                {n.actorId && <UserName userId={n.actorId} link={!to} className="mr-1" />}
                <span>{TEXT[n.type] ?? "New activity"}{["LIKE", "COMMENT"].includes(n.type) ? ` ${n.targetType?.toLowerCase()}` : ""}</span>
                {n.preview && <p className="mt-0.5 line-clamp-2 text-zinc-500">“{n.preview}”</p>}
                <p className="text-xs text-zinc-500">{timeAgo(n.createdAt)}</p>
              </div>
            </div>
          );
          return <li key={n.id}>{to ? <Link to={to} className="block hover:bg-zinc-50 dark:hover:bg-zinc-800/50">{body}</Link> : body}</li>;
        })}
      </ul>
      <LoadMore list={list} sentinelRef={sentinel} />
    </div>
  );
}
