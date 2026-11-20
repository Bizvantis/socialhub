"""Download the free-licensed images used by the demo seeder.

Photos: Lorem Picsum (https://picsum.photos), which serves Unsplash photos under the Unsplash
License (free to use, no permission needed). Avatars: DiceBear "notionists" (CC0).
Files land in tools/seed/assets/ (git-ignored); credits.json keeps photographer names.

    python tools/seed/fetch_assets.py            # downloads only what is missing
"""
import json, pathlib, random, time, urllib.request

HERE = pathlib.Path(__file__).parent
PHOTOS = HERE / "assets" / "photos"
AVATARS = HERE / "assets" / "avatars"
UA = {"User-Agent": "socialhub-demo-seeder/1.0"}
PHOTO_COUNT = 66


def get(url, timeout=60):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout) as r:
                return r.read()
        except Exception as e:  # flaky network: retry with back-off
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)


def photos():
    PHOTOS.mkdir(parents=True, exist_ok=True)
    credits_file = HERE / "assets" / "credits.json"
    if credits_file.exists():
        catalogue = json.loads(credits_file.read_text())
    else:
        listing = []
        for page in range(1, 11):
            listing += json.loads(get(f"https://picsum.photos/v2/list?page={page}&limit=100"))
        catalogue = random.Random(20261120).sample(listing, PHOTO_COUNT)
        catalogue = [{"id": p["id"], "author": p["author"], "source": p["url"]} for p in catalogue]
        credits_file.write_text(json.dumps(catalogue, indent=1))
    for p in catalogue:
        out = PHOTOS / f"{p['id']}.jpg"
        if not out.exists():
            out.write_bytes(get(f"https://picsum.photos/id/{p['id']}/1080/1080.jpg"))
    return catalogue


def avatars(seeds):
    AVATARS.mkdir(parents=True, exist_ok=True)
    for seed in seeds:
        out = AVATARS / f"{seed}.png"
        if not out.exists():
            out.write_bytes(get(f"https://api.dicebear.com/9.x/notionists/png?seed={seed}&size=256"
                                "&backgroundColor=b6e3f4,c0aede,d1d4f9,ffd5dc,ffdfbf"))


if __name__ == "__main__":
    cat = photos()
    import seed_data
    avatars([p["username"] for p in seed_data.PERSONAS if not p.get("avatar_photo")])
    total = sum(f.stat().st_size for f in (HERE / "assets").rglob("*") if f.is_file())
    print(f"{len(cat)} photos, {total / 1e6:.1f} MB in {HERE / 'assets'}")
