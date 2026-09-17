package io.github.digitalsmile.goldberry.build;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The Linux development packages the superbuild needs, and how to name them to a
 * package manager.
 *
 * <h2>Why this exists</h2>
 *
 * SDL3's {@code CheckX11} does not degrade when an X11 extension is absent. Every
 * {@code SDL_X11_*} sub-feature defaults to {@code ON}, and a missing one ends in
 * {@code SDL_missing_dependency}, which is a {@code FATAL_ERROR}:
 *
 * <pre>{@code
 * CMake Error at .../cmake/macros.cmake:433 (message):
 *   Couldn't find dependency package for XSCRNSAVER.  Please install the needed
 *   packages or configure with -DSDL_X11_XSCRNSAVER=OFF
 * }</pre>
 *
 * That error arrives minutes into a configure, from inside a vendored CMake
 * script, naming a CMake option rather than a package. {@code checkToolchain}
 * exists to say the same thing in one second and in terms of {@code apt} — and it
 * can only do that if this table agrees with what SDL actually demands.
 *
 * <p>It did not. {@code xscrnsaver} was listed under the module name {@code xss},
 * which no distribution ships a {@code .pc} file for — SDL's own spec is
 * {@code xscrnsaver} (`cmake/sdlchecks.cmake`, {@code Xss_PKG_CONFIG_SPEC}) — so
 * the row never matched on any machine, present or absent, and it was marked
 * optional besides. {@code xtst} was not listed at all. Both CI workflows had
 * already been taught, by the same failure, that the two are hard dependencies;
 * the local check never learned it. ADR-0082 records that, and
 * {@code LinuxDependenciesTest} now asserts the two cannot drift apart again.
 *
 * <h2>A row can also cost a capability</h2>
 *
 * The X11 rows above are absolute: without them there is no library at all. The
 * desktop-integration rows are the opposite and are worse for it. {@code dbus-1},
 * {@code ibus-1.0} and {@code libudev} each gate a feature SDL compiles out
 * <em>silently</em> when the headers are absent, leaving a call that succeeds and
 * answers nothing — the system theme, the portal file dialog, the input method on
 * X11, device hotplug. A row therefore also names the {@code Capability} constants
 * a library loses without it, which is the same fact the superbuild compiles into
 * the binary and {@code Goldberry.capabilities()} reads back. See
 * {@code docs/gaps.md} G32 and ADR-0325.
 *
 * <h2>What a row means</h2>
 *
 * A module here is a <em>pkg-config</em> module name, because that is what
 * {@code pkg-config --exists} is asked and what SDL itself looks for. The package
 * names differ per distribution and neither matches the module: {@code xscrnsaver}
 * comes from {@code libxss-dev} on Debian and {@code libXScrnSaver-devel} on RHEL.
 * Getting that mapping wrong is how a correct diagnosis becomes an install command
 * that does not work.
 */
public final class LinuxDependencies {

    private LinuxDependencies() {
    }

    /** How much the superbuild minds if a dependency is absent. */
    public enum Necessity {

        /**
         * SDL stops the configure. Not a degraded build — no build at all, with
         * the message at the top of this class.
         */
        HARD_STOP,

        /**
         * Goldberry ships a backend that needs it, and SDL drops that backend
         * <em>silently</em> when it is missing. {@code CheckWayland} is one
         * {@code pkg_check_modules} over five specs: lose any one of them and the
         * Wayland driver simply is not compiled in, the configure succeeds, and
         * the first sign of trouble is a user on Wayland with no window. Worth a
         * hard failure here precisely because SDL will not give one.
         */
        NEEDED,

        /** A feature the toolkit does not use. Absence is a warning, not an error. */
        OPTIONAL;

        /** @return whether an absent dependency should fail the build */
        public boolean required() {
            return this != OPTIONAL;
        }
    }

    /** A package manager, as far as naming a package is concerned. */
    public enum PackageManager {
        /** Debian, Ubuntu, and the runner in {@code showcase.yml}. */
        APT,
        /** RHEL, AlmaLinux, and the manylinux container in {@code linux.yml}. */
        DNF;

        /** @return the command that installs packages, without the package names */
        public String installPrefix() {
            return switch (this) {
                case APT -> "sudo apt install";
                case DNF -> "sudo dnf install -y";
            };
        }
    }

    /**
     * One dependency: what to probe for, what to install, and what breaks without it.
     *
     * @param module       the pkg-config module name, as {@code pkg-config --exists} takes it
     * @param aptPackage   the Debian/Ubuntu package providing it
     * @param dnfPackage   the RHEL/Fedora package providing it
     * @param necessity    how much the superbuild minds if it is absent
     * @param purpose      what it is for, in the words the failure message uses
     * @param capabilities the names of the {@code Capability} constants a built
     *                     library loses without it, empty when it costs none --
     *                     the link between this table and what an application can
     *                     read back from {@code Goldberry.capabilities()}
     *                     (ADR-0325, {@code docs/gaps.md} G32)
     */
    public record Dependency(String module,
                             String aptPackage,
                             String dnfPackage,
                             Necessity necessity,
                             String purpose,
                             List<String> capabilities) {

        public Dependency {
            requireText(module, "module");
            requireText(aptPackage, "aptPackage");
            requireText(dnfPackage, "dnfPackage");
            requireText(purpose, "purpose");
            Objects.requireNonNull(necessity, "necessity");
            capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        }

        /**
         * A dependency that backs no run-time capability -- which is most of them:
         * an X11 extension SDL will not configure without is not something an
         * application can be told about later, because there is no library to tell
         * it with.
         *
         * @param module     the pkg-config module name
         * @param aptPackage the Debian/Ubuntu package providing it
         * @param dnfPackage the RHEL/Fedora package providing it
         * @param necessity  how much the superbuild minds if it is absent
         * @param purpose    what it is for
         */
        public Dependency(String module,
                          String aptPackage,
                          String dnfPackage,
                          Necessity necessity,
                          String purpose) {
            this(module, aptPackage, dnfPackage, necessity, purpose, List.of());
        }

        /** @return whether a built library loses a reportable capability without this */
        public boolean backsACapability() {
            return !capabilities.isEmpty();
        }

        private static void requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
        }

        /**
         * @param manager the package manager to name it for
         * @return the package name to hand that manager
         */
        public String packageFor(PackageManager manager) {
            return switch (manager) {
                case APT -> aptPackage;
                case DNF -> dnfPackage;
            };
        }

        /** @return whether an absent copy of this should fail the build */
        public boolean required() {
            return necessity.required();
        }

        /**
         * @return the purpose, with a note when SDL will refuse to configure --
         *         which is the difference between "install this and get a feature"
         *         and "install this or there is no library"
         */
        public String describedPurpose() {
            return necessity == Necessity.HARD_STOP
                    ? purpose + " (SDL stops the configure without it)"
                    : purpose;
        }
    }

    /**
     * Every dependency the superbuild probes for on Linux.
     *
     * <p>The X11 rows through {@code xtst} are each a {@code dep_option(SDL_X11_*
     * ... ON ...)} in SDL's {@code CMakeLists.txt} with an
     * {@code SDL_missing_dependency} branch in {@code sdlchecks.cmake}. XDBE,
     * XShape and XSync are on that list too and are deliberately not rows of their
     * own: their headers all come from {@code libxext-dev}, which {@code xext}
     * already covers.
     */
    public static final List<Dependency> ALL = List.of(
            new Dependency("x11", "libx11-dev", "libX11-devel",
                    Necessity.HARD_STOP, "SDL3 X11 backend"),
            new Dependency("xext", "libxext-dev", "libXext-devel",
                    Necessity.HARD_STOP, "SDL3 double-buffering, shaped windows and XSync"),
            new Dependency("xrandr", "libxrandr-dev", "libXrandr-devel",
                    Necessity.HARD_STOP, "SDL3 per-monitor DPI"),
            new Dependency("xcursor", "libxcursor-dev", "libXcursor-devel",
                    Necessity.HARD_STOP, "SDL3 cursors"),
            new Dependency("xi", "libxi-dev", "libXi-devel",
                    Necessity.HARD_STOP, "SDL3 input"),
            new Dependency("xfixes", "libxfixes-dev", "libXfixes-devel",
                    Necessity.HARD_STOP, "SDL3 pointer capture"),
            // Not `xss`. That module name does not exist -- SDL asks pkg-config for
            // `xscrnsaver`, and so does every distribution that ships the .pc file.
            // Listing it as `xss` meant the row could not match even on a machine
            // that had it, so the probe was answering a question nobody asked.
            new Dependency("xscrnsaver", "libxss-dev", "libXScrnSaver-devel",
                    Necessity.HARD_STOP, "SDL3 screensaver inhibition"),
            // XTest is the next hard failure after XScrnSaver, in that order. Adding
            // one without the other moves the error down a line and no further,
            // which is exactly what happened in linux.yml.
            new Dependency("xtst", "libxtst-dev", "libXtst-devel",
                    Necessity.HARD_STOP, "SDL3 pointer warping on X11"),

            new Dependency("wayland-client", "libwayland-dev", "wayland-devel",
                    Necessity.NEEDED, "SDL3 Wayland backend"),
            new Dependency("wayland-cursor", "libwayland-dev", "wayland-devel",
                    Necessity.NEEDED, "SDL3 Wayland cursors"),
            new Dependency("wayland-egl", "libwayland-dev", "wayland-devel",
                    Necessity.NEEDED, "SDL3 Wayland backend"),
            // The fifth spec in SDL's Wayland pkg-config check, and the one nothing
            // else pulls in. Without it the whole Wayland driver drops out quietly.
            new Dependency("egl", "libegl1-mesa-dev", "mesa-libEGL-devel",
                    Necessity.NEEDED, "SDL3 Wayland backend"),
            // Not part of SDL's Wayland pkg-config spec -- a separate check, and one
            // that fails quietly in a way nothing else here does. GNOME's compositor
            // does not do server-side decorations, so on GNOME/Wayland libdecor is
            // the ONLY thing that draws a titlebar or a resize edge. Build without
            // its headers and SDL compiles a Wayland driver that opens a window with
            // no close button and no way to resize it, and says nothing (ADR-0083).
            new Dependency("libdecor-0", "libdecor-0-dev", "libdecor-devel",
                    Necessity.NEEDED, "SDL3 Wayland decorations and resize"),
            // Goldberry binds no xkbcommon: SDL owns keyboard translation and loads
            // libxkbcommon itself (ADR-0055). Its headers are still needed to BUILD
            // SDL's Wayland backend, and the keymap data at run time.
            new Dependency("xkbcommon", "libxkbcommon-dev", "libxkbcommon-devel",
                    Necessity.NEEDED, "SDL3 keyboard translation"),
            new Dependency("xkeyboard-config", "xkb-data", "xkeyboard-config",
                    Necessity.NEEDED, "SDL3 keymaps"),

            new Dependency("alsa", "libasound2-dev", "alsa-lib-devel",
                    Necessity.OPTIONAL, "SDL3 audio"),
            new Dependency("libpulse", "libpulse-dev", "pulseaudio-libs-devel",
                    Necessity.OPTIONAL, "SDL3 audio"),
            new Dependency("libdrm", "libdrm-dev", "libdrm-devel",
                    Necessity.OPTIONAL, "SDL3 KMS/DRM"),
            new Dependency("gbm", "libgbm-dev", "mesa-libgbm-devel",
                    Necessity.OPTIONAL, "SDL3 KMS/DRM"),
            new Dependency("libudev", "libudev-dev", "systemd-devel",
                    Necessity.OPTIONAL, "SDL3 input-device hotplug",
                    List.of("DEVICE_HOTPLUG")),

            // NEEDED, and it used to be OPTIONAL under the vague purpose "SDL3
            // desktop integration". That is how a client shipped a settings screen
            // telling its user their dark desktop had no light-or-dark setting.
            //
            // src/core/linux/SDL_system_theme.c IS SDL's theme detection on Linux --
            // the XDG settings portal over D-Bus, with no second path -- and every
            // line of it is behind SDL_USE_LIBDBUS, which is #defined from
            // HAVE_DBUS_DBUS_H, which SDL's CMake sets from a build-time probe for
            // these headers. SDL dlopen()s libdbus-1.so at RUN time, so the shipped
            // library needs no D-Bus installed to use it; it needed the headers to
            // be able to. Built without them, SDL_GetSystemTheme() is a compile-time
            // constant UNKNOWN, on every Linux desktop, however it is set -- and
            // Host.systemTheme() is a shipped API answering empty for ever.
            //
            // Optional was the honest word while nothing bound SDL_GetSystemTheme.
            // ADR-0322 bound it. See docs/gaps.md G32.
            new Dependency("dbus-1", "libdbus-1-dev", "dbus-devel",
                    Necessity.NEEDED,
                    "SDL3 system theme, portal file dialogs and screensaver inhibition",
                    List.of("SYSTEM_THEME", "FILE_DIALOG", "SCREENSAVER_INHIBIT")),

            // The X11 input method, and only X11: SDL drives zwp_text_input_v3 from
            // the compositor on Wayland and needs neither IBus nor Fcitx there. So
            // this is OPTIONAL in the strict sense -- a Wayland session composes
            // fine without it -- and it is exactly the kind of absence that hides,
            // because whoever builds the library and whoever cannot type Japanese
            // into it are rarely on the same display server.
            new Dependency("ibus-1.0", "libibus-1.0-dev", "ibus-devel",
                    Necessity.OPTIONAL, "SDL3 input method on X11",
                    List.of("INPUT_METHOD")));

    /**
     * The dependencies a built library loses a reportable capability without.
     *
     * <p>These are the rows the published artifact's own workflow has to install
     * whatever their necessity says, because their absence is invisible: the
     * library builds, links, paints and takes input, and one call answers "the
     * desktop does not say" for ever. {@code Goldberry.capabilities()} is what
     * finally reports it at run time; installing the package is what keeps there
     * being nothing to report. See {@code docs/gaps.md} G32 and ADR-0325.
     *
     * @return the rows naming at least one capability
     */
    public static List<Dependency> capabilityBacked() {
        return ALL.stream().filter(Dependency::backsACapability).toList();
    }

    /** @return the dependencies whose absence should fail the build */
    public static List<Dependency> required() {
        return ALL.stream().filter(Dependency::required).toList();
    }

    /** @return the dependencies SDL refuses to configure without */
    public static List<Dependency> hardStops() {
        return ALL.stream().filter(d -> d.necessity() == Necessity.HARD_STOP).toList();
    }

    /**
     * The command that installs a set of dependencies.
     *
     * <p>Deduplicated and sorted, because four Wayland modules come from one
     * {@code libwayland-dev} and an install line that names it four times reads
     * like four separate problems.
     *
     * @param dependencies what to install; may be empty
     * @param manager      the package manager to phrase it for
     * @return a single shell command, or an empty string for an empty input
     */
    public static String installCommand(List<Dependency> dependencies, PackageManager manager) {
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(manager, "manager");
        if (dependencies.isEmpty()) {
            return "";
        }
        var packages = dependencies.stream()
                .map(dependency -> dependency.packageFor(manager))
                .distinct()
                .sorted()
                .collect(Collectors.joining(" "));
        return manager.installPrefix() + " " + packages;
    }

    /**
     * The message for dependencies that must be installed before the build can run.
     *
     * @param absent  the required dependencies that were not found; must not be empty
     * @param manager the package manager to phrase the install command for
     * @return a multi-line message naming each one, what it is for, and how to get it
     * @throws IllegalArgumentException if {@code absent} is empty -- there is no
     *                                  such thing as a failure with nothing missing
     */
    public static String missingRequiredMessage(List<Dependency> absent, PackageManager manager) {
        if (Objects.requireNonNull(absent, "absent").isEmpty()) {
            throw new IllegalArgumentException("no dependencies are missing; there is nothing to report");
        }
        return """
                Missing development headers the superbuild needs:
                %s

                %s
                """.formatted(detail(absent), installCommand(absent, manager));
    }

    /**
     * The message for dependencies whose absence only costs a feature.
     *
     * @param absent  the optional dependencies that were not found; must not be empty
     * @param manager the package manager to phrase the install command for
     * @return a multi-line warning
     * @throws IllegalArgumentException if {@code absent} is empty
     */
    public static String missingOptionalMessage(List<Dependency> absent, PackageManager manager) {
        if (Objects.requireNonNull(absent, "absent").isEmpty()) {
            throw new IllegalArgumentException("no dependencies are missing; there is nothing to report");
        }
        return """
                WARNING: optional headers absent; SDL3 will build without these features:
                %s
                  %s""".formatted(detail(absent), installCommand(absent, manager));
    }

    private static String detail(List<Dependency> dependencies) {
        return dependencies.stream()
                .map(dependency -> "  " + dependency.module() + " -- " + dependency.describedPurpose())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Picks a package manager from what the machine has.
     *
     * @param hasDnf whether {@code dnf} is on the PATH
     * @return {@link PackageManager#DNF} if it is, {@link PackageManager#APT} otherwise
     */
    public static PackageManager managerFor(boolean hasDnf) {
        return hasDnf ? PackageManager.DNF : PackageManager.APT;
    }

    /**
     * Looks a dependency up by its pkg-config module name.
     *
     * @param module the module name
     * @return the matching dependency
     * @throws IllegalArgumentException if no row has that module
     */
    public static Dependency byModule(String module) {
        var wanted = Objects.requireNonNull(module, "module").toLowerCase(Locale.ROOT);
        return ALL.stream()
                .filter(dependency -> dependency.module().equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no dependency for pkg-config module " + module));
    }
}
