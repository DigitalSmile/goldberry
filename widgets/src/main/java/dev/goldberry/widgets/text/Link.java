package dev.goldberry.widgets.text;

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

/// Text that does something when pressed: an in-app action, an external
/// target, or both.
///
/// ```kdl
/// link action="app.show-docs" "Read the docs"
/// link href="https://goldberry.dev" "Goldberry on the web"
/// link href="mailto:hello@example.org" visited=#true "Write to us"
/// ```
///
/// In Java: `new Link("Read the docs", actions::showDocs)`,
/// `Link.external("Goldberry on the web", "https://goldberry.dev")`.
///
/// `action=` runs through the application's action registry. `href=` is
/// handed to the desktop's own handler for its scheme, and a warning is logged
/// when the desktop refuses it. A link with both runs the action and then
/// opens the target. A link with neither is a word in the link ink that takes
/// no focus, which is what a document shows while its wiring is not there yet.
///
/// An external link carries a trailing 12px `external-link` icon and its
/// accessible name ends in "opens outside this window", because a colour cannot
/// say that. The icon is the toolkit's own, so the state that holds it closes
/// it. `visited` is a class, `link.visited`; the toolkit keeps no history, so
/// it is the application's to set.
///
/// A link is a row of a word and, sometimes, an icon; it does not flow inside a
/// sentence. The CSS type `link` is [LinkText], the node the state builds, so
/// this record holds the model and that node holds the appearance.
///
/// Read more: [Text and links](https://goldberry.dev/docs/components/text.html#link).
///
/// @param label      the word; an empty label is refused
/// @param onPress    what an in-app link does, or null
/// @param href       what an external link opens, or null
/// @param visited    whether the application says this has been followed
/// @param attributes `id` and `class`, exactly as on every other widget
@Markup("link")
public record Link(
        String label, @Nullable Runnable onPress, @Nullable String href, boolean visited, Attributes attributes)
        implements Widget.Stateful, Attributed<Link> {

    /// Written out so that the parameters taking null for a default can say so.
    public Link(
            String label,
            @Nullable Runnable onPress,
            @Nullable String href,
            boolean visited,
            @Nullable Attributes attributes) {
        Objects.requireNonNull(label, "label");
        if (label.isEmpty()) {
            throw new IllegalArgumentException(
                    "a link needs a word: it is read as part of the page, and a link with nothing in it"
                            + " is a target nobody can name");
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.label = label;
        this.onPress = onPress;
        this.href = href;
        this.visited = visited;
        this.attributes = attributes;
    }

    /// A link that does something inside the application.
    public Link(String label, Runnable onPress) {
        this(label, onPress, null, false, Attributes.NONE);
    }

    /// A link that opens `href` through the platform.
    public static Link external(String label, String href) {
        return new Link(label, null, Objects.requireNonNull(href, "href"), false, Attributes.NONE);
    }

    /// This link, followed already.
    public Link visited(boolean value) {
        return new Link(label, onPress, href, value, attributes);
    }

    /// Whether this opens outside the window.
    public boolean isExternal() {
        return href != null;
    }

    @Override
    public Link withAttributes(Attributes value) {
        return new Link(label, onPress, href, visited, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new LinkState();
    }

    /// Builds a `link` from markup.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Link(
                Wiring.label(node),
                node.stringProperty("action") == null ? null : wiring.action(node, "action"),
                node.stringProperty("href"),
                node.booleanProperty("visited"),
                Attributes.of(node));
    }
}
