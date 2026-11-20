package com.socialhub.videoworker.job;

import com.socialhub.videoworker.ffmpeg.Ffmpeg;
import com.socialhub.videoworker.storage.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * One transcode job:
 *   download original -> probe -> thumbnail -> HLS renditions + master playlist -> upload.
 * Output keys sit next to the original:  u/{owner}/{purpose}/{uuid}/thumb.jpg  and  .../hls/**
 * Re-running a job overwrites the same keys, which is what makes retries safe.
 */
@Component
public class TranscodeJob {

    public record Output(String thumbnailKey, String playbackKey, int durationSeconds, int width, int height) {}

    private static final Logger log = LoggerFactory.getLogger(TranscodeJob.class);

    private final S3Client s3;
    private final StorageProperties props;
    private final Ffmpeg ffmpeg;

    public TranscodeJob(S3Client s3, StorageProperties props, Ffmpeg ffmpeg) {
        this.s3 = s3;
        this.props = props;
        this.ffmpeg = ffmpeg;
    }

    public Output run(long mediaId, String objectKey) throws IOException, InterruptedException {
        String prefix = objectKey.substring(0, objectKey.lastIndexOf('/') + 1);
        Path work = Files.createTempDirectory("transcode-" + mediaId + "-");
        try {
            Path input = work.resolve("original" + ext(objectKey));
            s3.getObject(GetObjectRequest.builder().bucket(props.bucket()).key(objectKey).build(),
                    ResponseTransformer.toFile(input));

            long t0 = System.currentTimeMillis();
            Ffmpeg.Probe probe = ffmpeg.probe(input);

            Path thumb = work.resolve("thumb.jpg");
            ffmpeg.thumbnail(input, Math.min(1.0, probe.durationSeconds() / 3), thumb);

            List<Ffmpeg.Rendition> renditions = Ffmpeg.renditionsFor(probe);
            Path hls = work.resolve("hls");
            for (Ffmpeg.Rendition r : renditions) ffmpeg.hls(input, r, hls.resolve(r.name()));
            Files.writeString(hls.resolve("master.m3u8"), Ffmpeg.masterPlaylist(renditions), StandardCharsets.UTF_8);

            put(prefix + "thumb.jpg", thumb, "image/jpeg");
            try (Stream<Path> files = Files.walk(hls)) {
                for (Path f : files.filter(Files::isRegularFile).toList()) {
                    String rel = hls.relativize(f).toString().replace('\\', '/');
                    put(prefix + "hls/" + rel, f, rel.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t");
                }
            }
            log.info("Transcoded media {} ({}x{}, {}s, {} renditions) in {} ms", mediaId, probe.width(),
                    probe.height(), Math.round(probe.durationSeconds()), renditions.size(), System.currentTimeMillis() - t0);
            return new Output(prefix + "thumb.jpg", prefix + "hls/master.m3u8",
                    (int) Math.round(probe.durationSeconds()), probe.width(), probe.height());
        } finally {
            deleteRecursively(work);
        }
    }

    private void put(String key, Path file, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(props.bucket()).key(key).contentType(contentType).build(),
                RequestBody.fromFile(file));
    }

    private static String ext(String key) {
        int dot = key.lastIndexOf('.');
        return dot < 0 ? "" : key.substring(dot);
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // temp dir cleanup is best effort
        }
    }
}
