import { useState } from "react";
import { useNavigate, useParams } from "react-router";
import { useAuth } from "../auth/AuthContext";
import { Posts } from "../api/endpoints";
import { PostCard } from "../components/cards";
import Comments from "../components/Comments";
import { ErrorBox, Spinner } from "../components/ui";
import { useQuery } from "../lib/hooks";
import { ReportDialog } from "./Watch";

export default function PostPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const post = useQuery(() => Posts.get(Number(id)), [id]);
  const [report, setReport] = useState(false);

  if (post.loading) return <Spinner />;
  if (post.error) return <ErrorBox error={post.error.status === 404 ? new Error("This post is unavailable.") : post.error} />;
  const p = post.data;
  const mine = user?.userId === p.authorId;

  return (
    <div className="mx-auto grid max-w-xl gap-5">
      <PostCard post={p} />
      <div className="flex gap-2">
        {mine ? (
          <button className="btn-ghost text-red-500" onClick={async () => {
            if (window.confirm("Delete this post?")) { await Posts.remove(p.id); navigate(`/u/${user.username}`); }
          }}>Delete post</button>
        ) : <button className="btn-ghost" onClick={() => setReport(true)}>⚑ Report</button>}
      </div>
      <Comments type="POST" id={p.id} ownerId={p.authorId} />
      <ReportDialog open={report} onClose={() => setReport(false)} targetType="POST" targetId={p.id} />
    </div>
  );
}
