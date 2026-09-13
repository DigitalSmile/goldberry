package io.github.digitalsmile.goldberry.widgets.nav.breadcrumbs;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.handler.Located;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The `…` that stands for the crumbs a trail is not showing — a **part**, and
/// the one that opens a menu.
///
/// §6 asks for exactly this rather than an elided label: "a truncated folder name
/// is worse than a hidden one, because it looks like a name". So nothing is
/// shortened — some crumbs are simply not on the row, and this is how you get at
/// them.
///
/// ## It is focusable, unlike every other part in the catalog
///
/// `tab-close`, `select-chip-remove` and `chip-dismiss` are all deliberately not
/// Tab stops, because each has a keyboard route through the control it sits in.
/// This one has none: the crumbs behind it are **not in the tree**, so there is
/// no node for the keyboard to reach and no key on the trail that could stand for
/// "the fourth of the hidden ones". A `…` that only a pointer could open would
/// make part of a navigation path unreachable without a mouse, which §13 does not
/// allow.
///
/// [Role#MENU_BUTTON] says so to anything listening — "the heading of a menu",
/// which is what this is.
///
/// ## Where the menu opens
///
/// It is told where it was drawn through [Located] and hands the rectangle to
/// [BreadcrumbsState], which is what [io.github.digitalsmile.goldberry.widgets.menu.Menus]
/// needs to place a popup. A position rather than an id, so two trails in one
/// window need no ids to tell their overflows apart — and the rectangle is the
/// **painted** one, which is where the user is looking (ADR-0270).
///
/// @param onOpen  what to ask when it is pressed
/// @param located told where this node ended up, once a frame and only when it
///                changes
record CrumbOverflow(Runnable onOpen, BiConsumer<LogicalRect, LogicalRect> located)
        implements Widget.Leaf, Styled, Paints, Handles, Located, Semantics {

    /// Three full stops would kern as three full stops. The single character is
    /// one glyph in every font this toolkit bundles, and it is what the label of
    /// this control actually is.
    private static final String ELLIPSIS = "…";

    @Override
    public String cssType() {
        return "crumb-overflow";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public void located(LogicalRect self, LogicalRect clip) {
        if (located != null) {
            located.accept(self, clip);
        }
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            open();
            event.consume();
        }
    }

    /// `Space`, `Enter` and `Down` — the last because that is what opens every
    /// other menu in this toolkit, and a control that says "there is a list under
    /// me" should answer the key that means "show me the list".
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE || event.key() == Key.ENTER || event.key() == Key.DOWN) {
            open();
            event.consume();
        }
    }

    private void open() {
        if (onOpen != null) {
            onOpen.run();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(Box.text(context.paragraph(style, ELLIPSIS), style.color()));
    }

    @Override
    public Role role() {
        return Role.MENU_BUTTON;
    }

    /// Not "…", which is what the label reads and what nothing could act on. A
    /// name is the one thing a screen reader has to work with, and "three
    /// dots" is not a place you can go.
    @Override
    public String accessibleName() {
        return "Show the rest of the path";
    }
}
