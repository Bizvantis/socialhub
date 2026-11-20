"""Spread seeded demo content over the past weeks (run on the Docker host after seed_demo.py).

The public API always stamps "now", so a freshly seeded site would say "posted just now" everywhere.
This rewrites the timestamps consistently in every place that stores them:
  - MySQL: post_db.posts, video_db.videos, engagement_db.comments (source of truth, shown in the UI)
  - Redis (feed-service): item snapshots and the score of every timeline / home-feed sorted set
  - Elasticsearch: createdAt / publishedAt used by "sort by date" in search

    python3 tools/seed/backdate.py --state tools/seed/.seed-state-<host>.json \
        --compose "-f docker-compose.yml -f docker-compose.full.yml"

Needs MYSQL_ROOT_PASSWORD and REDIS_PASSWORD in the environment (source .env first).
Idempotent: running it twice gives the same result relative to the time recorded on the first run.
"""
import argparse, datetime as dt, json, os, pathlib, shlex, subprocess

LUA_RESCORE = """
local n, cursor = 0, '0'
repeat
  local r = redis.call('SCAN', cursor, 'MATCH', ARGV[1], 'COUNT', 1000)
  cursor = r[1]
  for _, k in ipairs(r[2]) do
    if redis.call('TYPE', k).ok == 'zset' then
      for i = 2, #ARGV, 2 do n = n + redis.call('ZADD', k, 'XX', 'CH', ARGV[i + 1], ARGV[i]) end
    end
  end
until cursor == '0'
return n
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--state", required=True)
    ap.add_argument("--compose", default="-f docker-compose.yml -f docker-compose.full.yml")
    args = ap.parse_args()

    state_file = pathlib.Path(args.state)
    state = json.loads(state_file.read_text())
    plan = state["backdate"]
    # Anchor "now" on the first run so re-runs are stable.
    anchor = state.get("backdate_anchor") or dt.datetime.now(dt.timezone.utc).isoformat()
    state["backdate_anchor"] = anchor
    state_file.write_text(json.dumps(state, indent=1))
    now = dt.datetime.fromisoformat(anchor)

    def when(days):
        return now - dt.timedelta(days=float(days))

    compose = ["docker", "compose"] + shlex.split(args.compose)

    def exec_in(service, cmd, stdin=None):
        r = subprocess.run(compose + ["exec", "-T", service] + cmd, input=stdin, capture_output=True, text=True, encoding="utf-8")
        if r.returncode != 0:
            raise SystemExit(f"{service}: {r.stderr.strip() or r.stdout.strip()}")
        return r.stdout

    # 1. MySQL
    sql = ["SET time_zone = '+00:00';"]
    for pid, days in plan["POST"].items():
        sql.append(f"UPDATE post_db.posts SET created_at = '{when(days):%Y-%m-%d %H:%M:%S.%f}' WHERE id = {int(pid)};")
    for vid, days in plan["VIDEO"].items():
        t = f"{when(days):%Y-%m-%d %H:%M:%S.%f}"
        sql.append(f"UPDATE video_db.videos SET created_at = '{t}', published_at = '{t}' WHERE id = {int(vid)};")
    for cid, days in plan["COMMENT"].items():
        sql.append(f"UPDATE engagement_db.comments SET created_at = '{when(days):%Y-%m-%d %H:%M:%S.%f}' WHERE id = {int(cid)};")
    exec_in("mysql", ["sh", "-c", 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" 2>/dev/null'], "\n".join(sql))
    print(f"mysql: {len(sql) - 1} rows")

    # 2. Redis (feed-service)
    ms = {f"P:{k}": int(when(v).timestamp() * 1000) for k, v in plan["POST"].items()}
    ms.update({f"V:{k}": int(when(v).timestamp() * 1000) for k, v in plan["VIDEO"].items()})
    cli = ["redis-cli", "-a", os.environ["REDIS_PASSWORD"], "--no-auth-warning"]
    pairs = [x for member, score in ms.items() for x in (member, str(score))]
    changed = sum(int(exec_in("redis", cli + ["EVAL", LUA_RESCORE, "0", pattern] + pairs).strip() or 0)
                  for pattern in ("tl:*", "feed:*"))
    keys = list(ms)
    raw = exec_in("redis", cli + ["--raw", "MGET"] + [f"item:{k}" for k in keys]).splitlines()
    sets = []
    for key, line in zip(keys, raw):
        if not line.strip():
            continue
        snap = json.loads(line)
        snap["createdAt"] = ms[key]
        text = json.dumps(snap, ensure_ascii=True, separators=(",", ":"))
        sets.append(f'SET item:{key} "' + text.replace("\\", "\\\\").replace('"', '\\"') + '" KEEPTTL')
    if sets:
        exec_in("redis", cli, "\n".join(sets) + "\n")
    print(f"redis: {changed} timeline scores, {len(sets)} item snapshots")

    # 3. Elasticsearch
    bulk = []
    for pid, days in plan["POST"].items():
        bulk += [json.dumps({"update": {"_index": "sh_posts", "_id": pid}}),
                 json.dumps({"doc": {"createdAt": when(days).isoformat().replace("+00:00", "Z")}})]
    for vid, days in plan["VIDEO"].items():
        bulk += [json.dumps({"update": {"_index": "sh_videos", "_id": vid}}),
                 json.dumps({"doc": {"publishedAt": when(days).isoformat().replace("+00:00", "Z")}})]
    out = exec_in("elasticsearch", ["curl", "-s", "-XPOST", "localhost:9200/_bulk?refresh=true",
                                    "-H", "Content-Type: application/x-ndjson", "--data-binary", "@-"], "\n".join(bulk) + "\n")
    items = json.loads(out).get("items", [])
    ok = sum(1 for i in items if i["update"].get("status") in (200, 201))
    print(f"elasticsearch: {ok}/{len(items)} documents")


if __name__ == "__main__":
    main()
