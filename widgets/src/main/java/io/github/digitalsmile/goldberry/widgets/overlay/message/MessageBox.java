package io.github.digitalsmile.goldberry.widgets.overlay.message;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

/// The node a stylesheet calls `message`.
///
/// [Message] is stateful and styles nothing, so this carries the CSS type, the
/// `id` and the classes — the arrangement `field`, `collapse`, `scroll`, `tabs`
/// and `select` all use.
///
/// @param kind       which of §7's four this is
/// @param text       the words
/// @param actions    the author's links, or empty
/// @param onDismiss  what the × tells, or null for a banner with no way out
/// @param phase      where the banner is in its entrance, or in its exit once the
///                   × has been pressed — see [MessageState]
/// @param departed   whether the exit has run out, in which case this draws
///                   nothing at all
/// @param onMotion   told what each frame says about the motion preference, so
///                   that the handler which acts on it has an answer
/// @param attributes the `id` and classes the document wrote
record MessageBox(
        Message.Kind kind,
        String text,
        List<Widget> actions,
        Runnable onDismiss,
        Phase phase,
        boolean departed,
        java.util.function.Consumer<Boolean> onMotion,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints {

    /// §3's "2px rise" — how far below its place a banner starts.
    private static final double TRAVEL = 2;

    @Override
    public String cssType() {
        return "message";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    /// The kind's own class beside the author's, so `message.danger` selects
    /// without anybody having to write the class in the document —
    /// [io.github.digitalsmile.goldberry.widgets.panel.skeleton.Skeleton]'s
    /// arrangement, for the same reason.
    @Override
    public Set<String> classes() {
        var all = new LinkedHashSet<>(attributes.classes());
        all.add(kind.cssClass());
        return Set.copyOf(all);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    /// The glyph, the words and the way out — see [Message]'s diagram.
    ///
    /// **Nothing at all once it has departed**, so the × that was just pressed
    /// leaves the hit test with the frame it was pressed on rather than staying
    /// clickable inside a banner nobody can see.
    @Override
    public List<Widget> children() {
        if (departed) {
            return List.of();
        }
        var parts = new ArrayList<Widget>(3);
        parts.add(new MessageIcon(kind));
        parts.add(new MessageBody(text, actions));
        if (onDismiss != null) {
            parts.add(new MessageDismiss(onDismiss));
        }
        return List.copyOf(parts);
    }

    /// Awake only while a phase is actually running.
    ///
    /// It asks the [Phase] rather than answering from a field, and that is the
    /// difference worth writing down: a phase settles **inside `render`**, when
    /// the clock it is reading passes the end. A part that decided at *build*
    /// time whether it was animating would go on saying yes until something else
    /// rebuilt it — and for a banner, nothing else ever does. §1.7's "the frame
    /// loop is fully idle when no animation is active" is a promise about a
    /// widget that has been sitting there for a minute, so it has to be the
    /// phase that answers.
    ///
    /// A departure is the one phase that does not settle itself — `LEAVING` stays
    /// `LEAVING`, because whatever is going has been dropped by its owner and
    /// there is nothing to settle into. [MessageState]'s timer ends it, and
    /// [#departed] is that answer.
    @Override
    public boolean isAnimating() {
        return !departed && phase.isRunning();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        onMotion.accept(context.reducedMotion());
        if (departed) {
            // Not `style`: a departed banner keeps no padding, no border and no
            // background, so what is left of it is a box of nothing. The gap its
            // container puts round it is the container's and stays.
            return Box.of();
        }
        var box = Box.of().style(style).children(boxes.toArray(Box[]::new));
        if (context.reducedMotion()) {
            // Not merely drawn at full strength: the phase is *ended*, so the
            // banner also stops asking for frames it would spend standing still.
            phase.skip();
            return box;
        }
        // Reading it is what starts the phase -- it is stamped from the frame
        // clock on its first read, and this is the only place there is one.
        var progress = phase.progressAt(context.nowMillis());
        var leaving = phase.kind() == Phase.Kind.LEAVING;
        var visible = leaving ? 1 - progress : progress;
        if (visible >= 1) {
            return box;
        }
        if (leaving) {
            // §3 asks for `opacity` and nothing else on the way out. A banner
            // that also slid would move the content under it twice — once as it
            // travelled and again as it stopped being there.
            return box.opacity(visible);
        }
        // Up from below: a banner that pushed the content down as it arrived
        // would make the whole region jump, so it rises the 2px §3 asks for
        // into a space the layout has already given it.
        return box.opacity(visible)
                .transform(Transform.of(new Transform.Function.Translate(
                        Transform.Length.ZERO, Transform.Length.px((1 - visible) * TRAVEL))));
    }
}
