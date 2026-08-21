package io.github.digitalsmile.goldberry.widgets.panel.collapse;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Markup;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// A header and a body that folds away — `docs/core-widgets.md` §5's `collapse`.
///
/// ```kdl
/// collapse title="Advanced" {
///     row { text "Timeout"; spacer; slider bind="timeout" }
///     row { text "Retries"; spacer; slider bind="retries" }
/// }
/// ```
///
/// ```java
/// new Collapse("Advanced", advancedSettings())
/// new Collapse("Advanced", open, this::setOpen, advancedSettings())   // controlled
/// ```
///
/// ## The body is unmounted while closed, not hidden
///
/// §5 is explicit and the reason is the whole argument for a widget tree: "a
/// collapsed section that kept a live subtree would keep its subscriptions, its
/// images and its scroll position alive for content nobody can see, and 'cheap to
/// rebuild' is what the widget tree is for"
/// ([ADR-0004](../../../../../../../../book/src/adr/0004-three-tree-retained-declarative-model.md)).
///
/// So a closed `collapse` describes **one** child. Not a child with `display:
/// none`, which §10's subset does not have; not a child of zero height, which
/// would still be built, still be subscribed and still be laid out.
///
/// The price is stated rather than hidden: **reopening a section rebuilds it**,
/// and anything that has to survive belongs in the model — which is the same
/// bargain `tabs` makes for its unselected content
/// ([ADR-0107](../../../../../../../../book/src/adr/0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)).
///
/// ## The height does not animate, and that is not a limitation
///
/// §5: "the chevron rotates on `base`; the body does not animate its height,
/// because height is not on §1.7's whitelist and never will be." An animated
/// height is a layout pass per frame for the whole subtree below it, and §1.7's
/// whitelist is `opacity` and `transform` precisely so that a transition can
/// never cost a reflow. The chevron turning is what says the section opened.
///
/// ## Uncontrolled or controlled, like every other value in the catalog
///
/// With no `open` given, the section keeps its own state — §5's "`open` is
/// retained state". Give it `open` and `onToggle` and the application decides,
/// which is `checkbox`'s arrangement and every other value's here: a `collapse`
/// whose `onToggle` does nothing stays shut, which is the behaviour and not a bug.
///
/// @param title      the header's text
/// @param open       whether it starts open, or — with [#onToggle] — whether it
///                   *is* open
/// @param onToggle   what a click on the header asks for, or null to keep the
///                   state here
/// @param children   the body, built only while it is showing
/// @param attributes the `id` and classes, which land on the `collapse` node
@Markup("collapse")
public record Collapse(
        String title, boolean open, Consumer<Boolean> onToggle, List<Widget> children,
        Attributes attributes)
        implements Widget.Stateful, Attributed<Collapse> {

    public Collapse(String title, Widget... kids) {
        this(title, false, null, List.of(kids), Attributes.NONE);
    }

    public Collapse(String title, boolean open, Consumer<Boolean> onToggle, Widget... kids) {
        this(title, open, onToggle, List.of(kids), Attributes.NONE);
    }

    public Collapse {
        Objects.requireNonNull(title, "title");
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// Whether the application is deciding, rather than this widget.
    public boolean isControlled() {
        return onToggle != null;
    }

    @Override
    public Collapse withAttributes(Attributes value) {
        return new Collapse(title, open, onToggle, children, value);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new CollapseState();
    }

    /// Builds a `collapse` from markup.
    ///
    /// `open=#true` is the initial state, and `toggle=` names an action taking a
    /// boolean when an application wants to own it.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Collapse(
                node.stringProperty("title") == null ? "" : node.stringProperty("title"),
                node.booleanProperty("open"),
                wiring.flag(node, "toggle"),
                children,
                Attributes.of(node));
    }
}
