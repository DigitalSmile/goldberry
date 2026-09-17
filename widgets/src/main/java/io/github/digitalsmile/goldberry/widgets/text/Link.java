package io.github.digitalsmile.goldberry.widgets.text;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// Text that does something — `docs/core-widgets.md` §2's `link`.
///
/// ```kdl
/// link action="app.show-docs" "Read the docs"
/// link href="https://github.com/DigitalSmile/goldberry" "Goldberry on GitHub"
/// link href="mailto:hello@example.org" visited=#true "Write to us"
/// ```
///
/// ## Two targets, one word
///
/// `action=` is in-app navigation and `href=` is an external target, "opened
/// through the platform" — [io.github.digitalsmile.goldberry.Host#openExternal],
/// which hands the URL to the desktop's own handler for its scheme. A link
/// with both runs the action and opens the target. A link with neither is a
/// word in the link ink that takes no focus, which is what a document gets
/// while its wiring is not there yet.
///
/// ## What an external link says about itself
///
/// §2: "External links carry a trailing 12px `external-link` icon and their
/// accessible name says so, because 'opens outside this window' is not
/// something a colour can convey." The icon is the toolkit's own and not the
/// application's — this is the one widget that makes one — so the state that
/// holds it closes it (ADR-0346).
///
/// ## `visited` is the application's
///
/// "`visited=#true` is the application's to set, because the toolkit keeps no
/// history." It is a class, `link.visited`, and nothing here sets it.
///
/// ## Block-level, and not a `span`
///
/// "A `link` inside a paragraph is the block-level spelling of `span
/// class="link"`; the `span` form stays for mid-sentence use." This widget is
/// a row of a word and, sometimes, an icon; it does not flow inside a
/// sentence, and `text`'s inline runs are how a link mid-sentence is written.
///
/// ## This node styles nothing
///
/// `link` as a **CSS type** is [LinkText], the node the state builds
/// (ADR-0109).
///
/// @param label      the word
/// @param onPress    what an in-app link does, or null
/// @param href       what an external link opens, or null
/// @param visited    whether the application says this has been followed
/// @param attributes `id` and `class`, exactly as on every other widget
@Markup("link")
public record Link(
        String label, @Nullable Runnable onPress, @Nullable String href, boolean visited, Attributes attributes)
        implements Widget.Stateful, Attributed<Link> {

    public Link {
        Objects.requireNonNull(label, "label");
        if (label.isEmpty()) {
            throw new IllegalArgumentException(
                    "a link needs a word: it is read as part of the page, and a link with nothing in it"
                            + " is a target nobody can name (§13)");
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
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
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Link(
                Wiring.label(node),
                node.stringProperty("action") == null ? null : wiring.action(node, "action"),
                node.stringProperty("href"),
                node.booleanProperty("visited"),
                Attributes.of(node));
    }
}
