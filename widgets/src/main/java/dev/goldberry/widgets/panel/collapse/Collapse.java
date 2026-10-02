package dev.goldberry.widgets.panel.collapse;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A header and a body that folds away: press the header and the body opens or
/// closes under it.
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
/// A collapsed section that kept a live subtree would keep its subscriptions,
/// its images and its scroll position alive for content nobody can see, and
/// "cheap to rebuild" is what the widget tree is for. So a closed `collapse`
/// describes one child: not a child with `display: none`, which the CSS subset
/// does not have; not a child of zero height, which would still be built, still
/// be subscribed and still be laid out.
///
/// The price is stated rather than hidden: reopening a section rebuilds it, and
/// anything that has to survive belongs in the model. It is the same bargain
/// `tabs` makes for its unselected content.
///
/// ## The height does not animate, and that is not a limitation
///
/// The chevron rotates on the `base` duration; the body does not animate its
/// height. An animated height is a layout pass per frame for the whole subtree
/// below it, and the design system lets only `opacity` and `transform` animate
/// precisely so that a transition can never cost a reflow. The chevron turning
/// is what says the section opened.
///
/// ## Uncontrolled or controlled, like every other value in the catalog
///
/// With no `open` given, the section keeps its own state. Give it `open` and
/// `onToggle` and the application decides,
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
///
/// Read more: [Panels](https://goldberry.dev/docs/components/panels.html#collapse).
@Markup("collapse")
public record Collapse(
        String title, boolean open, @Nullable Consumer<Boolean> onToggle, List<Widget> children, Attributes attributes)
        implements Widget.Stateful, Attributed<Collapse> {

    public Collapse(String title, Widget... kids) {
        this(title, false, null, List.of(kids), Attributes.NONE);
    }

    public Collapse(String title, boolean open, Consumer<Boolean> onToggle, Widget... kids) {
        this(title, open, onToggle, List.of(kids), Attributes.NONE);
    }

    /// Written out so that the parameters taking null for a default can say so.
    public Collapse(
            String title,
            boolean open,
            @Nullable Consumer<Boolean> onToggle,
            @Nullable List<Widget> children,
            @Nullable Attributes attributes) {
        Objects.requireNonNull(title, "title");
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.title = title;
        this.open = open;
        this.onToggle = onToggle;
        this.children = children;
        this.attributes = attributes;
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
    public @Nullable Object key() {
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
                Objects.requireNonNullElse(node.stringProperty("title"), ""),
                node.booleanProperty("open"),
                wiring.flag(node, "toggle"),
                children,
                Attributes.of(node));
    }
}
