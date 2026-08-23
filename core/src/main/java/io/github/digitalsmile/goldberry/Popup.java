package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.render.popup.BackendPopup;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.digitalsmile.goldberry.paint.Frame;

/// A widget tree in a platform window of its own — a menu, a dropdown, a
/// tooltip.
///
/// The widget layer over [BackendPopup]
/// ([ADR-0102](../../../../../book/src/adr/0102-a-popup-is-a-window-the-platform-may-refuse.md)),
/// and the answer to the one thing the in-window overlay layer cannot do: leave
/// the window. A dropdown near the bottom of a window is routinely taller than
/// the space below its button, and clipped to the window a nine-item list shows
/// four ([ADR-0100](../../../../../book/src/adr/0100-a-window-has-a-layer-above-its-application.md)).
///
/// ```java
/// host.popup(menu(), LogicalPoint.of(24, 120), LogicalSize.of(180, 132))
///     .ifPresent(open -> this.menu = open);
/// ```
///
/// ## It has trees of its own
///
/// A popup is a second window, so it gets a second element tree, a second render
/// tree and a second pointer router. What it **shares** is the renderer — the
/// stylesheets, the font book and the frame clock — so a popup is themed by the
/// same cascade as the window it belongs to, restyles with it, and animates on
/// the same tick.
///
/// The cost of separate trees is that a popup's contents do not inherit anything
/// from the widget that opened them: they are a root, not a descendant. For a
/// menu that is right — its items are a list, not part of the button's subtree —
/// and for a `tooltip` that wants the styling of the thing it is describing it is
/// a limitation to answer when that widget is built.
///
/// ## Light dismissal
///
/// On by default: a press anywhere in the owner window, or `Escape`, closes the
/// popup. That is `docs/core-widgets.md` §7's rule for a `popover`, and it is
/// here rather than in a widget because it needs input the owner window's router
/// deliberately does not deliver — a press on *nothing* dispatches to nothing,
/// and light dismissal is exactly the case where nothing was hit.
///
/// Confined to the UI thread, and closed either by [#close()] or by the window
/// that owns it going away.
public final class Popup implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Popup.class);

    private final BackendPopup backend;
    private final Window window;
    private final ElementTree tree;
    private final RenderTree render;
    private final PointerRouter router;
    private final Supplier<WidgetRenderer> renderer;
    private final Runnable onClosed;

    private boolean lightDismiss = true;
    private boolean closed;

    /// Whether [#focusFirst] has run. See there.
    private boolean focused;

    /// Which node to put the keyboard on when this popup first paints, or null
    /// for "the first focusable one". See [#focusOn].
    private String focusId;

    /// The last painted frame's geometry — see [#anchor].
    private java.util.List<HitTest.Region> regions = java.util.List.of();

    /// The trees are handed in rather than built here because the content has
    /// usually been **measured** already — that is how the popup got a size — and
    /// measuring builds both. A second element tree would also be a second lot of
    /// `initState`.
    Popup(BackendPopup backend, Window window, ElementTree tree, RenderTree render,
            Supplier<WidgetRenderer> renderer, Runnable onClosed) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.window = Objects.requireNonNull(window, "window");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.onClosed = Objects.requireNonNull(onClosed, "onClosed");
        this.tree = Objects.requireNonNull(tree, "tree");
        // A popup's tree paints into a popup's window, exactly as the owner's
        // does into its own — a `setState` in a menu item is as much a reason for
        // a frame as one in the application (ADR-0122).
        tree.onDirty(window::repaint);
        this.render = Objects.requireNonNull(render, "render");
        this.router = new PointerRouter();

        router.focusRoot(tree.root());
        window.pointerRouter(router);
        window.onPaint(this::paint);
        // `Escape` on the popup's *own* window: once a menu has taken focus, the
        // key goes to it rather than to the window that opened it, so the owner's
        // watcher never sees it. A press inside is deliberately not watched — that
        // is someone choosing an item, not dismissing the menu.
        window.inputWatcher(new Window.InputWatcher() {
            @Override
            public boolean pressed(
                    io.github.digitalsmile.goldberry.input.event.PointerEvent.Button button,
                    float x, float y) {
                // A press inside a popup is somebody choosing an item.
                return false;
            }

            @Override
            public boolean keyPressed(io.github.digitalsmile.goldberry.input.key.Key key,
                    io.github.digitalsmile.goldberry.input.key.Modifiers modifiers, boolean repeat) {
                if (key == io.github.digitalsmile.goldberry.input.key.Key.ESCAPE) {
                    dismissedByInput();
                    return true;
                }
                // Everything else goes to this popup's own router, which is
                // where it was going anyway — the watcher only exists here for
                // the key no widget owns.
                return false;
            }
        });
    }

    /// The window behind this popup, for the handful of things a caller may need
    /// from it — the cursor, a resize notification. Named as the escape hatch it
    /// is, exactly like [Host#window()].
    public Window window() {
        return window;
    }

    /// The painted rectangle of a node **inside this popup**, in the owner
    /// window's coordinates.
    ///
    /// What a submenu is anchored to. `Host.anchor` answers from the main
    /// window's geometry and knows nothing about what is in a popup, so a menu
    /// item that opens a submenu has to ask the popup it is in — and the answer
    /// is translated by this popup's own offset, because that is the space
    /// `Host.popup` places in.
    public java.util.Optional<LogicalRect> anchor(
            String id) {
        Objects.requireNonNull(id, "id");
        for (var region : regions) {
            if (region.owner() instanceof io.github.digitalsmile.goldberry.widget.Element element
                    && element.widget() instanceof io.github.digitalsmile.goldberry.widget.style.Styled styled
                    && id.equals(styled.id())) {
                var bounds = region.bounds();
                return java.util.Optional.of(
                        bounds.offsetBy(backend.offset().x(), backend.offset().y()));
            }
        }
        return java.util.Optional.empty();
    }

    /// This popup's whole rectangle, in the owner window's coordinates.
    ///
    /// [#anchor] answers for a node *inside* the popup; this is the popup itself,
    /// which is what a submenu is placed beside. The difference is the panel's
    /// padding and its border — an item's right edge is a few pixels inside the
    /// menu's, so a submenu anchored to the item overlaps the border of the menu
    /// it came from ([ADR-0113](../../../../../book/src/adr/0113-a-submenu-is-placed-beside-its-menu.md)).
    public LogicalRect bounds() {
        return new LogicalRect(
                backend.offset(), window.size());
    }

    /// Where the popup sits, as an offset from its owner window's top-left.
    public LogicalPoint offset() {
        return backend.offset();
    }

    /// Moves the popup, in the owner's logical coordinates.
    ///
    /// What a `popover` does when its anchor scrolls: cheaper than closing and
    /// reopening, and it does not flicker.
    public void move(LogicalPoint position) {
        requireOpen();
        backend.move(position);
    }

    /// Asks for the popup to be resized.
    ///
    /// **A request.** The window manager decides when it happens, so the size may
    /// still be the old one immediately afterwards — see [BackendPopup#resize].
    public void resize(LogicalSize size) {
        requireOpen();
        backend.resize(size);
        window.repaint();
    }

    /// Whether a press outside this popup, or `Escape`, closes it. On by default.
    ///
    /// Off for a popup the application closes itself — a menu that stays open
    /// while its owner is being dragged, or one under a test's control.
    public Popup lightDismiss(boolean value) {
        this.lightDismiss = value;
        return this;
    }

    /// See [#lightDismiss(boolean)].
    public boolean isLightDismissed() {
        return lightDismiss;
    }

    public boolean isOpen() {
        return !closed && backend.isOpen();
    }

    /// Shows this popup something new, without closing it.
    ///
    /// A `select multiple` is what needed it: its list stays open while values
    /// are picked, so the rows have to follow a model that moves under them
    /// ([ADR-0182](../../../book/src/adr/0182-a-select-may-hold-more-than-one.md)).
    /// Reconciled from the root rather than rebuilt, so the keyboard keeps its
    /// place and nothing flickers.
    ///
    /// @param content the new description — the same kind of widget the popup was
    ///                opened with, see [io.github.digitalsmile.goldberry.widget.ElementTree#update]
    public void content(Widget content) {
        if (!isOpen()) {
            return;
        }
        tree.update(Objects.requireNonNull(content, "content"));
        resizeToContent();
        window.repaint();
    }

    /// Measures what the tree now describes and asks the window for that size.
    ///
    /// **A popup was measured once, when it opened, and never again.** For a menu
    /// that is right — its content does not change. For the two things that now
    /// re-describe themselves it was a bug with no workaround: a `tree` whose
    /// branch expanded drew its new rows into a window still the height of the
    /// collapsed one, so they were simply not there, and no viewport appeared
    /// either because the fit that would have added one runs at open time
    /// ([ADR-0186](../../../book/src/adr/0186-a-panel-that-hangs-off-a-field-is-not-a-menu.md)).
    ///
    /// The fit is re-applied with the measurement, which is what puts the
    /// viewport in when the content outgrows the screen rather than only when it
    /// was already too big.
    ///
    /// A **request**, like every resize: the window manager decides when it
    /// happens ([BackendPopup#resize]).
    private void resizeToContent() {
        if (measurer == null) {
            return;
        }
        var size = measurer.measure(tree, render);
        if (size != null && !size.equals(window.size())) {
            resize(size);
        }
    }

    /// How this popup measures itself again — the launcher's, because measuring
    /// needs the window's scale and the `Fit` this popup was opened with, and
    /// neither belongs here.
    @FunctionalInterface
    interface Measurer {

        /// @return the size the content wants now, or null if it cannot be taken
        LogicalSize measure(ElementTree tree, RenderTree render);
    }

    private Measurer measurer;

    /// Told by the launcher, which opened this and knows how it was measured.
    void measuredBy(Measurer value) {
        this.measurer = value;
    }

    /// Asks for another frame.
    public void repaint() {
        if (isOpen()) {
            window.repaint();
        }
    }

    /// Closes the popup and takes its trees down with it. Idempotent.
    ///
    /// The reverse of the order they were built in, for
    /// [Launcher#shutDown]'s reason: the render tree holds Yoga measure callbacks
    /// that close over paragraphs, and a paragraph over a font.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            tree.unmount();
            render.close();
            window.close();
        } finally {
            onClosed.run();
        }
        LOG.debug("popup closed");
    }

    /// One frame of this popup, painted by the same renderer as its owner.
    private void paint(Frame frame) {
        if (tree.needsBuild()) {
            tree.flush();
            // **And then measure again.** A popup's content can change without
            // anything outside it knowing: a `tree` expanding a branch is a
            // `setState` in the popup's *own* tree, which flushes here and never
            // reaches the widget that opened the popup. Measuring only in
            // `content` therefore missed exactly the case that needed it — the
            // new rows were drawn into a window still the height of the
            // collapsed one
            // ([ADR-0187](../../../book/src/adr/0187-a-panel-takes-the-pointer-and-leaves-the-keyboard.md)).
            resizeToContent();
        }
        var current = renderer.get();
        // Transparent, not a colour: a popup's panel has rounded corners, and
        // what is outside them is nothing rather than black. It also stops a
        // shrinking popup leaving the previous frame in the margin.
        frame.fill(0x00000000);
        render.update(frame, current.render(tree));
        // Full-frame, unlike the owner window's: a popup is small, it is repainted
        // only when something in it changed, and damage tracking would be
        // machinery for a saving nobody can measure on a 180×132 menu.
        render.paint(frame);
        regions = HitTest.capture(render);
        router.windowBounds(LogicalRect.of(
                0, 0, frame.size().width(), frame.size().height()));
        router.updateRegions(regions);
        if (!focused) {
            focused = true;
            focusFirst();
        }
        if (current.isAnimating()) {
            window.repaint();
        }
    }

    /// Handles a key the **owner** window received while this popup was open.
    ///
    /// A popup may or may not have the platform's keyboard focus — SDL will give
    /// a `POPUP_MENU` window focus on some drivers and not on others, and a
    /// tooltip must never have it. So the owner forwards, and a menu is operable
    /// by arrows either way ([ADR-0104]).
    ///
    /// @return whether the popup's router did something with it
    boolean handleKey(io.github.digitalsmile.goldberry.input.key.Key key,
            io.github.digitalsmile.goldberry.input.key.Modifiers modifiers, boolean repeat) {
        if (!isOpen()) {
            return false;
        }
        var handled = router.keyPressed(key, modifiers, repeat);
        if (handled) {
            window.repaint();
        }
        return handled;
    }

    /// Opens with the keyboard on the node carrying this `id`, rather than on the
    /// first focusable one.
    ///
    /// What a control that has *already chosen* needs. A `select` showing its
    /// third option opens a list whose third row is the one an arrow should move
    /// from; starting at the first row makes `Down` mean "go to the second
    /// option" whatever the value was, which is a control that loses the user's
    /// place every time they open it
    /// ([ADR-0141](../../../../../book/src/adr/0141-a-select-is-a-closed-control-and-a-list.md)).
    ///
    /// Called between [Host#popup] returning and the first frame, which is the
    /// only window there is: the focus is placed after that frame, because
    /// traversal walks an element tree that does not exist until then.
    ///
    /// A node that is not there when the frame comes falls back to the first
    /// focusable one, because a list that opened with no focus at all would
    /// ignore the first arrow key.
    ///
    /// @param id the node to focus, or null to restore the default
    public void focusOn(String id) {
        this.focusId = id;
    }

    /// Whether this popup takes the keyboard when it opens. On by default.
    ///
    /// **Off for a popup that hangs off something the user is typing in.** A
    /// suggestion list under a combobox must not take the keyboard: the field is
    /// what is being typed into, and a list that focused its first row on opening
    /// swallowed the second keystroke and every one after it — which is what "it
    /// allows only one character and then is disabled" was
    /// ([ADR-0185](../../../book/src/adr/0185-a-list-that-hangs-off-a-field-does-not-take-the-keyboard.md)).
    ///
    /// The arrows still reach it. A popup may or may not have the platform's
    /// focus either way, so the owner forwards keys to whatever popup is open
    /// ([ADR-0104](../../../book/src/adr/0104-a-popup-is-measured-then-placed.md))
    /// — which is the mechanism that makes this safe rather than a compromise.
    public Popup takesFocus(boolean value) {
        this.takesFocus = value;
        return this;
    }

    private boolean takesFocus = true;

    /// Puts the keyboard on this popup's first focusable node.
    ///
    /// Called after its first frame, because focus traversal walks the element
    /// tree and the tree is not built until then. A menu whose first item is not
    /// focused would answer `Down` by focusing the first item — one keystroke
    /// later than every menu anywhere else.
    private void focusFirst() {
        // Not "from the keyboard": nobody pressed anything, and a menu whose
        // first row is lit before the user has touched a key looks like a menu
        // that has already chosen (ADR-0112). The row is focused so that an arrow
        // has somewhere to start; `:focus-visible` is what draws it, and the
        // first arrow press is what sets that.
        //
        // The same `false` for a chosen node, and it matters more there: a
        // `select` row focused *from the keyboard* would be selected on the spot
        // by the option's own follow-the-focus rule, so opening the list would
        // report a change nobody asked for.
        if (!takesFocus) {
            return;
        }
        var chosen = focusId == null ? null : elementWithId(tree.root(), focusId);
        if (chosen != null) {
            router.focus(chosen, false);
            return;
        }
        router.moveFocus(1, false);
    }

    /// The element in this popup's tree carrying `id`, or null.
    ///
    /// A walk rather than a lookup in [#regions], which [#anchor] uses: focus is
    /// placed *before* anything asks where a node was painted, and a region
    /// carries a rectangle where this needs the element itself.
    private static io.github.digitalsmile.goldberry.widget.Element elementWithId(
            io.github.digitalsmile.goldberry.widget.Element element, String id) {
        if (element.widget() instanceof io.github.digitalsmile.goldberry.widget.style.Styled styled
                && id.equals(styled.id())) {
            return element;
        }
        for (var child : element.children()) {
            var found = elementWithId(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /// Called by the launcher when the owner window sees input the popup should
    /// be dismissed by.
    void dismissedByInput() {
        if (lightDismiss) {
            close();
        }
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw new IllegalStateException("this popup has been closed");
        }
    }

    @Override
    public String toString() {
        return "Popup[" + backend.kind() + " at " + backend.offset()
                + (isOpen() ? "" : ", closed") + "]";
    }
}
