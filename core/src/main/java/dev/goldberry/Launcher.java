package dev.goldberry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.goldberry.bind.Subscription;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.image.StyleImages;
import dev.goldberry.css.lint.StyleLint;
import dev.goldberry.drive.FrameBudgetException;
import dev.goldberry.drive.ResizeWalk;
import dev.goldberry.input.cursor.CursorImage;
import dev.goldberry.motion.Clock;
import dev.goldberry.render.desktop.notify.NotificationCenter;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.Ownership;
import dev.goldberry.render.window.WindowSpec;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Widget;

/// Runs an [Application]: opens its window, builds its trees, drives the frame
/// loop and takes everything down in the reverse order, in one place instead of
/// in every `main`.
///
/// Not public: [Goldberry#launch] is the door. There is exactly one right way to
/// wire these objects together and no reason for an application to hold a
/// launcher, so what an application is handed is a [Host].
///
/// What is here is what belongs to the **application**: the stylesheets, the
/// fonts, the models, the clock and the order things are taken down in. What
/// belongs to a **window** — its trees, router, overlays, popups and frame — is
/// a [HostedWindow], one per window: the first one this class opens, and one
/// more for each [Host#openWindow].
///
/// Read more: [The lifecycle](https://goldberry.dev/docs/guide/windows.html#the-lifecycle).
final class Launcher {

    private static final Logger LOG = LoggerFactory.getLogger(Launcher.class);

    private final Application application;
    private final Options options;

    // Built by [#run] rather than here, in the order the window needs them, and
    // non-null from there on: everything that reads them runs inside `run` or is
    // called back by a window it opened. `NullAway.Init` says exactly that and
    // nothing more -- a read of one of them is still checked like any other.
    @SuppressWarnings("NullAway.Init")
    private Fonts fonts;

    /// The first window, whose closing ends the application.
    @SuppressWarnings("NullAway.Init")
    private HostedWindow main;

    /// Every other window [Host#openWindow] opened that is not yet taken down,
    /// in the order they opened.
    private final List<HostedWindow> others = new ArrayList<>();

    /// The one clock every window runs on.
    ///
    /// A field rather than the renderer's default, because two things read it and
    /// they must agree: the renderer times transitions against it, and
    /// [Host#clock()] hands it to widgets that need to know how long ago a
    /// keystroke arrived. A renderer left on its own `Clock.system()` would be a
    /// second clock -- the same defect `frameNow` exists to prevent one level down.
    ///
    /// It is also what a restyle must not disturb: a window rebuilds its renderer
    /// whenever the sheets change, and a clock rebuilt with it would restart
    /// every transition in flight when the theme switched. And it is the
    /// application's rather than a window's, so a second window's spinners turn
    /// with the first one's.
    private final Clock clock = Clock.system();

    /// The application's models, kept because they are swept once a frame.
    ///
    /// A no-op for a woven model, which notified from inside the assignment that
    /// changed it. For one bound at run time it is how a change made from
    /// somewhere no listener could see — a timer callback, a background job
    /// reporting in — reaches the screen rather than waiting for the next action.
    ///
    /// Read once at start-up rather than per frame: `models()` is a description of
    /// the wiring, and an application that returns a fresh list every call would
    /// otherwise allocate one per frame.
    private List<Object> models = List.of();

    /// The application's models' restyle and repaint subscriptions, given back
    /// in [#shutDown()].
    private final List<Subscription> modelSubscriptions = new ArrayList<>();

    /// Whether the frame limit has already been reached and [Goldberry#stop]
    /// called.
    ///
    /// `stop()` ends the loop; it does not unschedule the frames already in
    /// flight -- the resize walk's zero-delay timer and an animating renderer
    /// have each asked for one by then. Those frames still paint, and without
    /// this flag each of them would report "painted N frame(s); exiting" again,
    /// which reads as three exits rather than one.
    private boolean stopping;

    /// The walk `--resize=` asked for, or null when the window is left alone.
    private @Nullable ResizeWalk resizeWalk;

    /// The four flags the launcher understands on the command line, all for CI.
    ///
    /// A toolkit that parsed argv would be overstepping; these are read only from
    /// the array an application chose to hand over, and an application that calls
    /// [Goldberry#launch(Application)] passes none.
    ///
    /// @param frames     paint this many frames and exit, or 0 to run until
    ///                   closed — which is how a headless run proves a window
    ///                   opened without a human to close it
    /// @param size       the opening size, or null for the application's own
    /// @param resize     walk the window's size a pixel a frame between the
    ///                   opening size and this one, or null to leave it alone —
    ///                   the load a frame-rate claim is measured under
    /// @param lateBudget how many refreshes the run may miss before it exits
    ///                   non-zero, or -1 for a run that is not judged
    /// @param capture    where the last frame of a `--frames=N` run is written
    ///                   as a PNG, as the screen showed it, or null for no
    ///                   picture — what a reference capture is taken with
    record Options(
            int frames,
            @Nullable LogicalSize size,
            @Nullable LogicalSize resize,
            long lateBudget,
            @Nullable Path capture) {

        static final Options NONE = new Options(0, null, null, -1, null);

        /// The two flags there were before the walk and the budget.
        Options(int frames, @Nullable LogicalSize size) {
            this(frames, size, null, -1, null);
        }

        /// Reads `--frames=N`, `--size=WxH`, `--resize=WxH`, `--late-budget=N`
        /// and `--capture=PATH`, ignoring everything else — an application's
        /// own arguments are its business. Null reads as no arguments.
        static Options of(String @Nullable [] args) {
            var frames = 0;
            LogicalSize size = null;
            LogicalSize resize = null;
            var lateBudget = -1L;
            Path capture = null;
            for (var arg : args == null ? new String[0] : args) {
                if (arg.startsWith("--frames=")) {
                    frames = whole(arg, arg.substring("--frames=".length()), "frames");
                } else if (arg.startsWith("--size=")) {
                    size = pair(arg, arg.substring("--size=".length()));
                } else if (arg.startsWith("--resize=")) {
                    resize = pair(arg, arg.substring("--resize=".length()));
                } else if (arg.startsWith("--late-budget=")) {
                    lateBudget = whole(arg, arg.substring("--late-budget=".length()), "late frames");
                } else if (arg.startsWith("--capture=")) {
                    capture = path(arg, arg.substring("--capture=".length()));
                }
            }
            return new Options(frames, size, resize, lateBudget, capture);
        }

        /// `--capture=PATH`'s `PATH`, or a refusal that names the flag.
        private static Path path(String flag, String text) {
            if (text.isBlank()) {
                throw new IllegalArgumentException(flag + " names no file");
            }
            try {
                return Path.of(text);
            } catch (InvalidPathException e) {
                throw new IllegalArgumentException(flag + " is not a path", e);
            }
        }

        /// `WxH`, or null for anything that is not two numbers around an `x`.
        private static @Nullable LogicalSize pair(String flag, String text) {
            // Trailing empties dropped is the behaviour wanted: the length
            // check below is what rejects `--size=800x` anyway.
            @SuppressWarnings("StringSplitter")
            var parts = text.split("x");
            if (parts.length != 2) {
                return null;
            }
            return new LogicalSize(real(flag, parts[0]), real(flag, parts[1]));
        }

        /// `--frames=N`'s `N`, or a refusal that names the flag.
        ///
        /// `NumberFormatException` names the text and not the flag it came from,
        /// and a launcher's argument error is read by whoever typed it.
        private static int whole(String flag, String text, String what) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(flag + " is not a whole number of " + what, e);
            }
        }

        /// One side of `--size=WxH`, or a refusal that names the flag.
        private static float real(String flag, String text) {
            try {
                return Float.parseFloat(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(flag + " is not a width and a height", e);
            }
        }
    }

    Launcher(Application application, Options options) {
        this.application = Objects.requireNonNull(application, "application");
        this.options = Objects.requireNonNull(options, "options");
    }

    /// [Application#minimumSize], unless the window is opening smaller than it.
    ///
    /// The contradiction is only reachable through `--size=`, and that flag is
    /// the reason this is a demotion rather than a refusal: it exists so a
    /// screenshot or a golden run can pin the window's geometry, and
    /// an application that declares a 1024-wide minimum must not be able to make
    /// `--size=800x600` fail to start. The floor is a promise to a *user* about
    /// what dragging an edge may do, and there is no user in a golden run.
    ///
    /// Said out loud rather than dropped quietly: the next person to wonder why
    /// their window resizes past its minimum is running with a `--size=`.
    private LogicalSize floorFitting(LogicalSize opening) {
        var minimum = application.minimumSize();
        if (minimum.width() <= opening.width() && minimum.height() <= opening.height()) {
            return minimum;
        }
        LOG.warn(
                "--size={} is smaller than the {} minimum {} asks for; opening without a minimum size",
                opening,
                minimum,
                application.getClass().getSimpleName());
        return WindowSpec.NO_MINIMUM;
    }

    /// Opens the window, runs the loop, and takes everything down in the reverse
    /// order it was built.
    void run() {
        LOG.info(
                "Goldberry {} — starting {}",
                Goldberry.version(),
                application.getClass().getSimpleName());

        var size = options.size() == null ? application.size() : options.size();
        // `--size=` un-maximizes as well as resizing, because an explicit size on
        // the command line and a window that ignores it is the one combination
        // nobody means: the flag exists so a screenshot or a golden run can pin
        // the window's geometry.
        var window = Window.open(WindowSpec.of(application.title(), size)
                .withMinimumSize(floorFitting(size))
                .withMaximized(application.maximized() && options.size() == null)
                .withPosition(application.position().orElse(null))
                .withDisplay(application.display().orElse(null)));
        // Straight after opening, so the taskbar never shows the generic icon for
        // longer than the first frame takes.
        applyIcon(window);
        applyCursors();
        if (options.resize() != null) {
            resizeWalk = new ResizeWalk(size, options.resize());
            LOG.info("walking the window's size a pixel a frame: {}", resizeWalk);
        }

        // On the UI thread and staying there: the book owns native objects from
        // two libraries, confined to the thread that built them, and opening a
        // face per frame would put font parsing on the frame path.
        // With the application's own faces added after the bundled ones, read
        // here and never again.
        fonts = Fonts.bundled(application.fonts());

        main = new HostedWindow(this, window, null, false);
        main.afterPaint(this::afterMainPaint);

        // Before `root()`, so an application can open its icons and bind its
        // accelerators and then describe a tree that uses them.
        application.start(main);

        // The application's models drive every window, and it says nothing about
        // it: a change to a bound field asks for a frame, and a change to one
        // declared `@Bind(restyle = true)` drops the resolved styles first.
        models = List.copyOf(application.models());
        for (var model : models) {
            modelSubscriptions.add(Models.onRestyle(model, this::restyleAll));
            modelSubscriptions.add(Models.onRepaint(model, this::repaintAll));
        }
        // A picture a stylesheet named arrives after the frame that asked for
        // it, and any window may be drawing it.
        modelSubscriptions.add(StyleImages.onArrival(this::repaintAll));

        main.mount(application.root());

        // A popup goes away when the *application* does, which no platform
        // reports: opening one sends a focus-lost for the window under it and a
        // focus-gained for the popup itself.
        GoldberryRuntime.get().onFocusChange(this::focusMayHaveLeft);

        try {
            Goldberry.run();
        } finally {
            shutDown();
        }
        LOG.info("{} finished after {} frame(s)", application.getClass().getSimpleName(), main.painted());
        // The whole run in one line, after the window has gone: the ring kept
        // the totals, and this is the number a frame-rate claim is.
        var summary = window.frames().summary();
        LOG.info("frames: {}", summary.describe());
        // Where they went: every presented frame, a still one included, so a
        // window that sat on the GPU says so even when it had nothing to upload.
        // A line of its own, for the reason the next one is.
        LOG.info("on screen: {}", window.presentations().describe());
        // A second line rather than more of the first, which a workflow greps.
        var presents = window.frames().presentSummary();
        if (presents.frames() > 0) {
            LOG.info("presents: {}", presents.describe());
        }
        if (summary.exceeds(options.lateBudget())) {
            throw new FrameBudgetException(summary, options.lateBudget());
        }
    }

    /// The application's icon on `window`, for the taskbar entry every window
    /// of its own has.
    private void applyIcon(Window window) {
        var icon = application.icon();
        if (!icon.isEmpty() && !window.icon(icon)) {
            LOG.debug("the platform kept its own window icon");
        }
    }

    /// The application's cursor pictures, given to the backend once: the
    /// cursor is process-global, so every window of the application shows them.
    private void applyCursors() {
        var cursors = application.cursors();
        if (!cursors.isEmpty()) {
            GoldberryRuntime.get().backend().setCursorPictures(CursorImage.byShape(cursors));
        }
    }

    /// What the first window does after each frame: the command line's frame
    /// count and resize walk, both for CI.
    private void afterMainPaint() {
        var window = main.window();
        // **Between frames, not inside this one.** On Windows and macOS
        // `SDL_SetWindowSize` takes effect on the spot, so a request made from
        // inside the painter changes the size under the frame being painted and
        // the platform then refuses it -- every frame of the run, each counted
        // late. A zero-delay timer runs on the next pump, after this frame has
        // been presented. And from where the window actually is, so a manager
        // that clamped or lagged the last request is walked from its answer
        // rather than from the ask; the resize itself asks for the next frame.
        // Into a local for the lambda: set once in [#run] and never cleared, so
        // this is the same walk the field holds.
        var walk = resizeWalk;
        if (walk != null) {
            main.after(Duration.ZERO, () -> {
                if (window.isOpen()) {
                    window.resize(walk.next(window.size()));
                }
            });
        }
        var painted = main.painted();
        if (options.frames() > 0 && !stopping) {
            if (painted >= options.frames()) {
                stopping = true;
                var capture = options.capture();
                if (capture != null) {
                    capture(window, capture);
                }
                LOG.info("painted {} frame(s); exiting", painted);
                Goldberry.stop();
            } else {
                window.repaint();
            }
        }
    }

    /// Writes the window's last frame to `path` as a PNG: `--capture=`.
    ///
    /// @throws UncheckedIOException when the file cannot be written
    private static void capture(Window window, Path path) {
        var picture = window.capture();
        if (picture.isEmpty()) {
            LOG.warn("--capture={}: the window has no frame to capture", path);
            return;
        }
        try {
            var parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, picture.get().encodePng());
            LOG.info("captured the last frame to {}", path.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException("--capture=" + path + " could not be written", e);
        }
    }

    // --- what every window shares ----------------------------------------------

    List<Stylesheet> stylesheets() {
        return application.stylesheets();
    }

    Fonts fonts() {
        return fonts;
    }

    Clock clock() {
        return clock;
    }

    /// The application's name, which a notification and the macOS menu bar are
    /// shown under.
    String title() {
        return application.title();
    }

    /// Desktop notifications and the dock or launcher badge, for every window:
    /// a notification is the application's, not one window's. Made on the first
    /// notification or badge.
    NotificationCenter notifications() {
        if (notifications == null) {
            notifications = new NotificationCenter(
                    () -> GoldberryRuntime.get().backend().notifier(),
                    (delay, action) -> GoldberryRuntime.get().loop().after(delay, action));
        }
        return notifications;
    }

    private @Nullable NotificationCenter notifications;

    List<Object> models() {
        return models;
    }

    /// The lint a renderer runs when it is rebuilt, when it was asked for.
    void lintStylesheets() {
        StyleLint.reportIfAsked(application.stylesheets());
    }

    /// Every window, the first one first.
    private List<HostedWindow> windows() {
        var all = new ArrayList<HostedWindow>(others.size() + 1);
        //noinspection ConstantValue
        if (main != null) {
            all.add(main);
        }
        all.addAll(others);
        return all;
    }

    /// The stylesheets are the application's, so a restyle is every window's.
    void restyleAll() {
        for (var window : windows()) {
            window.stylesChanged();
        }
    }

    /// A model is the application's, so a change to one may show in any window.
    void repaintAll() {
        for (var window : windows()) {
            window.repaint();
        }
    }

    /// Whether a window of this application other than `window` has the
    /// keyboard — the user went to work in another of its windows.
    boolean anotherWindowFocused(HostedWindow window) {
        for (var other : windows()) {
            if (other != window && other.window().isFocused()) {
                return true;
            }
        }
        return false;
    }

    private void focusMayHaveLeft() {
        for (var window : windows()) {
            window.focusMayHaveLeft();
        }
    }

    // --- more than one window ---------------------------------------------------

    /// Opens a window for `root` that shares this application's stylesheets,
    /// fonts, models and clock.
    ///
    /// @param opener the host it was asked of: the owner, when the spec says the
    ///               new window has one
    Optional<WindowHost> openWindow(WindowSpec spec, Widget root, HostedWindow opener) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(root, "root");
        if (!opener.isOpen()) {
            throw new IllegalStateException("cannot open a window from one that has closed");
        }
        var owned = spec.ownership() != Ownership.NONE;
        var window = Window.open(spec, owned ? opener.window() : null);
        applyIcon(window);
        var hosted = new HostedWindow(this, window, owned ? opener : null, spec.ownership() == Ownership.MODAL);
        others.add(hosted);
        hosted.mount(root);
        if (spec.ownership() == Ownership.MODAL) {
            opener.modalOpened(hosted);
        }
        LOG.debug("window \"{}\" opened beside {} other(s)", spec.title(), others.size());
        return Optional.of(hosted);
    }

    /// `window` began to close: the windows that go with it are closed first.
    ///
    /// The first window takes every other with it — closing it ends the
    /// application. Any other takes the windows that belong to it, as the
    /// platform would.
    void closing(HostedWindow window) {
        for (var other : List.copyOf(others)) {
            if (other != window && (window == main || other.owner() == window)) {
                other.close();
            }
        }
    }

    /// `window`'s tree is gone.
    void disposed(HostedWindow window) {
        others.remove(window);
    }

    /// How long to wait before believing a focus-lost.
    ///
    /// **Deferred rather than acted on**, and this is the whole mechanism. The
    /// platform reports focus per window: opening a popup sends a lost for the
    /// owner and then a gained for the popup, in that order, so a menu that
    /// closed on the first of those would close as it opened. One turn of the
    /// event loop later, the pair has been seen and the question "is any window
    /// of ours focused" has its real answer.
    ///
    /// Short enough not to leave a menu floating over another application while
    /// anybody notices, long enough to cover a pair of events the compositor
    /// delivers in two batches.
    ///
    /// **60 ms is one number covering every driver**, and the first driver to
    /// deliver that pair more slowly makes a menu look like it closes as it
    /// opens. It cannot be derived — the pair is the compositor's own scheduling
    /// and nothing reports what it will be — so what can be done about it is to
    /// let it be told: [#SETTLE_PROPERTY] overrides it without a rebuild, which
    /// is what turns "this driver is broken" into a flag somebody can set.
    ///
    /// **An instance field and not a constant**: read when the launcher is built
    /// rather than when the class is loaded, so a test that sets the property can
    /// set it in the test rather than in the JVM that runs the suite.
    private final Duration focusSettle = focusSettle();

    /// How long to disbelieve a focus-lost for, in milliseconds.
    ///
    /// A tuning flag of the same kind as `goldberry.frame.rate`: rarely needed,
    /// and the one thing that makes a compositor nobody here has run against
    /// somebody else's afternoon rather than a bug report.
    static final String SETTLE_PROPERTY = "goldberry.popup.settle";

    /// The default, or what [#SETTLE_PROPERTY] says.
    ///
    /// Clamped rather than trusted: zero would act on the *first* of the pair
    /// every driver sends and close every menu as it opened, which is the exact
    /// bug this delay exists for. A value that will not parse is ignored rather
    /// than fatal, for the reason a malformed frame rate is — a tuning flag
    /// should not stop an application starting.
    static Duration focusSettle() {
        var raw = System.getProperty(SETTLE_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return Duration.ofMillis(60);
        }
        try {
            return Duration.ofMillis(Math.clamp(Long.parseLong(raw.trim()), 1L, 2_000L));
        } catch (NumberFormatException e) {
            LOG.warn("{}=\"{}\" is not a number of milliseconds; using 60", SETTLE_PROPERTY, raw);
            return Duration.ofMillis(60);
        }
    }

    /// How long a window waits before believing a focus-lost — see
    /// [#focusSettle()].
    Duration popupSettle() {
        return focusSettle;
    }

    /// The reverse of the build order, and the ordering is the reason this class
    /// exists.
    ///
    /// After the loop rather than in a try-with-resources around it: the paint
    /// callback holds the renderer and the fonts and runs until `run` returns. And
    /// the render trees go before the fonts, because a render object holds a Yoga
    /// measure callback that closes over a paragraph, and a paragraph over a font
    /// — closing them the other way round leaves Blend2D reading unmapped memory.
    private void shutDown() {
        // The models are the application's and may outlive this launch, as a
        // model shared by two launches does; one still subscribed would go on
        // asking a closed window for frames.
        for (var subscription : modelSubscriptions) {
            subscription.close();
        }
        modelSubscriptions.clear();
        // Every other window first, closed and taken down now rather than on a
        // turn of the loop that is not coming.
        for (var other : List.copyOf(others)) {
            other.close();
            other.dispose();
        }
        //noinspection ConstantValue
        if (main != null) {
            main.dispose();
        }
        // After the trees, so a widget still holding an icon has already gone.
        application.stop();
        //noinspection ConstantValue
        if (fonts != null) {
            fonts.close();
        }
        // And hand the window back before the process goes away. Not tidiness:
        // `Goldberry.stop()` ends the loop with the window still open, so without
        // this the process exits with a live Wayland surface and SDL never quit --
        // which GNOME 46's Mutter does not always survive.
        Goldberry.shutdown();
    }
}
