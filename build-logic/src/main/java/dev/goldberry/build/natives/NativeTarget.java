package dev.goldberry.build.natives;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One row of the distribution matrix: a platform libgoldberry is built and
 * published for, and the file names its two libraries take there.
 *
 * <p>Four rows, not six. Windows on ARM and macOS on Intel are not built, and the
 * Java side's {@code NativePlatform} refuses the same two pairs, so a target added
 * here and not there fails in {@code NativeLibraryTest} rather than on a user's
 * machine.
 *
 * <p>This is the one copy of the table the build has. It used to live in
 * {@code natives/build.gradle} and reach every other module through that
 * project's {@code ext}, which made each of them configure {@code :natives}
 * first; a class on the build's own classpath needs no such ordering.
 *
 * <p>Read more: <a href="https://goldberry.dev/docs/contributing/building.html#the-native-superbuild">The
 * native superbuild</a>.
 *
 * @param id              the classifier, {@code linux-x64}
 * @param library         libgoldberry's file name on this platform
 * @param webview         the web view library's file name on this platform
 * @param webviewRequired whether a published jar for this target must carry the web view
 */
public record NativeTarget(String id, String library, String webview, boolean webviewRequired) {

    /** Every target CI builds, in the order the jars are listed. */
    public static final List<NativeTarget> ALL = List.of(
            new NativeTarget("linux-x64", "libgoldberry.so", "libgoldberry-webview.so", true),
            new NativeTarget("linux-aarch64", "libgoldberry.so", "libgoldberry-webview.so", true),
            new NativeTarget("windows-x64", "goldberry.dll", "goldberry-webview.dll", true),
            new NativeTarget("macos-aarch64", "libgoldberry.dylib", "libgoldberry-webview.dylib", true));

    /** The operating systems a target id begins with. */
    public enum OperatingSystem {
        /** {@code linux-*}. */
        LINUX,
        /** {@code macos-*}. */
        MACOS,
        /** {@code windows-*}. */
        WINDOWS
    }

    public NativeTarget {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(library, "library");
        Objects.requireNonNull(webview, "webview");
    }

    /**
     * The target this machine builds natively.
     *
     * @return the row for this JVM's {@code os.name} and {@code os.arch}
     * @throws IllegalStateException if this machine is not a published target
     */
    public static NativeTarget host() {
        return host(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    /**
     * The target a machine with these properties builds natively. Mirrors
     * {@code NativePlatform.of} on the Java side.
     *
     * @param osName the {@code os.name} system property
     * @param osArch the {@code os.arch} system property
     * @return the row for that pair
     * @throws IllegalStateException if the pair is not a published target
     */
    public static NativeTarget host(String osName, String osArch) {
        var id = osToken(osName) + "-" + archToken(osArch);
        return byId(id);
    }

    /**
     * The row with this classifier.
     *
     * @param id a classifier such as {@code macos-aarch64}
     * @return its row
     * @throws IllegalStateException if no target has that id
     */
    public static NativeTarget byId(String id) {
        return ALL.stream()
                .filter(target -> target.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No native target declared for " + id));
    }

    /**
     * The operating system half of the id.
     *
     * @return what the id begins with
     */
    public OperatingSystem system() {
        return switch (id.substring(0, id.indexOf('-'))) {
            case "linux" -> OperatingSystem.LINUX;
            case "macos" -> OperatingSystem.MACOS;
            case "windows" -> OperatingSystem.WINDOWS;
            default -> throw new IllegalStateException("No operating system in " + id);
        };
    }

    /**
     * Whether a JVM that opens a window or a GPU device here has to be started on
     * its first thread, which AppKit insists on.
     *
     * @return {@code true} on macOS
     */
    public boolean needsFirstThread() {
        return system() == OperatingSystem.MACOS;
    }

    /**
     * The id as a task-name suffix: {@code linux-x64} is {@code LinuxX64}.
     *
     * @return the capitalised segments, joined
     */
    public String taskSuffix() {
        var suffix = new StringBuilder();
        for (var segment : id.split("-")) {
            suffix.append(Character.toUpperCase(segment.charAt(0))).append(segment.substring(1));
        }
        return suffix.toString();
    }

    /**
     * Where {@code :natives:cmakeBuild} installs this target, relative to that
     * project's build directory.
     *
     * @return {@code native/<id>/install}
     */
    public String nativesInstallDir() {
        return "native/" + id + "/install";
    }

    /**
     * Where {@code :media:ffmpegBuild} installs this target, relative to that
     * project's build directory.
     *
     * @return {@code ffmpeg/<id>/install}
     */
    public String ffmpegInstallDir() {
        return "ffmpeg/" + id + "/install";
    }

    /**
     * The libgoldberry a local {@code :natives:cmakeBuild} produces, in a
     * checkout rooted at {@code repository}.
     *
     * @param repository the directory holding {@code settings.gradle}
     * @return the library's path, whether or not it has been built
     */
    public Path localLibrary(Path repository) {
        return repository.resolve("natives/build").resolve(nativesInstallDir()).resolve("lib").resolve(library);
    }

    /**
     * The directory a local {@code :media:ffmpegBuild} installs FFmpeg's libraries
     * into, in a checkout rooted at {@code repository}.
     *
     * @param repository the directory holding {@code settings.gradle}
     * @return the directory, whether or not it has been built
     */
    public Path localFfmpeg(Path repository) {
        return repository.resolve("media/build").resolve(ffmpegInstallDir()).resolve("lib");
    }

    private static String osToken(String osName) {
        var name = normalize(osName);
        if (name.contains("linux")) {
            return "linux";
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return "macos";
        }
        if (name.contains("windows")) {
            return "windows";
        }
        throw new IllegalStateException("Goldberry has no native target for os.name=\"" + osName + "\"");
    }

    private static String archToken(String osArch) {
        return switch (normalize(osArch)) {
            case "amd64", "x86_64", "x64" -> "x64";
            case "aarch64", "arm64" -> "aarch64";
            default -> throw new IllegalStateException("Goldberry has no native target for os.arch=\"" + osArch + "\"");
        };
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "").toLowerCase(Locale.ROOT).strip();
    }
}
