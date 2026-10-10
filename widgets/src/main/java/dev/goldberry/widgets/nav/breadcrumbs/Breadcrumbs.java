package dev.goldberry.widgets.nav.breadcrumbs;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// The path to here: a row of crumbs, each a way back up the path, with the
/// last one where you are.
///
/// ```kdl
/// breadcrumbs {
///     crumb icon="home" press="app.go-home" "Home"
///     crumb press="app.go-library" "Library"
///     crumb press="app.go-shelf" "Reference"
///     crumb "The Red Book"
/// }
/// ```
///
/// ## The last crumb is where you are, and it is not a link
///
/// It is the one rule a trail cannot get wrong: a current page
/// that looked pressable would invite a click that reloads what is already on
/// screen. So the **trail** decides which crumb is current — the last one — and
/// writes it down on every build, exactly as a `tabs` tells a `tab` that it is
/// selected. A document cannot say otherwise, because a document that marked two
/// crumbs current, or none, would break the only invariant this widget holds.
///
/// A current crumb is not focusable and raises nothing, whatever `press=` it was
/// written with. That is a deliberate silent demotion rather than a refusal: a
/// trail is nearly always built from a loop over a path, so every crumb gets the
/// same `press=` and the last one is *supposed* to be inert. Refusing it would
/// make the common case an error.
///
/// ## Overflow is a menu, not an ellipsis inside a word
///
/// Overflow collapses the middle into a `…` that opens a `menu` of the hidden
/// crumbs, rather than eliding characters: a truncated folder name is worse
/// than a hidden one, because it looks like a name.
///
/// So past [#collapseAfter] crumbs the trail shows the **first**, a `…`, and the
/// last `collapseAfter - 2` — which keeps the number of things on the row at
/// exactly `collapseAfter`, counting the `…` as one. The first stays because
/// "where does this tree start" is the question a deep path makes hardest, and
/// the tail stays because that is where you are.
///
/// ## One crumb too wide is the current crumb's ellipsis
///
/// The menu counts crumbs, so it cannot answer a trail of two whose last name is
/// longer than the row — `Chat › #platform-announcements` in a narrow header.
/// That one case is answered by the **current** crumb alone: it is the one crumb
/// that may be narrower than its label, and it is cut with a `…` before the trail
/// would paint past its own edge. The rule above still holds for every crumb
/// before it, which keeps its whole name; the current crumb can be shortened
/// because it is never hidden in the menu, and the page under the trail
/// carries its name in full anyway. It is the stylesheet's
/// (`crumb:checked { flex-shrink: 1 }`), so an application that would rather
/// clip, or shrink another crumb, says so in CSS.
///
/// The `…` opens a [dev.goldberry.widgets.menu.Menu] of what
/// it is hiding, in order, each row doing what that crumb's `press` does. It
/// needs a window to open a popup in, which is the whole reason this widget is
/// stateful and its appearance is a node of its own — see [BreadcrumbsState].
///
/// ## This node styles nothing
///
/// `breadcrumbs` as a **CSS type** is [CrumbTrail], the node this one builds.
/// [dev.goldberry.widgets.panel.tabs.Tabs]'s arrangement and
/// its reason: a stateful widget that was also styled would put two `breadcrumbs`
/// nodes in the cascade, one inside the other, and every rule in `controls.css`
/// would apply to both — a doubled padding and a doubled height waiting to happen.
///
/// Read more: [Navigation](https://goldberry.dev/docs/components/navigation.html#breadcrumbs).
///
/// @param children      the crumbs, as written. Anything that is not a [Crumb] is
///                      drawn in the row and left alone, which is how a `badge`
///                      or a `spacer` gets into a trail
/// @param collapseAfter how many things the row may hold before the middle
///                      collapses; four, unless told otherwise. Zero or
///                      less means never collapse, which is what a trail in a
///                      window with room to spare wants
/// @param attributes    `id` and `class`, exactly as on every other widget
@Markup("breadcrumbs")
public record Breadcrumbs(List<Widget> children, int collapseAfter, Attributes attributes)
        implements Widget.Stateful, Attributed<Breadcrumbs> {

    /// How many things a row holds before its middle collapses into a menu.
    ///
    /// Four and not three because three is `Home … Here`, which has collapsed a
    /// single crumb into a control that is the same width — a menu that saves
    /// nothing and costs a click.
    public static final int DEFAULT_COLLAPSE_AFTER = 4;

    /// The fewest a collapsing trail can show: the first, the `…`, and the
    /// current page.
    ///
    /// A `collapseAfter` under this would ask for a row that cannot contain where
    /// you are, so it is raised rather than refused — the number is a *hint about
    /// width*, and a hint that cannot be met should be met as closely as possible
    /// rather than stop the window opening.
    static final int MINIMUM_COLLAPSE_AFTER = 3;

    /// Written out so that the parameters taking null for a default can say so.
    public Breadcrumbs(@Nullable List<Widget> children, int collapseAfter, @Nullable Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.children = children;
        this.collapseAfter = collapseAfter;
        this.attributes = attributes;
    }

    /// A trail of crumbs, collapsing at four.
    public Breadcrumbs(Widget... children) {
        this(List.of(children), DEFAULT_COLLAPSE_AFTER, Attributes.NONE);
    }

    /// This trail collapsing at a different width, or never — see the parameter.
    public Breadcrumbs collapseAfter(int value) {
        return new Breadcrumbs(children, value, attributes);
    }

    @Override
    public Breadcrumbs withAttributes(Attributes value) {
        return new Breadcrumbs(children, collapseAfter, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// The crumbs as written, before the trail rebuilt them with what only it
    /// knows — for a test, and for an application that wants to count them.
    public List<Widget> rawCrumbs() {
        return children;
    }

    @Override
    public State<?> createState() {
        return new BreadcrumbsState();
    }

    /// Builds a `breadcrumbs` from markup.
    ///
    /// Which crumb is current is **not** an attribute, for the reason above: the
    /// trail writes it, and a document that could write it would be able to say
    /// something no trail is allowed to be.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        Objects.requireNonNull(node, "node");
        var collapse = (int) node.numberProperty("collapse-after", DEFAULT_COLLAPSE_AFTER);
        return new Breadcrumbs(children, collapse, Attributes.of(node));
    }
}
