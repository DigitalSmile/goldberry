package io.github.digitalsmile.goldberry.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Finds a native build tool -- {@code cmake}, {@code ninja}, {@code meson} -- and
 * returns an <em>absolute</em> path to it.
 *
 * <h2>Why this exists</h2>
 *
 * Gradle's {@code Exec} task cannot be trusted to find a tool by its bare name.
 * It passes an explicit environment map to every process it starts, and that one
 * detail changes how the JDK looks the executable up:
 *
 * <ul>
 *   <li>With no environment override, {@code ProcessBuilder} ends in
 *       {@code execvp}, which searches the {@code PATH} the child inherits --
 *       the JVM's own.</li>
 *   <li>With an environment override, it ends in the JDK's {@code execvpe},
 *       which searches {@code parentPathv} -- a copy of {@code PATH} taken from
 *       the JVM's <em>native</em> {@code environ} at start-up, and never
 *       refreshed.</li>
 * </ul>
 *
 * Those two are the same {@code PATH} in a normal program. They are not the same
 * in a Gradle daemon. The daemon outlives the shell that started it and serves
 * builds launched from anywhere, so Gradle rewrites what {@code System.getenv()}
 * reports to match the <em>current</em> client. The native {@code environ}
 * underneath is untouched, and so is the {@code parentPathv} snapshot taken from
 * it.
 *
 * <p>A daemon first started by a GUI launcher -- IntelliJ IDEA, say -- inherits
 * launchd's {@code PATH=/usr/bin:/bin:/usr/sbin:/sbin}, which has no Homebrew in
 * it. Every later build then sees the right {@code PATH} from {@code getenv} and
 * the wrong one from {@code execvpe}. The symptom is a toolchain check that
 * passes, reporting the {@code cmake} it just ran successfully, followed
 * immediately by:
 *
 * <pre>{@code
 * > A problem occurred starting process 'command 'cmake''
 *   Exec failed, error: 2 (No such file or directory)
 * }</pre>
 *
 * which names a file that is installed, executable, and on the {@code PATH}
 * Gradle just printed. {@code ./gradlew --stop} clears it until the next GUI
 * launch re-creates the daemon.
 *
 * <p>An absolute path sidesteps the lookup entirely: {@code execvpe} only
 * consults {@code parentPathv} for a name with no {@code /} in it. Resolving
 * here also lets the check and the use agree, because both ask this class rather
 * than each asking a different layer of the JDK.
 *
 * <h2>What it searches</h2>
 *
 * The client's {@code PATH} first, in order, then {@link #conventionalDirectories
 * the conventional install directories} for the platform -- so a tool installed
 * where its installer puts it is found even from a daemon whose {@code PATH} was
 * inherited from launchd and never had it.
 *
 * <p>The reasoning, the bisection that pinned it down, and the alternatives are
 * in ADR-0040, {@code book/src/adr/0040-find-the-native-tools-by-absolute-path.md}.
 */
public final class ToolResolver {

    private ToolResolver() {
    }

    /**
     * The platform, as far as locating a tool is concerned.
     *
     * <p>Coarser than the native target IDs in {@code natives/build.gradle}:
     * architecture does not change where an installer puts {@code cmake}, so it
     * plays no part here.
     */
    public enum Family {
        /** macOS: Homebrew, MacPorts, or the drag-and-drop {@code CMake.app}. */
        MACOS,
        /** Linux, and anything else POSIX enough to look like it. */
        LINUX,
        /** Windows, where an executable needs an extension to be one. */
        WINDOWS
    }

    /**
     * Classifies an {@code os.name} value.
     *
     * <p>Unrecognised systems are treated as {@link Family#LINUX}, deliberately:
     * an unknown Unix still keeps its tools in {@code /usr/local/bin}, and this
     * is not the place that decides whether Goldberry supports a platform.
     * {@code natives/build.gradle} fails loudly for an unknown {@code os.name}
     * when it derives the native target, which is the check that should own that
     * question.
     *
     * @param osName the {@code os.name} system property; {@code null} is treated as unknown
     * @return the family to search as
     */
    public static Family familyOf(String osName) {
        var name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (name.contains("windows")) {
            return Family.WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return Family.MACOS;
        }
        return Family.LINUX;
    }

    /**
     * The file names a tool might have on this platform, most likely first.
     *
     * <p>Only Windows has more than one: {@code cmake} there is {@code cmake.exe},
     * and Meson in particular is commonly a {@code .cmd} shim from pip.
     *
     * @param tool   the tool's base name, e.g. {@code "cmake"}
     * @param family the platform to name it for
     * @return one or more candidate file names, in search order
     */
    public static List<String> candidateFileNames(String tool, Family family) {
        return family == Family.WINDOWS
                ? List.of(tool + ".exe", tool + ".cmd", tool + ".bat", tool)
                : List.of(tool);
    }

    /**
     * Where the usual installers put these tools, searched after {@code PATH}.
     *
     * <p>This list is the actual fix for the daemon case: a {@code PATH} of
     * {@code /usr/bin:/bin:/usr/sbin:/sbin} contains none of these directories,
     * and every one of them is somewhere a supported installer really installs
     * to.
     *
     * @param family   the platform to list directories for
     * @param userHome the {@code user.home} system property; ignored when blank
     * @return absolute directories, in search order; not filtered for existence
     */
    public static List<Path> conventionalDirectories(Family family, String userHome) {
        var directories = new ArrayList<Path>();
        switch (family) {
            case MACOS -> {
                directories.add(Path.of("/opt/homebrew/bin"));                    // Homebrew, Apple silicon
                directories.add(Path.of("/usr/local/bin"));                       // Homebrew, Intel
                directories.add(Path.of("/opt/local/bin"));                       // MacPorts
                directories.add(Path.of("/Applications/CMake.app/Contents/bin")); // the .dmg
            }
            case LINUX -> {
                directories.add(Path.of("/usr/local/bin"));
                directories.add(Path.of("/usr/bin"));
                directories.add(Path.of("/bin"));
                directories.add(Path.of("/snap/bin"));
            }
            case WINDOWS -> {
                directories.add(Path.of("C:\\Program Files\\CMake\\bin"));
                directories.add(Path.of("C:\\Program Files\\Meson"));
                directories.add(Path.of("C:\\ProgramData\\chocolatey\\bin"));
            }
        }
        // pip is how the README says to get a CMake or Meson newer than the
        // distribution's, and `pip install --user` lands here on every platform.
        if (userHome != null && !userHome.isBlank()) {
            directories.add(Path.of(userHome, ".local", "bin"));
        }
        return List.copyOf(directories);
    }

    /**
     * Resolves a tool against a {@code PATH} and a list of fallback directories.
     *
     * <p>A {@code tool} that already looks like a path -- absolute, or containing
     * a separator -- is not searched for. It is taken at its word and returned if
     * it is an executable file, so that an explicit override points where it
     * says and fails visibly when it is wrong, rather than silently resolving to
     * some other copy found on the {@code PATH}.
     *
     * @param tool             a bare tool name, or a path to one
     * @param pathVariable     the {@code PATH} to search, entries separated by the
     *                         platform separator; may be {@code null} or empty
     * @param extraDirectories directories to search after {@code PATH}
     * @param family           the platform, which decides the candidate file names
     * @return the absolute path to the tool, or empty if no candidate is an
     *         executable regular file
     */
    public static Optional<Path> resolve(String tool,
                                         String pathVariable,
                                         List<Path> extraDirectories,
                                         Family family) {
        if (tool == null || tool.isBlank()) {
            return Optional.empty();
        }
        if (looksLikeAPath(tool)) {
            var candidate = Path.of(tool);
            return isExecutableFile(candidate) ? Optional.of(absolute(candidate)) : Optional.empty();
        }

        // A LinkedHashSet because PATH and the conventional directories overlap
        // -- /usr/local/bin is on both lists on Linux -- and a directory searched
        // twice is two stat calls for one answer.
        var directories = new LinkedHashSet<Path>();
        if (pathVariable != null) {
            for (var entry : pathVariable.split(java.io.File.pathSeparator, -1)) {
                // Empty entries are legal in a PATH and mean "the working
                // directory". Resolving a build tool relative to wherever Gradle
                // happens to be running is not a behaviour worth having.
                if (!entry.isBlank()) {
                    directories.add(Path.of(entry));
                }
            }
        }
        directories.addAll(extraDirectories);

        for (var directory : directories) {
            for (var fileName : candidateFileNames(tool, family)) {
                var candidate = directory.resolve(fileName);
                if (isExecutableFile(candidate)) {
                    return Optional.of(absolute(candidate));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves a tool for the machine described by the given properties.
     *
     * <p>The convenience form: derives the family and the conventional
     * directories, then defers to
     * {@link #resolve(String, String, List, Family)}.
     *
     * @param tool         a bare tool name, or a path to one
     * @param pathVariable the {@code PATH} to search -- the <em>client's</em>, read
     *                     through Gradle's provider API rather than
     *                     {@code System.getenv}, which is the whole point
     * @param osName       the {@code os.name} system property
     * @param userHome     the {@code user.home} system property
     * @return the absolute path to the tool, or empty if it was not found
     */
    public static Optional<Path> resolve(String tool, String pathVariable, String osName, String userHome) {
        var family = familyOf(osName);
        return resolve(tool, pathVariable, conventionalDirectories(family, userHome), family);
    }

    private static boolean looksLikeAPath(String tool) {
        return tool.indexOf('/') >= 0
                || tool.indexOf(java.io.File.separatorChar) >= 0;
    }

    private static boolean isExecutableFile(Path candidate) {
        // Follows symlinks on purpose: /opt/homebrew/bin/cmake is a link into
        // ../Cellar, and Homebrew is the majority case on macOS.
        return Files.isRegularFile(candidate) && Files.isExecutable(candidate);
    }

    private static Path absolute(Path candidate) {
        // toAbsolutePath, not toRealPath: resolving the symlink would report
        // Homebrew's versioned Cellar path, which pins the answer to a version
        // that `brew upgrade` is about to replace.
        return candidate.toAbsolutePath().normalize();
    }
}
