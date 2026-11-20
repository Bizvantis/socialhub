import { Media } from "../api/endpoints";
import { mediaCache } from "./hooks";

/**
 * Direct-to-S3 upload in three steps:
 *   1. ask media-service for a pre-signed PUT URL (it validates type and size first);
 *   2. PUT the file straight to S3: the bytes never pass through our servers;
 *   3. tell media-service we are done; it verifies the object with HeadObject.
 * XMLHttpRequest is used for step 2 because, unlike fetch, it reports upload progress.
 */
export async function uploadFile(purpose, file, onProgress = () => {}, signal) {
  const ticket = await Media.startUpload(purpose, file);
  await new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(ticket.method, ticket.uploadUrl);
    Object.entries(ticket.headers || {}).forEach(([k, v]) => xhr.setRequestHeader(k, v));
    xhr.upload.onprogress = (e) => e.lengthComputable && onProgress(Math.round((e.loaded / e.total) * 100));
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`Upload failed (${xhr.status})`)));
    xhr.onerror = () => reject(new Error("Upload failed: network error"));
    xhr.onabort = () => reject(new DOMException("Upload cancelled", "AbortError"));
    signal?.addEventListener("abort", () => xhr.abort());
    xhr.send(file);
  });
  onProgress(100);
  const media = await Media.complete(ticket.mediaId);
  mediaCache.set(media.id, media);
  return media;
}

export const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
export const VIDEO_TYPES = ["video/mp4", "video/quicktime", "video/webm"];
