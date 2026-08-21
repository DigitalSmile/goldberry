package io.github.digitalsmile.goldberry.widgets.panel.carousel;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Markup;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import java.time.Duration;
import java.util.List;
import java.util.function.IntConsumer;

/// One child at a time out of a list — `docs/core-widgets.md` §5's `carousel`.
///
/// ```kdl
/// carousel loop=#true interval=5000 {
///     card { text "The first slide" }
///     card { text "The second" }
/// }
/// ```
///
/// ```java
/// new Carousel(first, second, third)                    // keeps its own index
/// new Carousel(index, this::setIndex, first, second)    // controlled
/// ```
///
/// ## The one widget in §5 that is a controller
///
/// Everything else in the group is a description: a `card` is a surface, a
/// `collapse` is a header and a body. A carousel with `interval` set has a
/// **rotation**, which is a thing that happens over time whether or not anybody
/// asks — and §1.7 rule 4 says nothing loops except explicit continuous
/// indicators. §5 names the exception and its conditions in one sentence:
///
/// > **Nothing advances on its own unless `interval` is set**, and when it is,
/// > the rotation pauses on hover, on focus anywhere inside, and entirely under
/// > reduced motion — §1.7 rule 4 says nothing loops except explicit continuous
/// > indicators, and a carousel that moves while being read is the canonical
/// > violation.
///
/// So `interval` defaults to **off**, and when it is on there are three separate
/// reasons to stop. Two of them are complete here; the third is not, and it is
/// stated rather than hidden: focus on a widget **inside a slide** does not pause
/// the rotation, because the cascade has no `:focus-within` and nothing tells a
/// widget that focus landed in its subtree. Focus on the strip or on the
/// carousel's own controls does pause it ([ADR-0165]).
///
/// ## `loop` is off by default
///
/// §5 again, and it is the right default for the same reason `interval` is: at
/// the last slide, `Next` being disabled says "that is all of them", where
/// wrapping silently to the first says nothing at all and can be mistaken for a
/// list that never ends.
///
/// ## Only the current slide is built
///
/// `tabs`'s bargain, for `tabs`'s reason
/// ([ADR-0107](../../../../../../../../book/src/adr/0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)):
/// a slide nobody can see should not hold subscriptions, images or a scroll
/// position. The cost is the same too — moving away from a slide and back
/// rebuilds it, and anything that has to survive belongs in the model.
///
/// @param index      which slide is showing; with [#onChange] this is which one
///                   *is* showing, and without it which one starts
/// @param onChange   what a press, a key or the rotation asks for, or null to
///                   keep the index here
/// @param loop       whether the last slide is followed by the first
/// @param interval   how long each slide is shown before the next, or `null` and
///                   `Duration.ZERO` for a carousel that never advances itself
/// @param children   the slides
/// @param attributes the `id` and classes, which land on the `carousel` node
@Markup("carousel")
public record Carousel(
        int index, IntConsumer onChange, boolean loop, Duration interval,
        List<Widget> children, Attributes attributes)
        implements Widget.Stateful, Attributed<Carousel> {

    public Carousel(Widget... slides) {
        this(0, null, false, null, List.of(slides), Attributes.NONE);
    }

    public Carousel(int index, IntConsumer onChange, Widget... slides) {
        this(index, onChange, false, null, List.of(slides), Attributes.NONE);
    }

    public Carousel {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        interval = interval == null || interval.isZero() || interval.isNegative()
                ? null
                : interval;
        // Clamped rather than refused. A list that shrank under a bound index is
        // an ordinary thing for an application to do between frames, and a
        // carousel that threw there would take the window down for it.
        index = children.isEmpty() ? 0 : Math.clamp(index, 0, children.size() - 1);
    }

    /// Whether the application is deciding, rather than this widget.
    public boolean isControlled() {
        return onChange != null;
    }

    /// Whether this carousel advances on its own — §5's "nothing advances on its
    /// own unless `interval` is set".
    public boolean rotates() {
        return interval != null && children.size() > 1;
    }

    /// How many slides there are.
    public int count() {
        return children.size();
    }

    @Override
    public Carousel withAttributes(Attributes value) {
        return new Carousel(index, onChange, loop, interval, children, value);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new CarouselState();
    }

    /// Builds a `carousel` from markup.
    ///
    /// `interval` is in **milliseconds**, because KDL has numbers and not
    /// durations and `interval="5s"` would be a second syntax to parse and to get
    /// wrong.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var millis = (long) node.numberProperty("interval", 0);
        // Resolved **once**, and null when the document named no action: a
        // carousel whose `onChange` was a lambda that did nothing would count as
        // controlled, and a controlled carousel that nobody answers never moves —
        // so `carousel { … }` with no `change=` would sit on its first slide
        // forever and look broken.
        var change = wiring.numeric(node, "change");
        return new Carousel(
                (int) node.numberProperty("index", 0),
                change == null ? null : change::accept,
                node.booleanProperty("loop"),
                millis > 0 ? Duration.ofMillis(millis) : null,
                children,
                Attributes.of(node));
    }
}
