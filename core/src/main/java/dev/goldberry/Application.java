package dev.goldberry;

import java.util.List;
import java.util.Optional;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.image.Image;
import dev.goldberry.input.cursor.CursorImage;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.WindowSpec;
import dev.goldberry.text.font.FontSource;
import dev.goldberry.widget.Widget;

/// The program an application implements: a root widget, and whatever else it
/// wants to say about its window. Everything else is [Goldberry#launch].
///
/// ```java
/// public final class Hello implements Application {
///
///     @Override
///     public Widget root() {
///         return new Column(
///                 new Text("Hello").id("greeting"),
///                 new Button("Close", Goldberry::stop));
///     }
///
///     public static void main(String[] args) {
///         Goldberry.launch(new Hello(), args);
///     }
/// }
/// ```
///
/// The launcher owns everything an application would otherwise set up by hand: a
/// window, a font book, the element tree, the render tree, the widget renderer,
/// the pointer router, the frame loop, damage tracking, the hit-test snapshot fed
/// from the painted frame, the idle rule, and the shutdown order. The last is the
/// part that is easy to get subtly wrong, because a render object holds a measure
/// callback that closes over a paragraph that closes over a font, and closing
/// them in the wrong order reads unmapped memory. None of it is a decision an
/// application makes differently.
///
/// What is left to the application is the widget tree, the stylesheets, and the
/// native resources only it knows it needs. An [dev.goldberry.icon.Icon] is the
/// usual one, and [#start] and [#stop] are where it opens and closes them.
/// Everything on this interface but [#root()] has a default, so the smallest
/// application is one method. Every method is called on the UI thread.
///
/// Read more: [Building an application](https://goldberry.dev/docs/applications.html#the-application).
public interface Application {

    /// The widget at the root of the window.
    ///
    /// Called **once**, on the UI thread, after [#start]. It is not a build
    /// method: what happens next is that the element tree mounts it, and every
    /// later rebuild comes from a `setState` inside it. An application whose
    /// whole window changes puts a [dev.goldberry.widget.Widget.Stateful]
    /// here and changes it from within, which is what makes the focus ring
    /// survive.
    Widget root();

    /// The window's title. Also settable at runtime through [Host#title].
    default String title() {
        return "Goldberry";
    }

    /// The window's opening size, in logical pixels.
    ///
    /// Still the size that matters when [#maximized] is true: it is what the
    /// window restores to when the user un-maximizes it.
    default LogicalSize size() {
        return new LogicalSize(960, 640);
    }

    /// The smallest the user may drag the window, in logical pixels.
    ///
    /// A zero size — the default — is "no minimum", and it is the only honest
    /// default: the toolkit does not know what the window contains, and a floor
    /// invented for it would be wrong for a palette and wrong again for an
    /// editor. An application does know, which is why this is one line to
    /// override:
    ///
    /// ```java
    /// @Override public LogicalSize minimumSize() {
    ///     return LogicalSize.of(640, 480);
    /// }
    /// ```
    ///
    /// A zero on one axis constrains only the other, so a window that cares about
    /// its width alone says `LogicalSize.of(480, 0)`.
    ///
    /// @throws IllegalArgumentException — from [Window#open] — if it is larger
    ///         than [#size] on either axis, which is a window that could not open
    ///         at the size it asked for
    default LogicalSize minimumSize() {
        return WindowSpec.NO_MINIMUM;
    }

    /// Whether the window opens filling the desktop's work area.
    ///
    /// A *state* the desktop owns rather than a large [#size]: it snaps to the
    /// work area rather than to the whole display, stays clear of panels and
    /// docks, and restores to [#size] when the user un-maximizes it. Asking for a
    /// screen-sized window instead gives one that is too big on a laptop and that
    /// no titlebar button can put back.
    ///
    /// False by default, which is the right default for a tool: an application
    /// that takes the whole screen without being asked is one the user has to
    /// undo before they can see anything else. A gallery whose whole subject is
    /// how much fits on a screen is the case for saying otherwise.
    default boolean maximized() {
        return false;
    }

    /// Where the window's top-left opens, in the desktop's coordinates — what
    /// [Window#position()] read when it last closed.
    ///
    /// ```java
    /// @Override public Optional<LogicalPoint> position() {
    ///     return settings.windowPosition();
    /// }
    /// ```
    ///
    /// Clamped onto a display that exists, so a position saved on a monitor
    /// that has since been unplugged still opens on the screen. Empty by
    /// default, which leaves it to the platform; ignored on Wayland, which
    /// places every window itself.
    ///
    /// Read more: [Where a window opens](https://goldberry.dev/docs/guide/windows.html#where-a-window-opens).
    default Optional<LogicalPoint> position() {
        return Optional.empty();
    }

    /// The name of the display the window opens on — what
    /// [Window#display()] named when it last closed.
    ///
    /// Centred on that display when there is no [#position()], and the
    /// fallback when the position is on no display any more. Empty by default.
    default Optional<String> display() {
        return Optional.empty();
    }

    /// The stylesheets, in cascade order — the toolkit's, then the theme's, then
    /// the application's own.
    ///
    /// Re-read only when [Host#restyle] asks for it, and **not** every frame: a
    /// list rebuilt per frame would rebuild the renderer per frame, and the
    /// renderer is what caches the resolved styles. Switching a theme is
    /// therefore two lines — set the field, call `restyle()` — and switching
    /// nothing costs nothing.
    default List<Stylesheet> stylesheets() {
        return List.of();
    }

    /// The window's icon — the picture the taskbar, the dock and the window
    /// switcher show — as several sizes of one image.
    ///
    /// ```java
    /// @Override public List<Image> icon() {
    ///     return Stream.of(16, 32, 48, 256)
    ///             .map(size -> Image.decode(read("icons/app-" + size + ".png")))
    ///             .toList();
    /// }
    /// ```
    ///
    /// Read once, as the window opens and before [#start]. [Image] rather than a
    /// path, because `Image.decode` already exists and an application decodes from
    /// its own resources. A list, because each platform wants its own sizes and a
    /// scaled-down PNG is a blurred one. The toolkit picks which size the platform
    /// scales from.
    ///
    /// Empty by default, which leaves the platform's generic icon. On macOS the
    /// dock shows the application bundle's icon whatever this says, and on Wayland
    /// a compositor without `xdg-toplevel-icon` shows the desktop file's. Installer
    /// packaging covers both.
    default List<Image> icon() {
        return List.of();
    }

    /// The application's own cursors: a picture for a shape, at each size it
    /// was drawn at.
    ///
    /// ```java
    /// @Override public List<CursorImage> cursors() {
    ///     return Stream.of(32, 48, 64, 96)
    ///             .map(size -> new CursorImage(Cursor.GRAB,
    ///                     Image.decode(read("cursors/grab-" + size + ".png")), size * 3 / 8, size / 8))
    ///             .toList();
    /// }
    /// ```
    ///
    /// The shapes stay CSS's, so `cursor: grab` in a stylesheet, a widget's
    /// own `pointer` and [Window#cursor(Cursor)] all show the application's
    /// picture for the shape they name, and a shape with no picture shows the
    /// platform's. The smallest size of a shape is the shape at 100%, and the
    /// toolkit shows the one the display's scale wants.
    ///
    /// Read once, as the first window opens and before [#start]. Empty by
    /// default, which leaves every shape to the platform. A backend with no
    /// pointer, or none that can show a picture, keeps the platform's shapes.
    default List<CursorImage> cursors() {
        return List.of();
    }

    /// The faces this application ships, added to the families `font-family` can
    /// name.
    ///
    /// ```java
    /// @Override public List<FontSource> fonts() {
    ///     return List.of(FontSource.stream("Forum", 400, Style.UPRIGHT,
    ///             () -> MyApp.class.getResourceAsStream("fonts/Forum-Regular.ttf")));
    /// }
    /// ```
    ///
    /// The lambda is the application's code, so it reads the file with the
    /// application's own access, and a modular application needs no `opens`
    /// for it. `FontSource.resource` reads it with the toolkit's instead, and
    /// then the package holding the file must be opened to `dev.goldberry.core`.
    ///
    /// Read **once**, before [#start], when the window's font book is opened. A
    /// face reaches the cascade, paragraph layout, a field's caret and the glyph
    /// cache together, because all four already share that book. The bundled
    /// families are searched first, so a file called `Inter` does not replace the
    /// face the design system's metrics were drawn against. Every file is looked
    /// for as the book opens, and one that is not there is a warning at start
    /// naming the face; text in that family is drawn in the UI face instead.
    ///
    /// Empty by default: the bundled faces are the whole design system.
    default List<FontSource> fonts() {
        return List.of();
    }

    /// The view models this window shows, if any.
    ///
    /// ```java
    /// private final Settings settings = new Settings();
    ///
    /// @Override public List<Object> models() {
    ///     return List.of(settings);
    /// }
    /// ```
    ///
    /// Naming them here is the whole of the wiring. The toolkit subscribes: a
    /// change to any `@Bind` field asks this window for a frame, and a change to
    /// one declared `@Bind(restyle = true)` asks for a restyle first. An
    /// application says nothing about repainting, which is the point: a model that
    /// changed and a window that did not repaint is the bug this wiring exists to
    /// make impossible.
    ///
    /// **A list**, because a window's own actions — "open the menu", "toggle the
    /// HUD" — belong to the window rather than to the view model, and an
    /// application that keeps two objects should not have to merge them by hand.
    ///
    /// A model here is also what the markup inflater resolves a document's `bind=`
    /// and `press=` against, so the same list answers both questions. See
    /// [Values](https://goldberry.dev/docs/applications.html#values).
    ///
    /// Opt out per model with `@Model(repaint = false)` — for one driving a
    /// background job, where every write would wake a window with nothing new to
    /// draw.
    default List<Object> models() {
        return List.of();
    }

    /// Called once on the UI thread, before [#root()] and before the first frame.
    ///
    /// Where an application opens the native resources it owns, registers
    /// accelerators, and keeps the [Host] if it needs one later. A widget is a
    /// value that is rebuilt and thrown away, so anything with a `close()` is
    /// opened here and not in a build.
    default void start(Host host) {}

    /// Called once after the event loop ends, in the reverse order of [#start] —
    /// after the widget tree is unmounted and before the toolkit shuts down.
    ///
    /// Whatever `start` opened is closed here. The launcher closes what the
    /// launcher opened, and nothing else: it cannot know that an `Icon` in a
    /// field is still referenced by a widget that has not been collected.
    default void stop() {}
}
