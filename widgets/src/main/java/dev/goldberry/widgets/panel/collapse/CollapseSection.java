package dev.goldberry.widgets.panel.collapse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// **This is the `collapse` a stylesheet selects.**
///
/// [Collapse] is stateful and styles nothing, so this node carries the CSS type
/// and the document's `id` and classes — the split `select`, `tabs` and `menubar`
/// all use.
///
/// `.open` while the body is showing, so a stylesheet can reach the header, the
/// chevron and the body by one class rather than each of them being told.
record CollapseSection(
        String title,
        boolean open,
        Runnable onToggle,
        dev.goldberry.widgets.core.presence.Phase phase,
        List<Widget> body,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints {

    /// Written out so that the parameters taking null for a default can say so.
    CollapseSection(
            String title,
            boolean open,
            Runnable onToggle,
            dev.goldberry.widgets.core.presence.Phase phase,
            @Nullable List<Widget> body,
            @Nullable Attributes attributes) {
        body = List.copyOf(body == null ? List.of() : body);
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.title = title;
        this.open = open;
        this.onToggle = onToggle;
        this.phase = phase;
        this.body = body;
        this.attributes = attributes;
    }

    @Override
    public String cssType() {
        return "collapse";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        if (!open) {
            return attributes.classes();
        }
        var all = new java.util.LinkedHashSet<>(attributes.classes());
        all.add("open");
        return Set.copyOf(all);
    }

    /// Whether the body below is on its way in.
    ///
    /// The node that *carries* the phase answers for it as well as the node that
    /// draws with it. The renderer ORs `isAnimating` over the whole tree, so this
    /// changes no behaviour — what it buys is that `AnimationSweepTest`'s rule
    /// stays sharp: a widget holding a [dev.goldberry.widgets.core.presence.Phase]
    /// answers the frame loop, with no exception for "it hands it to a child"
    /// that nothing could check.
    ///
    /// **Guarded by `open`**, and that guard is the bug in miniature: a section
    /// closed half way through its arrival keeps an `ENTERING` phase that nothing
    /// will ever read again, so nothing will ever settle it. A shut section has
    /// no body and animates nothing whatever its phase remembers.
    @Override
    public boolean isAnimating() {
        return open && phase.isRunning();
    }

    /// The header, and the body only when it is showing.
    ///
    /// The body is a node of its own rather than the children going straight in,
    /// because a `collapse` is a column of two things and the second one needs
    /// padding a stylesheet can set without also indenting the header.
    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(2);
        parts.add(new CollapseHeader(title, open, onToggle));
        if (open) {
            parts.add(new CollapseBody(body, phase));
        }
        return List.copyOf(parts);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
