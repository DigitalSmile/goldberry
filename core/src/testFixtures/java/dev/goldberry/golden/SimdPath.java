package dev.goldberry.golden;

import java.lang.management.ManagementFactory;
import java.util.Locale;

import com.sun.management.HotSpotDiagnosticMXBean;

/// Which vector instructions this machine rasterizes with, as the JVM read them.
///
/// Blend2D compiles its pipelines for the CPU it finds, so a golden image is a
/// statement about one instruction set. A golden that fails on a runner has to
/// say which one, or "red on CI and green here" cannot tell a rasterizer that
/// disagrees on AVX-512 from anything else. A green run that says so is how a
/// path nobody here can run gets checked.
///
/// The JVM's own reading rather than the rasterizer's: HotSpot sets `UseAVX` to
/// the highest level the CPU and the operating system both enable, which is
/// the same question the rasterizer's dispatch asks, and it needs nothing from
/// the native library. On ARM every 64-bit core has NEON, which is the path
/// the rasterizer takes there.
public final class SimdPath {

    private SimdPath() {}

    /// The architecture and its vector level, as `x86-64, AVX2`.
    public static String describe() {
        var arch = System.getProperty("os.arch", "unknown").toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "aarch64", "arm64" -> "aarch64, NEON";
            case "amd64", "x86_64" -> "x86-64, " + x86Level(useAvx());
            default -> arch;
        };
    }

    /// What HotSpot's `UseAVX` means, or "vector level unknown" when it could
    /// not be read.
    static String x86Level(int useAvx) {
        return switch (useAvx) {
            case 0 -> "SSE";
            case 1 -> "AVX";
            case 2 -> "AVX2";
            case 3 -> "AVX-512";
            default -> "vector level unknown";
        };
    }

    /// HotSpot's `UseAVX`, or -1 on a VM that does not have it.
    private static int useAvx() {
        try {
            var bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            return bean == null
                    ? -1
                    : Integer.parseInt(bean.getVMOption("UseAVX").getValue());
        } catch (IllegalArgumentException | LinkageError e) {
            // No such option (not HotSpot, or not x86), or no management module.
            return -1;
        }
    }
}
