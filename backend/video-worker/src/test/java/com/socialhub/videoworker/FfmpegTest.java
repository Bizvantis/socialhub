package com.socialhub.videoworker;

import com.socialhub.videoworker.ffmpeg.Ffmpeg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FfmpegTest {

    @Test
    void lowResolutionSourceGetsOnly360pWithAspectKept() {
        List<Ffmpeg.Rendition> r = Ffmpeg.renditionsFor(new Ffmpeg.Probe(10, 854, 480));
        assertEquals(1, r.size());
        assertEquals(360, r.get(0).height());
        assertEquals(640, r.get(0).width()); // 854 * 360/480 = 640.5 -> even 640
    }

    @Test
    void hdSourceGets360pAnd720p() {
        List<Ffmpeg.Rendition> r = Ffmpeg.renditionsFor(new Ffmpeg.Probe(10, 1920, 1080));
        assertEquals(List.of("360p", "720p"), r.stream().map(Ffmpeg.Rendition::name).toList());
        assertEquals(1280, r.get(1).width());
    }

    @Test
    void tinySourceIsNeverUpscaled() {
        Ffmpeg.Rendition r = Ffmpeg.renditionsFor(new Ffmpeg.Probe(3, 320, 240)).get(0);
        assertEquals(240, r.height());
        assertEquals(320, r.width());
    }

    @Test
    void verticalReelKeepsPortraitAspect() {
        Ffmpeg.Rendition r = Ffmpeg.renditionsFor(new Ffmpeg.Probe(15, 1080, 1920)).get(0);
        assertEquals(360, r.height());
        assertEquals(202, r.width()); // 1080*360/1920 = 202.5 -> even 202
    }

    @Test
    void masterPlaylistListsEveryRendition() {
        String m = Ffmpeg.masterPlaylist(Ffmpeg.renditionsFor(new Ffmpeg.Probe(10, 1280, 720)));
        assertTrue(m.startsWith("#EXTM3U"));
        assertTrue(m.contains("RESOLUTION=640x360\n360p/index.m3u8"));
        assertTrue(m.contains("RESOLUTION=1280x720\n720p/index.m3u8"));
    }
}
