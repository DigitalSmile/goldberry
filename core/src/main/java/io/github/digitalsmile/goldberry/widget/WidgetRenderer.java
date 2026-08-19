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
import io.github.digitalsmile.goldberry.text.ParagraphCache;
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

    /// What the frame loop is managing, for the widgets that draw it. Nothing
    /// until a window says otherwise, because a renderer with no loop over it —
    /// a test, a layer — has no frames to report.
    private io.github.digitalsmile.goldberry.FrameStats frames =
            io.github.digitalsmile.goldberry.FrameStats.none();

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
        this.paintContext = context(style -> fonts.of(style.typography()));
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
        this.paintContext = context(style -> font);
    }

    /// The shaping cache the paint context is built over, for the frame trace —
    /// a frame that shapes text it shaped last frame is a frame with a defect in
    /// it, and the count is the only thing that says so (ADR-0152).
    private ParagraphCache paragraphs;

    /// A paint context over `fonts`, with a shaping cache behind it.
    ///
    /// One cache per renderer rather than one global one: it holds `GlyphRun`s,
    /// which are six `int[]`s the length of the text, and its entries are only
    /// useful to trees drawn with the same fonts. A renderer is what owns both.
    ///
    /// The cache is what makes a paragraph the **same instance** frame to frame,
    /// which is not only about the 56 µs of shaping it saves — the retained
    /// render tree reads that identity to decide it can keep the measure callback
    /// it already bound (ADR-0069).
    private Paints.Context context(java.util.function.Function<ComputedStyle, Font> fonts) {
        var cache = ParagraphCache.create();
        this.paragraphs = cache;
        return new Paints.Context() {

            @Override
            public Font font(ComputedStyle style) {
                return fonts.apply(style);
            }

            @Override
            public io.github.digitalsmile.goldberry.text.Paragraph paragraph(
                    ComputedStyle style, String text) {
                return cache.paragraph(fonts.apply(style), text);
            }

            /// This frame's time, read once in `render` -- not `clock.nowMillis()`
            /// again here. A widget asking the clock directly would get a slightly
            /// later answer than the transitions running beside it, and two
            /// spinners in one window would each be on their own tick.
            @Override
            public double nowMillis() {
                return frameNow;
            }

            @Override
            public boolean reducedMotion() {
                return reducedMotion;
            }

            @Override
            public io.github.digitalsmile.goldberry.FrameStats frames() {
                return frames;
            }
        };
    }

    /// The time the current frame is being rendered at — see
    /// [Paints.Context#nowMillis()].
    private double frameNow;

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
    /// The frame statistics every node on this renderer reads — see
    /// [Paints.Context#frames()].
    ///
    /// The launcher points this at the window's; a test hands in
    /// [io.github.digitalsmile.goldberry.FrameStats#of] so a golden image of a
    /// HUD shows numbers somebody chose rather than whatever the machine that ran
    /// the test managed.
    public WidgetRenderer frames(io.github.digitalsmile.goldberry.FrameStats value) {
        this.frames = Objects.requireNonNull(value, "frames");
        return this;
    }

    public WidgetRenderer reducedMotion(boolean value) {
        this.reducedMotion = value;
        return this;
    }

    /// The resolver this renderer styles with.
    ///
    /// Package-private, and it exists for one reader: the style-cache test, which
    /// asks an element whether it still holds a style *this* resolver produced.
    /// That is the mechanism ADR-0070 rests on, and asserting on it directly beats
    /// inferring it from a colour that would also be right for the wrong reason.
    StyleResolver resolver() {
        return resolver;
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
        frameNow = now;
        animating = false;
        // So a node whose state changes between frames can ask what the sheets
        // say without having resolved a style of its own (ADR-0149).
        tree.styleResolver(resolver);
        var textHitsBefore = FrameTrace.ENABLED ? paragraphs.hits() : 0;
        var textMissesBefore = FrameTrace.ENABLED ? paragraphs.misses() : 0;
        var boxes = render(tree.root(), null, now);
        if (FrameTrace.ENABLED) {
            tree.trace().text((int) (paragraphs.hits() - textHitsBefore),
                    (int) (paragraphs.misses() - textMissesBefore));
        }
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
        var trace = FrameTrace.ENABLED ? element.tree().trace() : null;
        if (trace != null) {
            trace.countWalk();
        }
        if (element.widget() instanceof Styled styled) {
            // What the widget computes from the frame, before the cascade is
            // asked — the same mirroring the pseudo-classes below get, for the
            // same reason, with the frame added (ADR-0150).
            element.frameClasses(styled.classes(frames));
            element.setPseudoClass(PseudoClass.DISABLED, styled.isDisabled());
            element.setPseudoClass(PseudoClass.CHECKED, styled.isChecked());
            element.setPseudoClass(PseudoClass.INDETERMINATE, styled.isIndeterminate());
            element.setPseudoClass(PseudoClass.AFFIXED, styled.isAffixed());
        }

        // A node the cascade can reach resolves a style; one it cannot passes its
        // ancestor's straight through. A widget that is neither `Styled` nor
        // `Paints` has no type, no id and no classes, so no selector names it and
        // resolving it would produce the inherited values it was handed anyway --
        // at the cost of a full cascade walk per composition node per frame.
        ComputedStyle self;
        if (element.widget() instanceof Styled || element.widget() instanceof Paints) {
            // §5's "style resolution (invalidated nodes)". The cache is checked
            // against the resolver *and* the inherited style, both by identity:
            // a theme swap builds a new renderer and therefore a new resolver, so
            // every entry misses at once; and a parent that re-resolved hands
            // down a different instance, so its children re-resolve without
            // anything having to tell them to (ADR-0070).
            self = element.cachedStyle(resolver, inherited);
            if (self == null) {
                var began = trace == null ? 0L : System.nanoTime();
                self = ComputedStyle.of(resolver.resolve(element), lengths, inherited);
                element.cacheStyle(resolver, inherited, self);
                if (trace != null) {
                    trace.countResolve();
                    trace.cascade(System.nanoTime() - began);
                }
            }
            // §8's `inline` layer, typed: the widget's last word, applied after
            // the cascade and **after** the cache — a widget-computed value
            // changes when the widget does, and caching it would pin a segmented
            // control's indicator to whichever segment was selected first.
            //
            // Before the animation below rather than inside `render`, which is
            // the whole point: a value written here is part of what the
            // transition observes and therefore moves, where the same value
            // written in `render` would snap (ADR-0099).
            var identityBegan = trace == null ? 0L : System.nanoTime();
            if (element.widget() instanceof Styled styled) {
                self = styled.restyle(self);
            }
            // The style handed to the children, kept as one **instance** for as
            // long as it keeps its value. Their cache is keyed on this by
            // identity, and `restyle` above hands back a new object every frame
            // for every widget that writes an inline value — so without this the
            // cache below a `scroll`, a `tab` or a `segmented` never hit at all
            // (ADR-0142).
            self = element.stableStyle(self);
            if (trace != null) {
                trace.identity(System.nanoTime() - identityBegan);
            }
        } else {
            self = inherited;
        }

        // The target the cascade just produced, and the values actually in
        // flight. `self` stays the target -- it is what the next frame diffs
        // against, and what children inherit -- while `painted` carries the
        // overlay. §1.7: an animated value is never written back into computed
        // style, because a cascade that saw the halfway colour as the node's real
        // one would start a second transition from it and never arrive.
        var painted = self;
        if (self != null && (!self.transitions().isEmpty() || element.isAnimating())) {
            var motionBegan = trace == null ? 0L : System.nanoTime();
            var animations = element.animations();
            animations.observe(reducedMotion ? self.transitions(self.transitions().reduced()) : self,
                    now);
            painted = animations.apply(self, now);
            animating |= animations.settle(now);
            if (trace != null) {
                trace.motion(System.nanoTime() - motionBegan);
            }
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

        // A widget that draws itself from the frame clock keeps the loop awake.
        // §1.7's idle loop stops the frame after the last transition settles, and
        // a spinner has no transition to settle -- so without this it would be
        // painted once and left there (ADR-0081).
        animating |= paints.isAnimating();

        // Tagged with the element that produced it, which is how a pointer
        // event gets from a rectangle on screen back to a node (ADR-0054).
        var boxBegan = trace == null ? 0L : System.nanoTime();
        var box = paints.render(painted, List.copyOf(children), paintContext).owner(element);
        if (trace != null) {
            trace.boxes(System.nanoTime() - boxBegan);
        }
        return List.of(box);
    }
}
