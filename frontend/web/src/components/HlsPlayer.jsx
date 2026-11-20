import Hls from "hls.js";
import { useEffect, useRef } from "react";
import { absolute, tokens } from "../api/client";

/**
 * Adaptive streaming player. hls.js picks 360p or 720p segments based on measured bandwidth.
 * Safari plays HLS natively; other browsers use hls.js (Media Source Extensions).
 * Playlists come through media-service (which signs each segment URL), so playlist requests
 * carry the access token for private videos; segment URLs are already pre-signed.
 */
export default function HlsPlayer({ src, poster, startAt = 0, autoPlay = false, onProgress, className = "" }) {
  const video = useRef(null);

  useEffect(() => {
    const el = video.current;
    if (!el || !src) return undefined;
    const url = absolute(src);
    let hls;
    if (Hls.isSupported()) {
      hls = new Hls({
        xhrSetup: (xhr, reqUrl) => {
          if (reqUrl.includes("/api/media/") && tokens.access) xhr.setRequestHeader("Authorization", `Bearer ${tokens.access}`);
        },
      });
      hls.loadSource(url);
      hls.attachMedia(el);
    } else if (el.canPlayType("application/vnd.apple.mpegurl")) {
      el.src = url;
    }
    const seek = () => { if (startAt > 5 && startAt < el.duration - 5) el.currentTime = startAt; };
    el.addEventListener("loadedmetadata", seek, { once: true });
    return () => hls?.destroy();
  }, [src, startAt]);

  // Report progress every 10 s of playback (counts the view once, saves the resume position).
  useEffect(() => {
    const el = video.current;
    if (!el || !onProgress) return undefined;
    let last = -1;
    const tick = () => {
      const t = Math.floor(el.currentTime);
      if (t >= 3 && (last < 0 || t - last >= 10)) { last = t; onProgress(t); }
    };
    el.addEventListener("timeupdate", tick);
    return () => el.removeEventListener("timeupdate", tick);
  }, [onProgress]);

  return (
    <video ref={video} poster={poster} controls playsInline autoPlay={autoPlay}
      className={`aspect-video w-full rounded-2xl bg-black ${className}`} />
  );
}
