package dev.goldberry.gpu.render.staging;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.SdlSubsystem;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.render.model.PhysicalSize;

/// Phase 0 of the zero-copy plan: what painting in place and uploading in
/// place would save, per driver, against today's staged path.
///
/// Three tables, in Markdown so a run's log pastes into the plan:
///
/// 1. Blend2D painting a UI scene into heap memory and into a mapped SDL
///    upload transfer buffer, synchronous and with the toolkit's workers, and
///    one translucent fill over a mebibyte, which is what a probe at device
///    creation would time.
/// 2. The upload of 1%, 10% and 100% of a 1080p and a 4K frame, on each path
///    (`UploadCost`): the UI thread's stage, and until the GPU has copied it.
/// 3. D6's gate for this driver: the 4K whole-frame stage in place against
///    staged (at most half), and painting into mapped memory against heap (at
///    most 5% slower).
///
/// Without a device it says why and returns: it gates nothing, and a runner
/// with no GPU is an answer too. The device is made without validation, which
/// would be measured otherwise. `goldberry.gpu.videoDriver` picks SDL's video
/// driver, as for the GPU lane. Run by `:gpu:uploadPathProbe`, on the first
/// thread, and read by a person.
final class UploadPathProbe {

    private static final int WARMUP = 20;
    private static final int RUNS = 100;
    private static final List<PhysicalSize> SIZES = List.of(new PhysicalSize(1920, 1080), new PhysicalSize(3840, 2160));

    private UploadPathProbe() {}

    static void main() {
        var device = open();
        if (device == null) {
            return;
        }
        try {
            var adapter = device.adapter();
            System.out.printf(
                    Locale.ROOT,
                    "GPU %s on %s (%s %s); padded rows recorded in one go: %s; %d processors%n%n",
                    device.driver(),
                    adapter.name(),
                    adapter.driverName(),
                    adapter.driverVersion(),
                    device.uploadsPaddedRows(),
                    Runtime.getRuntime().availableProcessors());
            var paints = paint(device);
            var uploads = upload(device);
            gate(device.driver(), paints, uploads);
        } finally {
            device.close();
            Sdl.get().quit();
        }
    }

    private static List<PaintCost.Row> paint(SdlGpuDevice device) {
        System.out.println("Painting, heap against mapped upload memory (median / p95 ms)");
        System.out.println();
        System.out.println("| Frame | Workers | Heap | Mapped, kept | Mapped each frame | Kept slower by |");
        System.out.println("|---|---|---|---|---|---|");
        var cost = new PaintCost(device, WARMUP, RUNS);
        var rows = new ArrayList<PaintCost.Row>();
        for (var size : SIZES) {
            for (var threads : new int[] {0, -1}) {
                rows.add(print(cost.scene(size, threads)));
            }
        }
        print(cost.mebibyte());
        System.out.println();
        return rows;
    }

    private static PaintCost.Row print(PaintCost.Row row) {
        System.out.printf(
                Locale.ROOT,
                "| %dx%d | %s | %s | %s | %s | %+.1f%% |%n",
                row.size().width(),
                row.size().height(),
                row.threads(),
                row.heap().cell(),
                row.mapped().cell(),
                row.remapped().cell(),
                row.slower());
        return row;
    }

    private static List<UploadCost.Row> upload(SdlGpuDevice device) {
        System.out.println("Uploading a frame's damage (median / p95 ms)");
        System.out.println();
        System.out.println("| Frame | Damage | Path | Bytes copied | Stage | Until copied |");
        System.out.println("|---|---|---|---|---|---|");
        var cost = new UploadCost(device, WARMUP, RUNS);
        var rows = new ArrayList<UploadCost.Row>();
        for (var size : SIZES) {
            for (var damage : Damage.values()) {
                for (var path : UploadCost.Path.values()) {
                    var row = cost.measure(path, size.width(), size.height(), damage);
                    rows.add(row);
                    System.out.printf(
                            Locale.ROOT,
                            "| %dx%d | %s | %s | %.1f MB | %s | %s |%n",
                            row.width(),
                            row.height(),
                            row.damage(),
                            row.path(),
                            row.bytes() / 1e6,
                            row.stage().cell(),
                            row.copied().cell());
                }
            }
        }
        System.out.println();
        return rows;
    }

    /// D6 for this driver, from the 4K rows.
    private static void gate(String driver, List<PaintCost.Row> paints, List<UploadCost.Row> uploads) {
        var staged = driver.equals("direct3d12") ? UploadCost.Path.STAGED_ALIGNED : UploadCost.Path.STAGED_PACKED;
        var before = find(uploads, staged).stage().median();
        var after = find(uploads, UploadCost.Path.IN_PLACE).stage().median();
        var ratio = after / before;
        var slower = 0.0;
        for (var row : paints) {
            if (row.size().width() == 3840) {
                slower = Math.max(slower, row.slower());
            }
        }
        var first = ratio <= 0.5;
        var second = slower <= 5;
        System.out.printf(
                Locale.ROOT,
                "D6 on %s: (1) the 4K whole-frame stage in place is %.2f of %s's (at most 0.50): %s;"
                        + " (2) painting at 4K into mapped memory is at worst %+.1f%% (at most +5%%): %s."
                        + " In place: %s%n",
                driver,
                ratio,
                staged,
                first ? "passes" : "fails",
                slower,
                second ? "passes" : "fails",
                first && second ? "on" : "off");
    }

    private static UploadCost.Row find(List<UploadCost.Row> rows, UploadCost.Path path) {
        return rows.stream()
                .filter(row -> row.path() == path && row.width() == 3840 && row.damage() == Damage.WHOLE)
                .findFirst()
                .orElseThrow();
    }

    /// SDL's video under the lane's driver and a device without validation, or
    /// null, having said why.
    private static SdlGpuDevice open() {
        var sdl = Sdl.get();
        var videoDriver = System.getProperty("goldberry.gpu.videoDriver", "");
        if (!videoDriver.isBlank()) {
            sdl.setHint(Sdl.VIDEO_DRIVER_HINT, videoDriver);
        }
        try {
            sdl.initialize(EnumSet.of(SdlSubsystem.VIDEO));
            return SdlGpuDevice.create(SdlGpuDevice.Options.defaults());
        } catch (SdlException e) {
            System.out.println("No GPU device, so nothing to measure: " + e.getMessage());
            sdl.quit();
            return null;
        }
    }
}
