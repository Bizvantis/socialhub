import { useParams } from "react-router";
import { Posts } from "../api/endpoints";
import { PostGrid } from "../components/grids";
import { LoadMore } from "../components/ui";
import { useCursorList, useInView } from "../lib/hooks";

export default function Hashtag() {
  const { tag } = useParams();
  const list = useCursorList((cursor) => Posts.byHashtag(tag, cursor), [tag]);
  const sentinel = useInView(() => list.loadMore());
  return (
    <div className="grid gap-5">
      <h1 className="font-serif text-3xl font-semibold tracking-tight">#{tag}</h1>
      {(list.items.length > 0 || list.done) && <PostGrid posts={list.items} empty={`No public posts with #${tag} yet`} />}
      <LoadMore list={list} sentinelRef={sentinel} />
    </div>
  );
}
