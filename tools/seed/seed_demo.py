"""Fill a SocialHub deployment with demo content, using only the public API (like a real client).

    python tools/seed/fetch_assets.py && python tools/seed/make_media.py     # once
    SEED_GUEST_PASSWORD=... python tools/seed/seed_demo.py --base https://<host>
    python tools/seed/seed_demo.py --base https://<host> --stories-only      # daily (stories expire in 24 h)

Re-runnable: progress is kept in .seed-state-<host>.json, so an interrupted run continues where it
stopped. Persona passwords are random and live only in .seed-accounts.json (git-ignored).
After seeding, run backdate.py on the server to spread the posts over the last few weeks.
"""
import argparse, json, mimetypes, os, pathlib, random, secrets, sys, time, urllib.error, urllib.parse, urllib.request

import seed_data as D

HERE = pathlib.Path(__file__).parent
ASSETS = HERE / "assets"
GEN = ASSETS / "generated"
ACCOUNTS_FILE = HERE / ".seed-accounts.json"
rng = random.Random(7)


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.tokens = {}

    def raw(self, method, url, body=None, token=None, headers=None, raw=False):
        h = dict(headers or {})
        if body is not None and not raw:
            h["Content-Type"] = "application/json"
        if token:
            h["Authorization"] = "Bearer " + token
        data = body if raw else (json.dumps(body).encode() if body is not None else None)
        req = urllib.request.Request(url if url.startswith("http") else self.base + url, data=data, method=method, headers=h)
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                txt = r.read()
                return r.status, (json.loads(txt) if txt and "json" in r.headers.get("Content-Type", "") else None)
        except urllib.error.HTTPError as e:
            txt = e.read()
            try:
                return e.code, json.loads(txt)
            except Exception:
                return e.code, txt[:300]

    def call(self, method, path, body=None, user=None, ok=(200, 201, 204), quiet=False):
        """JSON API call as `user` with retries for rate limits / restarts and re-login on 401."""
        for attempt in range(8):
            token = self.token(user) if user else None
            s, r = self.raw(method, path, body, token)
            if s in ok:
                return r.get("data") if isinstance(r, dict) else r
            if s == 401 and user:
                self.tokens.pop(user, None)
            elif s in (429, 502, 503, 504):
                time.sleep(min(2 ** attempt * 0.5, 15))
            else:
                if quiet:
                    return None
                raise RuntimeError(f"{method} {path} as {user}: {s} {r}")
        raise RuntimeError(f"{method} {path} as {user}: gave up after retries")

    def token(self, user):
        if user not in self.tokens:
            acc = ACCOUNTS[user]
            d = self.call("POST", "/api/auth/login", {"emailOrUsername": user, "password": acc["password"]})
            self.tokens[user] = d["accessToken"]
        return self.tokens[user]

    def upload(self, user, purpose, path):
        data = pathlib.Path(path).read_bytes()
        ctype = mimetypes.guess_type(str(path))[0]
        t = self.call("POST", "/api/media/uploads",
                      {"purpose": purpose, "contentType": ctype, "sizeBytes": len(data), "fileName": pathlib.Path(path).name}, user)
        for attempt in range(5):
            s, r = self.raw(t.get("method", "PUT"), t["uploadUrl"], data, headers={**(t.get("headers") or {}), "Content-Type": ctype}, raw=True)
            if 200 <= s < 300:
                break
            time.sleep(2 ** attempt)
        else:
            raise RuntimeError(f"S3 upload failed {s} {r}")
        self.call("POST", f"/api/media/{t['mediaId']}/complete", user=user)
        return t["mediaId"]


class State:
    def __init__(self, base):
        host = urllib.parse.urlparse(base).netloc.replace(":", "_")
        self.file = HERE / f".seed-state-{host}.json"
        self.d = json.loads(self.file.read_text()) if self.file.exists() else {}

    def get(self, key):
        return self.d.get(key)

    def put(self, key, value):
        self.d[key] = value
        self.file.write_text(json.dumps(self.d, indent=1))
        return value

    def once(self, key, fn):
        """Run fn() only if `key` was not done before; remembers the result."""
        if key in self.d:
            return self.d[key]
        return self.put(key, fn())


def load_accounts(guest_password):
    accounts = json.loads(ACCOUNTS_FILE.read_text()) if ACCOUNTS_FILE.exists() else {}
    for u in [p["username"] for p in D.PERSONAS] + D.LURKERS:
        accounts.setdefault(u, {"password": secrets.token_urlsafe(18)})
    if guest_password:
        accounts["guest"] = {"password": guest_password}
    ACCOUNTS_FILE.write_text(json.dumps(accounts, indent=1))
    return accounts


def display_name(username):
    return " ".join(w.capitalize() for w in username.split("_"))


def register_all(api, st, users):
    for u in users:
        def reg(u=u):
            body = {"email": f"{u}@demo.socialhub.app", "username": u, "password": ACCOUNTS[u]["password"]}
            for attempt in range(10):
                s, r = api.raw("POST", "/api/auth/register", body)
                if s not in (429, 502, 503, 504):
                    break
                time.sleep(min(1.5 * (attempt + 1), 10))
            if s not in (201, 409):
                raise RuntimeError(f"register {u}: {s} {r}")
            return api.call("GET", "/api/users/me", user=u)["userId"]
        st.once(f"user:{u}", reg)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default=os.environ.get("GW", "http://localhost"))
    ap.add_argument("--stories-only", action="store_true")
    args = ap.parse_args()

    global ACCOUNTS
    guest_pw = os.environ.get("SEED_GUEST_PASSWORD")
    ACCOUNTS = load_accounts(guest_pw)
    api, st = Api(args.base), State(args.base)

    if args.stories_only:
        if "guest" in ACCOUNTS:   # undo anything a visitor changed on the shared demo profile
            api.call("PUT", "/api/users/me", {"displayName": D.GUEST["displayName"], "bio": D.GUEST["bio"],
                                              "privateAccount": False}, "guest", quiet=True)
        return stories(api, st, fresh=True)

    personas = [p["username"] for p in D.PERSONAS]
    users = personas + D.LURKERS + (["guest"] if "guest" in ACCOUNTS else [])
    print(f"seeding {args.base} ({len(users)} accounts)")
    register_all(api, st, users)
    uid = {u: st.get(f"user:{u}") for u in users}

    # profiles and avatars
    for p in D.PERSONAS:
        def profile(p=p):
            avatar = api.upload(p["username"], "AVATAR", ASSETS / "avatars" / f"{p['username']}.png")
            api.call("PUT", "/api/users/me", {"displayName": p["displayName"], "bio": p["bio"], "avatarMediaId": avatar}, p["username"])
            return avatar
        st.once(f"profile:{p['username']}", profile)
    for u in D.LURKERS:
        st.once(f"profile:{u}", lambda u=u: api.call("PUT", "/api/users/me", {"displayName": display_name(u)}, u) and True)
    if "guest" in ACCOUNTS:
        st.once("profile:guest", lambda: api.call("PUT", "/api/users/me", {"displayName": D.GUEST["displayName"], "bio": D.GUEST["bio"]}, "guest") and True)
    print("profiles done")

    # follow graph (before content, so posts fan out into home feeds)
    def follow(a, b):
        st.once(f"follow:{a}>{b}", lambda: api.call("POST", f"/api/users/{uid[b]}/follow", user=a) or True)
    for a in personas:
        for b in rng.sample([x for x in personas if x != a], rng.randint(4, 7)):
            follow(a, b)
    for a in D.LURKERS:
        for b in rng.sample(personas, rng.randint(3, 8)):
            follow(a, b)
        if rng.random() < 0.3:
            follow(a, rng.choice([x for x in D.LURKERS if x != a]))
    if "guest" in ACCOUNTS:
        for b in personas:
            follow("guest", b)
    print("follows done")

    # channels
    channel = {}
    for owner, (handle, name, desc, pid) in D.CHANNELS.items():
        def make(owner=owner, handle=handle, name=name, desc=desc):
            cid = api.call("POST", "/api/channels", {"handle": handle, "name": name, "description": desc}, owner)["id"]
            avatar = api.upload(owner, "AVATAR", ASSETS / "avatars" / f"{owner}.png")
            banner = api.upload(owner, "BANNER", GEN / "banners" / f"{handle}.jpg")
            api.call("PATCH", f"/api/channels/{cid}", {"avatarMediaId": avatar, "bannerMediaId": banner}, owner)
            return cid
        channel[owner] = st.once(f"channel:{owner}", make)
    subscribers = personas + D.LURKERS + (["guest"] if "guest" in ACCOUNTS else [])
    for owner, cid in channel.items():
        for u in subscribers:
            if u != owner and (u == "guest" or rng.random() < 0.45):
                st.once(f"sub:{u}>{cid}", lambda u=u, cid=cid: api.call("POST", f"/api/channels/{cid}/subscribe", user=u) or True)
    print("channels done")

    # content, oldest first so feeds and "recent" sorts line up with the backdated times
    items = [("post", i, p[3]) for i, p in enumerate(D.POSTS)] + \
            [("video", i, v["days"]) for i, v in enumerate(D.VIDEOS)] + \
            [("reel", i, r[4]) for i, r in enumerate(D.REELS)]
    items.sort(key=lambda x: -x[2])
    theme = {p["username"]: p["theme"] for p in D.PERSONAS}
    backdate = st.get("backdate") or {"POST": {}, "VIDEO": {}, "COMMENT": {}}
    video_engagement = []
    for kind, i, days in items:
        if kind == "post":
            owner, photos, caption, _ = D.POSTS[i]
            def mk(owner=owner, photos=photos, caption=caption):
                ids = [api.upload(owner, "POST_IMAGE", ASSETS / "photos" / f"{p}.jpg") for p in photos]
                return api.call("POST", "/api/posts", {"type": "IMAGE" if len(ids) == 1 else "CAROUSEL", "mediaIds": ids,
                                                       "caption": caption, "visibility": "PUBLIC"}, owner)["id"]
            target, tid = "POST", st.once(f"post:{i}", mk)
        elif kind == "reel":
            owner, key, photos, caption, _ = D.REELS[i]
            def mk(owner=owner, key=key, caption=caption):
                mid = api.upload(owner, "REEL", GEN / "reels" / f"{key}.mp4")
                return api.call("POST", "/api/posts", {"type": "REEL", "mediaIds": [mid], "caption": caption, "visibility": "PUBLIC"}, owner)["id"]
            target, tid = "POST", st.once(f"reel:{key}", mk)
        else:
            v = D.VIDEOS[i]; owner = v["owner"]
            def mk(v=v):
                mid = api.upload(v["owner"], "VIDEO", GEN / "videos" / f"{v['key']}.mp4")
                thumb = api.upload(v["owner"], "THUMBNAIL", GEN / "thumbs" / f"{v['key']}.jpg")
                return api.call("POST", "/api/videos", {"mediaId": mid, "title": v["title"], "description": v["description"],
                                                        "tags": v["tags"], "category": v["category"], "visibility": "PUBLIC",
                                                        "thumbnailMediaId": thumb}, v["owner"])["id"]
            target, tid = "VIDEO", st.once(f"video:{v['key']}", mk)
        backdate[target][str(tid)] = days
        if kind in ("video", "reel"):
            video_engagement.append((target, tid, owner, days))   # only possible once transcoding is done
        else:
            engage(api, st, target, tid, owner, theme[owner], days, uid, backdate)
        st.put("backdate", backdate)
        print(f"  {kind:5} {tid:>4}  {owner:18} {days:>5} days ago")

    stories(api, st, fresh=False)
    library(api, st)
    wait_and_view(api, st)
    wait_reels(api, st)
    for target, tid, owner, days in video_engagement:
        engage(api, st, target, tid, owner, theme[owner], days, uid, backdate)
        st.put("backdate", backdate)
    print("video likes and comments done")
    print("\nseed complete. Next: run backdate.py on the server (uses", st.file.name, ")")


def engage(api, st, target, tid, owner, theme, days, uid, backdate):
    """Likes, comments and replies from other accounts, with comment times shortly after the post."""
    others = [p["username"] for p in D.PERSONAS if p["username"] != owner]
    likers = rng.sample(others, rng.randint(3, len(others))) + rng.sample(D.LURKERS, rng.randint(4, 18))
    if "guest" in ACCOUNTS and rng.random() < 0.25:
        likers.append("guest")
    for u in likers:
        st.once(f"like:{u}>{target}{tid}", lambda u=u: api.call("PUT", "/api/reactions", {"targetType": target, "targetId": tid, "kind": "LIKE"}, u) or True)
    pool = D.COMMENTS[theme] + (D.VIDEO_COMMENTS if target == "VIDEO" else [])
    commenters = rng.sample(others + D.LURKERS[:10], rng.randint(2, 5))
    texts = rng.sample(pool, min(len(commenters), len(pool)))
    for n, (u, text) in enumerate(zip(commenters, texts)):
        def mk(u=u, text=text):
            return api.call("POST", "/api/comments", {"targetType": target, "targetId": tid, "body": text}, u)["id"]
        cid = st.once(f"comment:{target}{tid}:{n}", mk)
        backdate["COMMENT"][str(cid)] = max(days - 0.02 * (n + 1) - rng.random() * 0.3, 0.01)
        if rng.random() < 0.4:
            st.once(f"clike:{owner}>{cid}", lambda cid=cid: api.call("PUT", "/api/reactions", {"targetType": "COMMENT", "targetId": cid, "kind": "LIKE"}, owner) or True)
        if rng.random() < 0.35:
            rid = st.once(f"reply:{cid}", lambda cid=cid: api.call("POST", "/api/comments", {"targetType": target, "targetId": tid,
                          "body": f"@{u} " + rng.choice(D.AUTHOR_REPLIES), "parentId": cid}, owner)["id"])
            backdate["COMMENT"][str(rid)] = max(backdate["COMMENT"][str(cid)] - 0.05, 0.005)


def stories(api, st, fresh):
    """Stories expire after 24 h, so a daily job posts them again (fresh=True)."""
    day = time.strftime("%Y-%m-%d")
    viewers = D.LURKERS + [p["username"] for p in D.PERSONAS]
    for owner, pid, caption in D.STORIES:
        key = f"story:{owner}:{pid}:{day}"
        if st.get(key):
            continue
        mid = api.upload(owner, "STORY_IMAGE", GEN / "stories" / f"{owner}_{pid}.jpg")
        sid = api.call("POST", "/api/stories", {"mediaId": mid, "caption": caption}, owner)["id"]
        for u in rng.sample([v for v in viewers if v != owner], rng.randint(5, 15)):
            api.call("POST", f"/api/stories/{sid}/view", user=u, quiet=True)
        st.put(key, sid)
    print("stories posted for", day)


def library(api, st):
    """Give the guest account some saved items so its library is not empty."""
    if "guest" not in ACCOUNTS:
        return
    posts = [st.get(f"post:{i}") for i in rng.sample(range(len(D.POSTS)), 6)]
    for pid in posts:
        st.once(f"save:guest>{pid}", lambda pid=pid: api.call("POST", "/api/bookmarks", {"targetType": "POST", "targetId": pid}, "guest") or True)


def wait_reels(api, st):
    pending = {st.get(f"reel:{r[1]}") for r in D.REELS}
    deadline = time.time() + 1200
    while pending and time.time() < deadline:
        for pid in list(pending):
            p = api.call("GET", f"/api/posts/{pid}", user=D.LURKERS[0], quiet=True)
            if p and p.get("status") in ("READY", "FAILED"):
                pending.discard(pid)
        if pending:
            print(f"  waiting for {len(pending)} reel(s) to finish transcoding...")
            time.sleep(10)
    time.sleep(31)   # engagement-service caches a target's state for 30 s


def wait_and_view(api, st):
    """Wait for transcoding, then record views (only READY videos count)."""
    vids = [st.get(f"video:{v['key']}") for v in D.VIDEOS]
    deadline = time.time() + 1800
    pending = set(vids)
    while pending and time.time() < deadline:
        for vid in list(pending):
            v = api.call("GET", f"/api/videos/{vid}", user="guest" if "guest" in ACCOUNTS else D.LURKERS[0], quiet=True)
            if v and v.get("status") == "READY":
                pending.discard(vid)
            elif v and v.get("status") == "FAILED":
                print("  video", vid, "FAILED to transcode"); pending.discard(vid)
        if pending:
            print(f"  waiting for {len(pending)} video(s) to finish transcoding...")
            time.sleep(15)
    viewers = D.LURKERS + [p["username"] for p in D.PERSONAS]
    for vid in vids:
        for u in rng.sample(viewers, rng.randint(12, len(viewers))):
            st.once(f"view:{u}>{vid}", lambda u=u, vid=vid: api.call("POST", f"/api/videos/{vid}/views",
                    {"watchedSeconds": rng.randint(10, 40)}, u, quiet=True) or True)
    print("views recorded")


if __name__ == "__main__":
    main()
