package dev.goldberry.media.view;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.goldberry.Application;
import dev.goldberry.Goldberry;
import dev.goldberry.Host;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.media.VideoStatistics;
import dev.goldberry.media.io.Source;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.image.Fit;

/// The 4K60 run: a minute of 4K60 VP9 decoded on the platform's video engine
/// and shown by a `video-view` in a real window, through the GPU.
///
/// Plays the clip `media/src/test/fixtures/make-4k60.sh` makes to its end, then
/// prints what happened to its pictures ([VideoStatistics]), which decoder and
/// form played them, and the CPU time the Engine's threads and the UI thread
/// spent per picture shown. The launcher's own `frames:` and `presents:` lines
/// follow as the window closes. Run by `:media:videoPresentProbe` on the first
/// thread, and read by a person; not part of `check`.
///
/// `-Pgoldberry.probe.clip=<file>` plays another clip; `-Pgoldberry.gpu=off`
/// runs the same minute on CPU present, for the comparison.
final class VideoPresentProbe implements Application {

    private static final String DEFAULT_CLIP = "build/probe/clip-4k60-vp9.webm";

    private final Path clip;
    private final MediaPlayer player;
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final Map<String, Long> cpuAtStart = new HashMap<>();
    private volatile long uiThreadId;
    private volatile boolean failed;

    private VideoPresentProbe(Path clip) {
        this.clip = clip;
        this.player =
                MediaPlayer.builder().hardwareDecoding(HardwareDecoding.AUTO).build();
    }

    static void main(String[] args) {
        var clip = Path.of(System.getProperty("goldberry.probe.clip", DEFAULT_CLIP))
                .toAbsolutePath();
        if (!Files.isRegularFile(clip)) {
            throw new IllegalStateException(
                    "no clip at " + clip + "; make one with media/src/test/fixtures/make-4k60.sh");
        }
        var probe = new VideoPresentProbe(clip);
        Goldberry.launch(probe, args);
        if (probe.failed) {
            System.exit(1);
        }
    }

    @Override
    public Widget root() {
        return new Column(List.of(new VideoView(player, Fit.CONTAIN)), Attributes.NONE.id("stage"));
    }

    @Override
    public String title() {
        return "goldberry: 4K60 video present probe";
    }

    @Override
    public LogicalSize size() {
        return new LogicalSize(1280, 720);
    }

    @Override
    public List<Stylesheet> stylesheets() {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(MediaStyles.stylesheet());
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, "#stage { flex-grow: 1; } video-view { flex-grow: 1; }"));
        return sheets;
    }

    @Override
    public void start(Host host) {
        uiThreadId = Thread.currentThread().threadId();
        player.open(Source.of(clip));
        Thread.ofPlatform().daemon().name("goldberry-probe-watch").start(this::watch);
    }

    @Override
    public void stop() {
        player.close();
    }

    /// Waits for playback to start, takes the threads' CPU times, waits for the
    /// end, and reports.
    private void watch() {
        if (!awaitState(PlaybackState.PLAYING, Duration.ofSeconds(30))) {
            System.out.println("the clip never played: " + player.status());
            failed = true;
            Goldberry.ui().execute(Goldberry::stop);
            return;
        }
        var started = System.nanoTime();
        cpuAtStart.putAll(cpuTimes());
        var length = player.status().duration().orElse(Duration.ofMinutes(1));
        awaitState(PlaybackState.ENDED, length.plusSeconds(30));
        var wall = (System.nanoTime() - started) / 1e9;
        var cpu = cpuTimes();
        var status = player.status();
        var stats = player.videoStatistics();
        report(status.state(), status.videoDecoder().orElse("none"), wall, stats, cpu);
        Goldberry.ui().execute(Goldberry::stop);
    }

    private boolean awaitState(PlaybackState wanted, Duration within) {
        var deadline = System.nanoTime() + within.toNanos();
        while (System.nanoTime() < deadline) {
            var state = player.status().state();
            if (state == wanted) {
                return true;
            }
            if (state == PlaybackState.ERROR) {
                return false;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /// Each Engine thread's CPU time by name, and the UI thread's, and the
    /// process's, in nanoseconds.
    private Map<String, Long> cpuTimes() {
        var times = new HashMap<String, Long>();
        for (var thread : Thread.getAllStackTraces().keySet()) {
            var name = thread.threadId() == uiThreadId ? "ui" : thread.getName();
            if (name.equals("ui") || name.startsWith("goldberry-media-")) {
                times.merge(name, threads.getThreadCpuTime(thread.threadId()), Long::sum);
            }
        }
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            times.put("process", os.getProcessCpuTime());
        }
        return times;
    }

    private void report(PlaybackState end, String decoder, double wall, VideoStatistics after, Map<String, Long> cpu) {
        // The counts since the source opened, the first picture's among them;
        // the CPU times since it began to play.
        var decoded = after.decoded();
        var shown = after.shown();
        var late = after.late();
        var passed = after.passed();
        System.out.printf(Locale.ROOT, "%n4K60 video present: %s, %s%n", clip.getFileName(), end);
        System.out.printf(
                Locale.ROOT, "decoder %s, pictures as %s, %.1f s played%n%n", decoder, player.pictureForm(), wall);
        System.out.println("| Measurement | value |");
        System.out.println("|---|---|");
        System.out.printf(Locale.ROOT, "| pictures decoded | %d |%n", decoded);
        System.out.printf(Locale.ROOT, "| shown | %d (%.1f a second) |%n", shown, shown / wall);
        System.out.printf(Locale.ROOT, "| dropped late, before preparing | %d |%n", late);
        System.out.printf(Locale.ROOT, "| passed over in the queue | %d |%n", passed);
        for (var name : List.of("goldberry-media-video", "goldberry-media-demux", "goldberry-media-audio", "ui")) {
            var spent = cpu.getOrDefault(name, 0L) - cpuAtStart.getOrDefault(name, 0L);
            System.out.printf(
                    Locale.ROOT,
                    "| %s thread CPU | %.2f ms a picture shown (%.0f%% of a core) |%n",
                    name.replace("goldberry-media-", ""),
                    shown == 0 ? 0 : spent / 1e6 / shown,
                    spent / 1e9 / wall * 100);
        }
        var process = cpu.getOrDefault("process", 0L) - cpuAtStart.getOrDefault("process", 0L);
        System.out.printf(
                Locale.ROOT,
                "| process CPU (not the system's video engine) | %.2f ms a picture (%.0f%% of a core) |%n",
                shown == 0 ? 0 : process / 1e6 / shown,
                process / 1e9 / wall * 100);
        var verdict = end == PlaybackState.ENDED && late + passed == 0 ? "no pictures dropped" : "PICTURES DROPPED";
        System.out.println();
        System.out.println(verdict);
        failed = !verdict.equals("no pictures dropped");
    }
}
