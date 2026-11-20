"""Generate the demo videos, reels, thumbnails, banners and story images from the photos.

Needs Pillow and ffmpeg on PATH. Output goes to tools/seed/assets/generated/ (git-ignored).
Videos are photo slideshows with a slow Ken Burns zoom and cross-fades, or text-slide explainers.

    python tools/seed/make_media.py
"""
import pathlib, shutil, subprocess, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

import seed_data as D

HERE = pathlib.Path(__file__).parent
PHOTOS = HERE / "assets" / "photos"
OUT = HERE / "assets" / "generated"
FONT_DIRS = [pathlib.Path("C:/Windows/Fonts"), pathlib.Path("/usr/share/fonts/truetype/dejavu")]


def font(size, bold=True):
    names = ["segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"] if bold else ["segoeui.ttf", "arial.ttf", "DejaVuSans.ttf"]
    for d in FONT_DIRS:
        for n in names:
            if (d / n).exists():
                return ImageFont.truetype(str(d / n), size)
    return ImageFont.load_default(size)


def photo(pid):
    return Image.open(PHOTOS / f"{pid}.jpg").convert("RGB")


def cover(img, w, h):
    """Scale and centre-crop to exactly w x h."""
    s = max(w / img.width, h / img.height)
    img = img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS)
    x, y = (img.width - w) // 2, (img.height - h) // 2
    return img.crop((x, y, x + w, y + h))


def bottom_shade(img, strength=200):
    w, h = img.size
    grad = Image.new("L", (1, h))
    for y in range(h):
        grad.putpixel((0, y), int(strength * max(0, (y - h * 0.45) / (h * 0.55)) ** 1.4))
    black = Image.new("RGB", (w, h), "black")
    return Image.composite(black, img, grad.resize((w, h)))


def title_card(pid, title, channel, size=(1280, 720)):
    img = bottom_shade(cover(photo(pid), *size))
    d = ImageDraw.Draw(img)
    f, small = font(58), font(28, bold=False)
    lines, line = [], ""
    for word in title.split():
        test = (line + " " + word).strip()
        if d.textlength(test, font=f) > size[0] - 120:
            lines.append(line); line = word
        else:
            line = test
    lines.append(line)
    y = size[1] - 70 - 70 * len(lines)
    d.text((60, y - 44), channel.upper(), font=small, fill=(255, 210, 120))
    for ln in lines:
        d.text((60, y), ln, font=f, fill="white"); y += 70
    return img


def text_slide(heading, lines, idx, total, size=(1280, 720)):
    w, h = size
    img = Image.new("RGB", size)
    top, bottom = (24, 20, 58), (91, 33, 182)          # navy -> violet (brand colours)
    for y in range(h):
        t = y / h
        ImageDraw.Draw(img).line([(0, y), (w, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)))
    d = ImageDraw.Draw(img)
    for i, r in enumerate((260, 180)):                   # soft decorative circles
        d.ellipse([w - r - 40 - i * 90, -r // 2 + i * 60, w - 40 - i * 90 + r, r + i * 60], outline=(96, 70, 170), width=2)
    first = idx == 0
    hf = font(72 if first else 60)
    words, rows, row = heading.split(), [], ""
    for word in words:                                   # wrap the heading to the slide width
        test = (row + " " + word).strip()
        if row and d.textlength(test, font=hf) > w - 260:
            rows.append(row); row = word
        else:
            row = test
    rows.append(row)
    y = (200 if first else 150) - (len(rows) - 1) * 40
    for r_ in rows:
        d.text((80, y), r_, font=hf, fill="white"); y += 88
    y += 30
    for ln in lines:
        d.text((80, y), ("" if first else "•  ") + ln, font=font(40 if not first else 36, bold=False), fill=(225, 220, 255))
        y += 70
    d.text((80, h - 70), "DEV BUILDS", font=font(24), fill=(255, 210, 120))
    d.text((w - 140, h - 70), f"{idx + 1}/{total}", font=font(24, bold=False), fill=(200, 195, 230))
    return img


def story_frame(pid, size=(1080, 1920)):
    bg = cover(photo(pid), *size).filter(ImageFilter.GaussianBlur(40))
    fg = photo(pid).resize((size[0], size[0]))
    bg.paste(fg, (0, (size[1] - size[0]) // 2))
    return bg


def slideshow(frames, out, size, seconds, fade=0.7, zoom=0.0006):
    """frames: list of PIL images already at `size`. Ken Burns zoom + cross-fades, H.264 MP4."""
    tmp = OUT / "tmp"; tmp.mkdir(parents=True, exist_ok=True)
    w, h = size
    fps, n = 30, len(frames)
    args = ["ffmpeg", "-y", "-loglevel", "error"]
    for i, f in enumerate(frames):
        p = tmp / f"{out.stem}_{i}.png"
        f.resize((w * 2, h * 2), Image.LANCZOS).save(p)   # upscale first: smooth, jitter-free zoompan
        args += ["-i", str(p)]
    chains, frames_per = [], int(seconds * fps)
    for i in range(n):
        chains.append(f"[{i}:v]zoompan=z='min(zoom+{zoom},1.15)':d={frames_per}:x='iw/2-(iw/zoom/2)'"
                      f":y='ih/2-(ih/zoom/2)':s={w}x{h}:fps={fps},setsar=1,format=yuv420p[s{i}]")
    last = "s0"
    for i in range(1, n):
        chains.append(f"[{last}][s{i}]xfade=transition=fade:duration={fade}:offset={i * (seconds - fade):.2f}[x{i}]")
        last = f"x{i}"
    total = n * seconds - (n - 1) * fade
    chains.append(f"[{last}]fade=t=in:st=0:d=0.5,fade=t=out:st={total - 0.6:.2f}:d=0.6[v]")
    args += ["-filter_complex", ";".join(chains), "-map", "[v]", "-c:v", "libx264", "-preset", "medium",
             "-crf", "22", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(out)]
    subprocess.run(args, check=True)


def main():
    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg not found on PATH")
    for sub in ("videos", "thumbs", "reels", "banners", "stories"):
        (OUT / sub).mkdir(parents=True, exist_ok=True)
    names = {u: ch[1] for u, ch in D.CHANNELS.items()}

    for v in D.VIDEOS:
        out = OUT / "videos" / f"{v['key']}.mp4"
        if "slides" in v:
            slides = [text_slide(hd, ln, i, len(v["slides"])) for i, (hd, ln) in enumerate(v["slides"])]
            thumb = slides[0]
            if not out.exists():
                slideshow(slides, out, (1280, 720), 5.0, zoom=0.0002)
        else:
            thumb = title_card(v["photos"][0], v["title"], names[v["owner"]])
            frames = [thumb] + [cover(photo(p), 1280, 720) for p in v["photos"][1:]]
            if not out.exists():
                slideshow(frames, out, (1280, 720), 4.0)
        thumb.save(OUT / "thumbs" / f"{v['key']}.jpg", quality=88)
        print("video", out.name, f"{out.stat().st_size / 1e6:.1f} MB")

    for owner, key, photos, caption, days in D.REELS:
        out = OUT / "reels" / f"{key}.mp4"
        if not out.exists():
            slideshow([cover(photo(p), 720, 1280) for p in photos], out, (720, 1280), 4.0, zoom=0.0009)
        print("reel", out.name, f"{out.stat().st_size / 1e6:.1f} MB")

    for owner, (handle, name, desc, pid) in D.CHANNELS.items():
        if pid:
            banner = cover(photo(pid), 2048, 512)
        else:
            banner = text_slide("", [], 1, 1, size=(2048, 512)).crop((0, 0, 2048, 512))
        banner.save(OUT / "banners" / f"{handle}.jpg", quality=88)

    for owner, pid, caption in D.STORIES:
        story_frame(pid).save(OUT / "stories" / f"{owner}_{pid}.jpg", quality=86)

    shutil.rmtree(OUT / "tmp", ignore_errors=True)
    total = sum(f.stat().st_size for f in OUT.rglob("*") if f.is_file())
    print(f"done: {total / 1e6:.1f} MB in {OUT}")


if __name__ == "__main__":
    main()
