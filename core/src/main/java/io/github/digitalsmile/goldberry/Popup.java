package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.BackendPopup;
import io.github.digitalsmile.goldberry.backend.LogicalPoint;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.layout.RenderTree;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                    io.github.digitalsmile.goldberry.input.PointerEvent.Button button,
                    float x, float y) {
                // A press inside a popup is somebody choosing an item.
                return false;
            }

            @Override
            public boolean keyPressed(io.github.digitalsmile.goldberry.input.Key key,
                    io.github.digitalsmile.goldberry.input.Modifiers modifiers, boolean repeat) {
                if (key == io.github.digitalsmile.goldberry.input.Key.ESCAPE) {
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
    public java.util.Optional<io.github.digitalsmile.goldberry.backend.LogicalRect> anchor(
            String id) {
        Objects.requireNonNull(id, "id");
        for (var region : regions) {
            if (region.owner() instanceof io.github.digitalsmile.goldberry.widget.Element element
                    && element.widget() instanceof io.github.digitalsmile.goldberry.widget.Styled styled
                    && id.equals(styled.id())) {
                var bounds = region.bounds();
                return java.util.Optional.of(
                        bounds.offsetBy(backend.offset().x(), backend.offset().y()));
            }
        }
        return java.util.Optional.empty();
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
        }
        var current = renderer.get();
        render.update(frame, current.render(tree));
        // Full-frame, unlike the owner window's: a popup is small, it is repainted
        // only when something in it changed, and damage tracking would be
        // machinery for a saving nobody can measure on a 180×132 menu.
        render.paint(frame);
        regions = HitTest.capture(render);
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
    boolean handleKey(io.github.digitalsmile.goldberry.input.Key key,
            io.github.digitalsmile.goldberry.input.Modifiers modifiers, boolean repeat) {
        if (!isOpen()) {
            return false;
        }
        var handled = router.keyPressed(key, modifiers, repeat);
        if (handled) {
            window.repaint();
        }
        return handled;
    }

    /// Puts the keyboard on this popup's first focusable node.
    ///
    /// Called after its first frame, because focus traversal walks the element
    /// tree and the tree is not built until then. A menu whose first item is not
    /// focused would answer `Down` by focusing the first item — one keystroke
    /// later than every menu anywhere else.
    private void focusFirst() {
        router.moveFocus(1);
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
