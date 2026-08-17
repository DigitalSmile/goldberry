package io.github.digitalsmile.goldberry.widget;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.CssLength;
import io.github.digitalsmile.goldberry.css.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.css.StyleResolver;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.text.Font;
import io.github.digitalsmile.goldberry.text.Fonts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Turns a built element tree into a box tree, styling every node on the way.
///
/// The join that makes the last six ADRs one thing: the element tree gives nodes
/// identity ([ADR-0052]), the cascade resolves each one's style
/// ([ADR-0049](../../../../../../book/src/adr/0049-the-css-engine-stops-at-computedstyle.md)),
/// and `BoxPainter` rasterizes what comes out.
///
/// Not every element renders. A [Widget.Stateless] exists to describe others and
/// produces nothing itself, so the renderer passes through it — which is why the
/// box tree is shallower than the element tree, and why a composition wrapper
/// costs nothing at paint time.
public final class WidgetRenderer {

    private final StyleResolver resolver;
    private final CssLength.Context lengths;
    private final Paints.Context paintContext;

    /// What time it is, for anything that animates (§1.7).
    private Clock clock = Clock.system();

    /// Whether `prefers-reduced-motion` is on — §1.7's rule 6.
    private boolean reducedMotion;

    /// Whether the tree rendered by the last [#render(ElementTree)] is still
    /// moving. Read by the application to decide whether to ask for another
    /// frame.
    private boolean animating;

    /// @param stylesheets in any order; the cascade decides by layer, not by
    ///                    the order they are handed over
    /// @param fonts       the faces and sizes text is shaped with. Owned by the
    ///                    caller and **not** closed here: a renderer is cheap and
    ///                    an application may well build several against one book
    public WidgetRenderer(List<Stylesheet> stylesheets, Fonts fonts) {
        this(stylesheets, fonts, CssLength.Context.DEFAULT);
    }

    public WidgetRenderer(List<Stylesheet> stylesheets, Fonts fonts, CssLength.Context lengths) {
        this.resolver = new StyleResolver(Objects.requireNonNull(stylesheets, "stylesheets"));
        this.lengths = Objects.requireNonNull(lengths, "lengths");
        Objects.requireNonNull(fonts, "fonts");
        this.paintContext = style -> fonts.of(style.typography());
    }

    /// A renderer that draws every node with one font, whatever the cascade said.
    ///
    /// For a test or a benchmark that is about something other than typography.
    /// An application wants the [Fonts] form: this one ignores `font-family`,
    /// `font-size` and `font-weight` entirely, so a button's label would be drawn
    /// at the same weight as the prose beside it.
    public WidgetRenderer(List<Stylesheet> stylesheets, Font font, CssLength.Context lengths) {
        this.resolver = new StyleResolver(Objects.requireNonNull(stylesheets, "stylesheets"));
        this.lengths = Objects.requireNonNull(lengths, "lengths");
        Objects.requireNonNull(font, "font");
        this.paintContext = style -> font;
    }

    /// See [#WidgetRenderer(List, Font, CssLength.Context)].
    public WidgetRenderer(List<Stylesheet> stylesheets, Font font) {
        this(stylesheets, font, CssLength.Context.DEFAULT);
    }

    /// The clock every animation on this renderer runs against.
    ///
    /// A test hands in [Clock#virtual()] so a golden image can snapshot the frame
    /// at exactly 80 ms of a 160 ms transition, on every machine and in CI — which
    /// is impossible against a wall clock, because the test would have to sleep
    /// and would then be asserting on whatever the scheduler gave it.
    public WidgetRenderer clock(Clock value) {
        this.clock = Objects.requireNonNull(value, "clock");
        return this;
    }

    /// Turns every transition instant — §1.7's `prefers-reduced-motion`.
    ///
    /// The declarations are kept at zero duration rather than dropped, so the
    /// machinery still runs and still ends and a reduced-motion user reaches the
    /// same states by the same route. §4 asks for the same shape from the
    /// high-contrast theme: an alias swap, never a separate code path.
    public WidgetRenderer reducedMotion(boolean value) {
        this.reducedMotion = value;
        return this;
    }

    /// Whether anything in the last rendered tree is still moving.
    ///
    /// The whole of §1.7's "the frame loop is fully idle when no animation is
    /// active": an application asks for another frame only while this is true, so
    /// a static window costs nothing and there is no polling anywhere.
    ///
    /// ```java
    /// window.onPaint(frame -> {
    ///     BoxPainter.paint(frame, renderer.render(tree));
    ///     if (renderer.isAnimating()) {
    ///         window.repaint();
    ///     }
    /// });
    /// ```
    public boolean isAnimating() {
        return animating;
    }

    /// Renders a whole tree.
    ///
    /// @throws IllegalStateException if the tree describes nothing that paints —
    ///         a root of pure composition with no primitive under it is almost
    ///         certainly a mistake, and an empty window is a poor way to report it
    public Box render(ElementTree tree) {
        Objects.requireNonNull(tree, "tree");
        // Read once for the whole frame. Two nodes must not see different times,
        // or two properties that §3.1 says "arrive together" -- a toggle's thumb
        // and its track -- would arrive microseconds apart and drift.
        var now = clock.nowMillis();
        animating = false;
        var boxes = render(tree.root(), null, now);
        if (boxes.isEmpty()) {
            throw new IllegalStateException(
                    "nothing in this widget tree paints; the root described only composition");
        }
        if (boxes.size() == 1) {
            return boxes.getFirst();
        }
        // A root that described several siblings needs something to hold them.
        return Box.of().children(boxes.toArray(Box[]::new));
    }

    /// The boxes one element contributes — one if it paints, otherwise its
    /// children's.
    ///
    /// **Styles resolve on the way down and boxes are built on the way up**,
    /// which is the shape inheritance forces: a child's `color` is its parent's
    /// unless it says otherwise, so the parent's style has to exist before the
    /// child is asked for one. The box tree is still assembled bottom-up, because
    /// a parent box needs its children.
    ///
    /// @param inherited the resolved style of the nearest ancestor that had one,
    ///                  or null at the root
    private List<Box> render(Element element, ComputedStyle inherited, double now) {
        // The pseudo-classes a widget owns rather than the router. `:disabled`,
        // `:checked` and `:indeterminate` are facts about the *description* —
        // what the widget was built with — so they are mirrored onto the element
        // here, before the cascade is asked. `:hover`, `:active` and `:focus` are
        // facts about the pointer and the keyboard, and input put those there.
        //
        // A widget cannot be both checked and indeterminate; `Styled` says so,
        // and mirroring `isChecked() && !isIndeterminate()` would hide a widget
        // that broke the rule instead of letting its stylesheet show it.
        if (element.widget() instanceof Styled styled) {
            element.setPseudoClass(PseudoClass.DISABLED, styled.isDisabled());
            element.setPseudoClass(PseudoClass.CHECKED, styled.isChecked());
            element.setPseudoClass(PseudoClass.INDETERMINATE, styled.isIndeterminate());
        }

        // A node the cascade can reach resolves a style; one it cannot passes its
        // ancestor's straight through. A widget that is neither `Styled` nor
        // `Paints` has no type, no id and no classes, so no selector names it and
        // resolving it would produce the inherited values it was handed anyway --
        // at the cost of a full cascade walk per composition node per frame.
        var self = element.widget() instanceof Styled || element.widget() instanceof Paints
                ? ComputedStyle.of(resolver.resolve(element), lengths, inherited)
                : inherited;

        // The target the cascade just produced, and the values actually in
        // flight. `self` stays the target -- it is what the next frame diffs
        // against, and what children inherit -- while `painted` carries the
        // overlay. §1.7: an animated value is never written back into computed
        // style, because a cascade that saw the halfway colour as the node's real
        // one would start a second transition from it and never arrive.
        var painted = self;
        if (self != null && (!self.transitions().isEmpty() || element.isAnimating())) {
            var animations = element.animations();
            animations.observe(reducedMotion ? self.transitions(self.transitions().reduced()) : self,
                    now);
            painted = animations.apply(self, now);
            animating |= animations.settle(now);
        }

        var children = new ArrayList<Box>();
        for (var child : element.children()) {
            // Children inherit the *target*, not the overlay: a label under a
            // control whose colour is mid-transition would otherwise take the
            // halfway value as its own inherited starting point and transition
            // again from there.
            children.addAll(render(child, self, now));
        }

        if (!(element.widget() instanceof Paints paints)) {
            // A composition node: it has no box of its own, so its children
            // become its parent's directly.
            return children;
        }

        // Tagged with the element that produced it, which is how a pointer
        // event gets from a rectangle on screen back to a node (ADR-0054).
        return List.of(paints.render(painted, List.copyOf(children), paintContext).owner(element));
    }
}
