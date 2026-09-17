package io.github.digitalsmile.goldberry.build.toolchain;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * What a Windows machine needs before {@code :natives:cmakeBuild} may run: MSVC,
 * and the environment that makes {@code cl} callable (ADR-0338).
 *
 * <h2>Why the compiler is named at all</h2>
 *
 * CMake's Ninja generator takes the first C compiler it finds on the
 * {@code PATH}, and on a GitHub {@code windows-2022} runner that is
 * {@code C:\mingw64\bin\cc.exe}: MSVC is installed but {@code cl.exe} is only on
 * the {@code PATH} inside a Developer Command Prompt. The first showcase build to
 * get that far configured GNU 14.2.0 without a word of complaint, linked a
 * {@code libgoldberry.dll} -- GNU naming -- that depends on
 * {@code libstdc++-6.dll}, and {@code showcaseImage} then failed looking for the
 * {@code goldberry.dll} an MSVC build writes and {@code goldberry-natives} ships.
 *
 * <p>So on Windows the compiler is not left to a search. {@code checkToolchain}
 * resolves {@link #COMPILER} the way it resolves {@code cmake}, refuses with
 * {@link #missingCompilerMessage} when it is absent, and {@code cmakeConfigure}
 * hands CMake the path it found through {@link #cmakeArguments}. The workflow's
 * side of the same agreement is {@link #CI_SETUP_ACTION}, which a test holds
 * {@code showcase.yml} to.
 */
public final class WindowsToolchain {

    /** The MSVC compiler driver, which is also what CMake is told to use for C++. */
    public static final String COMPILER = "cl";

    /**
     * The workflow step that runs {@code vcvarsall} for a job and exports what it
     * set, so every later step -- Gradle included -- sees {@code cl} on the
     * {@code PATH}.
     */
    public static final String CI_SETUP_ACTION = "ilammy/msvc-dev-cmd@v1";

    /** The workflow step that must come after {@link #CI_SETUP_ACTION}. */
    public static final String BUILD_STEP = "Build libgoldberry";

    private WindowsToolchain() {
    }

    /**
     * The cache entries that pin CMake to a compiler.
     *
     * <p>Forward slashes, because a {@code -D} value is written into the cache as
     * it was given and CMake reads a backslash in a path as the start of an
     * escape when the value is later expanded.
     *
     * @param compiler the resolved {@code cl.exe}
     * @return the two {@code -D} arguments, C then C++
     */
    public static List<String> cmakeArguments(Path compiler) {
        var path = compiler.toAbsolutePath().toString().replace('\\', '/');
        return List.of("-DCMAKE_C_COMPILER=" + path, "-DCMAKE_CXX_COMPILER=" + path);
    }

    /**
     * The message for a Windows machine on which {@code cl} could not be found.
     *
     * @param otherCompiler the compiler CMake would otherwise have picked, when
     *                      one was found, so the message can say what the build
     *                      would have quietly done instead
     * @return the message, ready for a {@code GradleException}
     */
    public static String missingCompilerMessage(Optional<Path> otherCompiler) {
        var message = new StringBuilder()
                .append("libgoldberry on Windows is built with MSVC, and `")
                .append(COMPILER)
                .append("` is not on the PATH.\n")
                .append("  Open an \"x64 Native Tools Command Prompt for VS 2022\", or run vcvars64.bat,\n")
                .append("  and build again. In GitHub Actions the ")
                .append(CI_SETUP_ACTION)
                .append(" step does the same for a job.\n");
        otherCompiler.ifPresent(found -> message
                .append("\nFound instead: ")
                .append(found)
                .append("\n  A MinGW build names the library libgoldberry.dll, depends on libstdc++-6.dll,\n")
                .append("  and is not the goldberry.dll that goldberry-natives ships (ADR-0338).\n"));
        return message.toString();
    }
}
