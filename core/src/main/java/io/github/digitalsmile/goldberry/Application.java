package io.github.digitalsmile.goldberry;

import java.util.List;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;
import io.github.digitalsmile.goldberry.text.font.FontSource;
import io.github.digitalsmile.goldberry.widget.Widget;

/// What a Goldberry application implements. Everything else is
/// [Goldberry#launch].
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
/// ## What the launcher owns, so this does not
///
/// A window, a font book, the element tree, the render tree, the widget
/// renderer, the pointer router, the frame loop, damage tracking, the hit-test
/// snapshot fed from the painted frame, the idle rule, and the shutdown order —
/// which is the part that is easy to get subtly wrong, because a render object
/// holds a measure callback that closes over a paragraph that closes over a font,
/// and closing them in the wrong order reads unmapped memory.
///
/// None of that is a decision an application makes differently, and every one of
/// them was fifteen lines of the showcase before this interface existed
/// (ADR-0093).
///
/// ## What is left to the application
///
/// The widget tree, the stylesheets, and the native resources only it knows it
/// needs — an [io.github.digitalsmile.goldberry.icon.Icon] is the usual one, and
/// [#start] and [#stop] are where it opens and closes them. Everything on this
/// interface but [#root()] has a default, so the smallest application is one
/// method.
public interface Application {

    /// The widget at the root of the window.
    ///
    /// Called **once**, on the UI thread, after [#start]. It is not a build
    /// method: what happens next is that the element tree mounts it, and every
    /// later rebuild comes from a `setState` inside it. An application whose
    /// whole window changes puts a [io.github.digitalsmile.goldberry.widget.Widget.Stateful]
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
    /// override (ADR-0304):
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
    /// no titlebar button can put back
    /// (ADR-0221).
    ///
    /// False by default, which is the right default for a tool: an application
    /// that takes the whole screen without being asked is one the user has to
    /// undo before they can see anything else. A gallery whose whole subject is
    /// how much fits on a screen is the case for saying otherwise.
    default boolean maximized() {
        return false;
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
    /// scales from (`docs/gaps.md` G40, ADR-0351).
    ///
    /// Empty by default, which leaves the platform's generic icon. On macOS the
    /// dock shows the application bundle's icon whatever this says, and on Wayland
    /// a compositor without `xdg-toplevel-icon` shows the desktop file's. Installer
    /// packaging covers both.
    default List<Image> icon() {
        return List.of();
    }

    /// The faces this application ships, added to the families `font-family` can
    /// name.
    ///
    /// ```java
    /// @Override public List<FontSource> fonts() {
    ///     return List.of(FontSource.resource(
    ///             "Forum", Weight.REGULAR, Style.UPRIGHT, MyApp.class, "fonts/Forum-Regular.ttf"));
    /// }
    /// ```
    ///
    /// Read **once**, before [#start], when the window's font book is opened. A
    /// face reaches the cascade, paragraph layout, a field's caret and the glyph
    /// cache together, because all four already share that book. The bundled
    /// families are searched first, so a file called `Inter` does not replace the
    /// face the design system's metrics were drawn against. A face whose bytes
    /// cannot be read is logged once and drawn in the UI face (`docs/gaps.md`
    /// G39, ADR-0349).
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
    /// application says nothing about repainting, which is the point — a model
    /// that changed and a window that did not repaint was the failure the old
    /// `changed()` line existed to prevent and regularly failed to
    /// (ADR-0128,
    /// ADR-0133).
    ///
    /// **A list**, because a window's own actions — "open the menu", "toggle the
    /// HUD" — belong to the window rather than to the view model, and an
    /// application that keeps two objects should not have to merge them by hand.
    ///
    /// A model here is also what
    /// `io.github.digitalsmile.goldberry.widgets` resolves a document's `bind=`
    /// and `press=` against, so the same list answers both questions.
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
