package com.socialhub.videoworker.ffmpeg;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper over the ffmpeg/ffprobe binaries.
 * Commands are argument lists (never a shell string), so file names cannot inject commands.
 */
@Component
public class Ffmpeg {

    public record Probe(double durationSeconds, int width, int height) {}

    /** One HLS rendition, e.g. 360p. */
    public record Rendition(String name, int width, int height, int bandwidth) {}

    private final String ffmpeg;
    private final String ffprobe;
    private final Duration timeout;
    private final ObjectMapper mapper;

    public Ffmpeg(@Value("${app.ffmpeg.ffmpeg-path:ffmpeg}") String ffmpeg,
                  @Value("${app.ffmpeg.ffprobe-path:ffprobe}") String ffprobe,
                  @Value("${app.ffmpeg.timeout:30m}") Duration timeout,
                  ObjectMapper mapper) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.timeout = timeout;
        this.mapper = mapper;
    }

    public Probe probe(Path input) throws IOException, InterruptedException {
        String out = run(List.of(ffprobe, "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height:format=duration", "-of", "json", input.toString()), true);
        JsonNode json = mapper.readTree(out);
        JsonNode stream = json.path("streams").path(0);
        if (stream.isMissingNode() || stream.path("width").asInt(0) == 0) {
            throw new IllegalArgumentException("No video stream found");
        }
        return new Probe(json.path("format").path("duration").asDouble(0), stream.path("width").asInt(),
                stream.path("height").asInt());
    }

    public void thumbnail(Path input, double atSeconds, Path output) throws IOException, InterruptedException {
        run(List.of(ffmpeg, "-y", "-v", "error", "-ss", String.format("%.2f", atSeconds), "-i", input.toString(),
                "-frames:v", "1", "-vf", "scale=640:-2", "-q:v", "3", output.toString()), false);
    }

    /** H.264/AAC HLS VOD rendition with 4-second segments. Audio is optional (0:a:0?). */
    public void hls(Path input, Rendition r, Path dir) throws IOException, InterruptedException {
        Files.createDirectories(dir);
        List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-y", "-v", "error", "-i", input.toString(),
                "-map", "0:v:0", "-map", "0:a:0?",
                "-vf", "scale=" + r.width() + ":" + r.height(),
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-profile:v", "main", "-pix_fmt", "yuv420p",
                "-g", "48", "-keyint_min", "48", "-sc_threshold", "0",
                "-c:a", "aac", "-b:a", "128k", "-ac", "2",
                "-hls_time", "4", "-hls_playlist_type", "vod",
                "-hls_segment_filename", dir.resolve("seg_%03d.ts").toString(),
                dir.resolve("index.m3u8").toString()));
        run(cmd, false);
    }

    /** Renditions for a source: 360p always, 720p only if the source is at least that tall. */
    public static List<Rendition> renditionsFor(Probe p) {
        List<Rendition> out = new ArrayList<>();
        out.add(scaled("360p", 360, 800_000, p));
        if (p.height() >= 720) out.add(scaled("720p", 720, 2_800_000, p));
        return out;
    }

    private static Rendition scaled(String name, int targetH, int bw, Probe p) {
        int h = Math.min(targetH, p.height()) / 2 * 2;
        int w = (int) Math.round(p.width() * (double) h / p.height() / 2.0) * 2; // keep aspect, even width
        return new Rendition(name, Math.max(w, 2), Math.max(h, 2), bw);
    }

    public static String masterPlaylist(List<Rendition> renditions) {
        StringBuilder sb = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
        for (Rendition r : renditions) {
            sb.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(r.bandwidth())
              .append(",RESOLUTION=").append(r.width()).append('x').append(r.height()).append('\n')
              .append(r.name()).append("/index.m3u8\n");
        }
        return sb.toString();
    }

    private String run(List<String> cmd, boolean captureStdout) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Path errFile = Files.createTempFile("ffmpeg", ".err");
        pb.redirectError(errFile.toFile());
        Path outFile = null;
        if (captureStdout) {
            outFile = Files.createTempFile("ffprobe", ".out");
            pb.redirectOutput(outFile.toFile());
        } else {
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        }
        Process proc = pb.start();
        try {
            if (!proc.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                throw new IOException(cmd.get(0) + " timed out after " + timeout);
            }
            if (proc.exitValue() != 0) {
                String err = Files.readString(errFile, StandardCharsets.UTF_8);
                throw new IOException(cmd.get(0) + " failed (" + proc.exitValue() + "): "
                        + err.substring(0, Math.min(err.length(), 500)));
            }
            return outFile == null ? "" : Files.readString(outFile, StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(errFile);
            if (outFile != null) Files.deleteIfExists(outFile);
        }
    }
}
