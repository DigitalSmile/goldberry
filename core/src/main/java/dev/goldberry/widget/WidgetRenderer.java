package dev.goldberry.widget;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.media.MediaContext;
import dev.goldberry.css.select.Selector.PseudoClass;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.log.Logs;
import dev.goldberry.motion.Clock;
import dev.goldberry.motion.KeyframeTrack;
import dev.goldberry.paint.Box;
import dev.goldberry.render.desktop.SystemTheme;
import dev.goldberry.stats.FrameStats;
import dev.goldberry.text.ParagraphCache;
import dev.goldberry.text.font.Font;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// Turns a built element tree into a box tree, styling every node on the way.
///
/// The middle step of a frame: the element tree gives nodes identity, this
/// resolves each one's style through the cascade and asks each painting widget
/// for its box, and `BoxPainter` rasterizes what comes out. An application
/// builds one over its stylesheets and fonts and renders once per frame; the
/// launcher does this for every window it opens.
///
/// ```java
/// var renderer = new WidgetRenderer(stylesheets, fonts);
/// renderer.prepare(tree);
/// window.onPaint(frame -> BoxPainter.paint(frame, renderer.render(tree)));
/// ```
///
/// Not every element renders. A [Widget.Stateless] exists to describe others and
/// produces nothing itself, so the renderer passes through it — which is why the
/// box tree is shallower than the element tree, and why a composition wrapper
/// costs nothing at paint time.
///
/// A resolved style is cached on its element, keyed by identity on this
/// renderer's resolver and on the parent's style, so a theme swap is a new
/// renderer and a frame in which nothing changed resolves nothing. `@media`
/// takes the same route: [#viewport], [#colorScheme] and [#reducedMotion]
/// move the media context, and a move that changes some condition's answer
/// swaps in a new resolver. Transitions
/// and keyframe animations are observed here as well, which is why
/// [#isAnimating()] is the question an application asks before it requests
/// another frame.
///
/// Read more:
/// [The frame loop](https://goldberry.dev/docs/overview/architecture.html#the-frame-loop).
public final class WidgetRenderer {

    private static final Logger LOG = Logs.of(WidgetRenderer.class);

    /// The cascade, under the media context below. Replaced, never edited, when
    /// a context change flips an `@media` condition: [StyleResolver#under]
    /// answers with a new instance then and with this one otherwise, and every
    /// style an element cached is keyed on the instance.
    private StyleResolver resolver;

    /// What the `@media` conditions are asked about: the window's size, the
    /// desktop's theme and [#reducedMotion].
    private MediaContext media = MediaContext.UNKNOWN;

    /// What the **root** resolves `em` and `rem` against — the configured pair,
    /// which is what an application passed in and never changes.
    private final CssLength.Context lengths;

    /// [#lengths] with `rem` set to what the root element actually computed.
    ///
    /// The one piece of per-frame style state the renderer keeps, and it is
    /// mutable for a reason nothing else here is: CSS defines `rem` as the root
    /// element's computed `font-size`, a node is handed its parent's style rather
    /// than the root's, and the walk is the only thing that ever holds both. It is
    /// reset to [#lengths] at the top of every frame and set once, at the root, so
    /// it is never read stale — a frame either has not reached the root yet, in
    /// which case the configured value is the right answer, or has, in which case
    /// this is.
    private CssLength.Context lengthsBelowRoot;

    private final Paints.Context paintContext;

    /// What time it is, for anything that animates: motion is a function of the
    /// frame's timestamp, never of a frame count.
    private Clock clock = Clock.system();

    /// What the frame loop is managing, for the widgets that draw it. Nothing
    /// until a window says otherwise, because a renderer with no loop over it —
    /// a test, a layer — has no frames to report.
    private FrameStats frames = FrameStats.none();

    /// Whether the user asked for less movement, as `prefers-reduced-motion`
    /// says.
    private boolean reducedMotion;

    /// Names `animation-name` used that no sheet declares, reported once each.
    private final Set<String> unknownKeyframes = new HashSet<>();

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
        this.lengthsBelowRoot = this.lengths;
        Objects.requireNonNull(fonts, "fonts");
        this.paintContext = context(style -> fonts.of(style.typography().scaled(textScale)));
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
        this.lengthsBelowRoot = this.lengths;
        Objects.requireNonNull(font, "font");
        this.paintContext = context(style -> font);
    }

    /// The shaping cache the paint context is built over, for the frame trace —
    /// a frame that shapes text it shaped last frame is a frame with a defect in
    /// it, and the count is the only thing that says so.
    private @Nullable ParagraphCache paragraphs;

    /// A paint context over `fonts`, with a shaping cache behind it.
    ///
    /// One cache per renderer rather than one global one: it holds `GlyphRun`s,
    /// which are six `int[]`s the length of the text, and its entries are only
    /// useful to trees drawn with the same fonts. A renderer is what owns both.
    ///
    /// The cache is what makes a paragraph the **same instance** frame to frame,
    /// which is not only about the 56 µs of shaping it saves — the retained
    /// render tree reads that identity to decide it can keep the measure callback
    /// it already bound.
    private Paints.Context context(java.util.function.Function<ComputedStyle, Font> fonts) {
        var cache = ParagraphCache.create();
        this.paragraphs = cache;
        return new Paints.Context() {

            @Override
            public Font font(ComputedStyle style) {
                return fonts.apply(style);
            }

            @Override
            public dev.goldberry.text.Paragraph paragraph(ComputedStyle style, String text) {
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

            /// Read against the element being rendered, which `renderBox` sets
            /// just before it calls `render` -- the same one-field trick
            /// `frameNow` uses, and for the same reason: the context is one
            /// object shared by every node, and this is the one question whose
            /// answer is per node.
            @Override
            public int color(String name, int fallback) {
                java.util.Objects.requireNonNull(name, "name");
                if (currentElement == null) {
                    return fallback;
                }
                // Cached by element identity against what its parent handed
                // down, so a chart asking for eight slots pays one cascade
                // rather than eight.
                // Substituted, not raw: a custom property may hold another one,
                // and `--gb-chart-1: var(--gb-warning)` is the natural way to
                // write "this series is the warning hue". Reading the tokens
                // without resolving reads that as "not a colour" and falls back
                // silently -- which is what the showcase's `p99 latency` card did
                // until the picture showed it still green.
                var resolved = resolver.customProperty(currentElement, name);
                if (resolved == null) {
                    return fallback;
                }
                var parsed = dev.goldberry.css.value.CssColor.parse(resolved);
                return parsed == null ? fallback : parsed;
            }

            /// [Paints.Context#length]'s implementation — `color`'s, with a
            /// length parser in place of the colour one.
            ///
            /// The `CssLength.Context` is the renderer's rather than the node's,
            /// which is a known and stated narrowing: an `em` here is against the
            /// root's size and not this element's, because the element's resolved
            /// style is not in hand at this seam. Nothing asks for one — the
            /// tokens this answers are all `px` — and the day one does, the fix
            /// is to pass the style in, not to guess.
            ///
            /// **`rem` is exact here.** This
            /// seam runs with `currentElement` set, so the walk has reached the
            /// root and [#lengthsBelowRoot] carries the size the root actually
            /// computed rather than the number an application configured.
            @Override
            public double length(String name, double fallback) {
                java.util.Objects.requireNonNull(name, "name");
                if (currentElement == null) {
                    return fallback;
                }
                var resolved = resolver.customProperty(currentElement, name);
                if (resolved == null) {
                    return fallback;
                }
                // A percentage is of something, and a widget asking for a token
                // has no containing block in hand to be a percentage of; `auto`
                // is not a number at all. Both answer the fallback.
                return dev.goldberry.css.value.CssLength.parse(resolved, lengthsBelowRoot)
                                instanceof dev.goldberry.layout.Length.Points points
                        ? points.value()
                        : fallback;
            }

            @Override
            public boolean reducedMotion() {
                return reducedMotion;
            }

            @Override
            public FrameStats frames() {
                return frames;
            }
        };
    }

    /// The time the current frame is being rendered at — see
    /// [Paints.Context#nowMillis()].
    private double frameNow;

    /// The element whose box is being built, for [Paints.Context#color] — the
    /// one question on that interface whose answer is per node rather than per
    /// frame.
    private dev.goldberry.widget.@Nullable Element currentElement;

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

    /// The frame statistics every node on this renderer reads — see
    /// [Paints.Context#frames()].
    ///
    /// The launcher points this at the window's; a test hands in
    /// [FrameStats#of] so a golden image of a
    /// HUD shows numbers somebody chose rather than whatever the machine that ran
    /// the test managed.
    public WidgetRenderer frames(FrameStats value) {
        this.frames = Objects.requireNonNull(value, "frames");
        return this;
    }

    /// The global text scale: a user setting that makes every piece of text in
    /// the window larger or smaller, and the switch that shows whether a
    /// component survives 150% without clipping.
    ///
    /// A switch on the renderer, which is where the other accessibility switches
    /// are — [#reducedMotion(boolean)] is the same shape, for the same reason:
    /// it is a *user* preference applied to a whole window, and there is no
    /// selector that could express one.
    ///
    /// ## It scales the text and not the layout, which is the point
    ///
    /// The factor is applied where a [ComputedStyle] becomes a [Font] and
    /// **nowhere in the cascade**. So a paragraph is shaped larger and a measured
    /// leaf grows around it, while a `height: 32px` stays 32 — which is exactly
    /// the condition every component is asked to survive.
    ///
    /// Scaling in the cascade instead is wrong twice over: `font-size: 1.2em`
    /// resolves against a parent that would already have been scaled, so an `em`
    /// chain would take the factor once per level; and a padding in `em` would
    /// grow with it, which would make the boxes get bigger too and hide the
    /// clipping this exists to reveal.
    ///
    /// **1 is the default and changes nothing** — not one golden moves.
    ///
    /// Read more: [Text scale](https://goldberry.dev/docs/guide/styling.html#text-scale).
    ///
    /// @param value the factor; the design system's range is 0.9 to 1.5, and
    ///              this clamps to it rather than refusing, because a text scale
    ///              is a user setting and a window that failed to open over one
    ///              is worse than a window whose text is as large as the design
    ///              system allows
    public WidgetRenderer textScale(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("a text scale must be a finite factor, not " + value);
        }
        this.textScale = Math.clamp(value, MINIMUM_TEXT_SCALE, MAXIMUM_TEXT_SCALE);
        return this;
    }

    /// What [#textScale] is set to.
    public double textScale() {
        return textScale;
    }

    /// The smallest text scale the design system allows: 90%.
    public static final double MINIMUM_TEXT_SCALE = 0.9;

    /// The largest text scale the design system allows: 150%.
    public static final double MAXIMUM_TEXT_SCALE = 1.5;

    private double textScale = 1;

    /// Turns every transition instant, as `prefers-reduced-motion` asks.
    ///
    /// The declarations are kept at zero duration rather than dropped, so the
    /// machinery still runs and still ends and a reduced-motion user reaches the
    /// same states by the same route. The high-contrast theme takes the same
    /// shape: an alias swap, never a separate code path.
    ///
    /// Read more: [Motion](https://goldberry.dev/docs/guide/design-system.html#motion).
    public WidgetRenderer reducedMotion(boolean value) {
        this.reducedMotion = value;
        return media(media.reducedMotion(value));
    }

    /// The window's logical size, which `@media (min-width: …)` and the rest
    /// are asked about.
    ///
    /// Told on every frame by the frame sequence, before the tree is prepared,
    /// so a resize that crosses a breakpoint restyles the frame it produced. A
    /// resize that crosses none changes nothing: the resolver is kept, and so is
    /// every style cached against it.
    ///
    /// Read more: [Media queries](https://goldberry.dev/docs/guide/styling.html#media-queries).
    public WidgetRenderer viewport(double width, double height) {
        return media(media.size(width, height));
    }

    /// The desktop's theme, which `@media (prefers-color-scheme: …)` is asked
    /// about. Light until told otherwise, which is also what a desktop that does
    /// not say is read as.
    public WidgetRenderer colorScheme(SystemTheme value) {
        return media(media.colorScheme(Objects.requireNonNull(value, "value")));
    }

    /// What the `@media` conditions are currently asked about.
    public MediaContext media() {
        return media;
    }

    private WidgetRenderer media(MediaContext value) {
        media = value;
        var next = resolver.under(value);
        if (next != resolver) {
            LOG.debug("an @media condition changed its answer under {}; restyling", value);
            resolver = next;
        }
        return this;
    }

    /// The resolver this renderer styles with.
    ///
    /// Package-private, and it exists for one reader: the style-cache test, which
    /// asks an element whether it still holds a style *this* resolver produced.
    /// That is the mechanism the style cache rests on, and asserting on it beats
    /// inferring it from a colour that would also be right for the wrong reason.
    StyleResolver resolver() {
        return resolver;
    }

    /// Hands `tree` this renderer's cascade **before** it is built.
    ///
    /// [#render] does the same thing on its way in, and that is a frame too late
    /// for one caller: a build may ask about a custom property
    /// ([BuildContext#token]), and a build runs before the frame it produces. A
    /// virtualized `list` deciding how many rows to make is the case — on the
    /// first frame it would otherwise have no cascade to ask and would build at
    /// its default, then correct itself on the next one.
    ///
    /// Idempotent, and the same instance every time: a renderer holds one
    /// resolver for its life, and a theme swap builds a new renderer.
    public void prepare(ElementTree tree) {
        java.util.Objects.requireNonNull(tree, "tree").styleResolver(resolver);
    }

    /// Whether anything in the last rendered tree is still moving.
    ///
    /// The frame loop is idle when nothing is animating: an application asks
    /// for another frame only while this is true, so a static window costs
    /// nothing and there is no polling anywhere.
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
        // or two properties that must arrive together -- a toggle's thumb
        // and its track -- would arrive microseconds apart and drift.
        var now = clock.nowMillis();
        frameNow = now;
        animating = false;
        // So a node whose state changes between frames can ask what the sheets
        // say without having resolved a style of its own.
        tree.styleResolver(resolver);
        // `rem` starts the frame meaning the configured size and becomes the
        // root's computed one the moment the walk has it. Reset here
        // rather than left from last frame, so a root whose `font-size` changed
        // cannot leave the tree below it resolving against the old number for a
        // frame -- and so the value is never a fact about a render that is over.
        lengthsBelowRoot = lengths;
        var cache =
                Objects.requireNonNull(this.paragraphs, "every constructor builds the cache with the paint context");
        var textHitsBefore = FrameTrace.ENABLED ? cache.hits() : 0;
        var textMissesBefore = FrameTrace.ENABLED ? cache.misses() : 0;
        var boxes = render(tree.root(), null, false, now);
        if (FrameTrace.ENABLED) {
            tree.trace().text((int) (cache.hits() - textHitsBefore), (int) (cache.misses() - textMissesBefore));
        }
        // The frame is over, so the cache knows what this one asked for and can
        // size itself to hold it. A cache smaller than one frame's working set
        // misses *every* lookup on the excess rather than merely missing more
        // often, which is what a document made of one paragraph per word turned
        // the default capacity into.
        cache.frame();
        if (boxes.isEmpty()) {
            throw new IllegalStateException("nothing in this widget tree paints; the root described only composition");
        }
        if (boxes.size() == 1) {
            return boxes.getFirst();
        }
        // A root that described several siblings needs something to hold them.
        return Box.of().children(boxes.toArray(Box[]::new));
    }

    /// The shaped paragraphs this renderer is keeping, for a diagnostic or a test.
    ///
    /// **Read-only in intent**: the cache is the renderer's, sized by the frames it
    /// has drawn, and handing it out is how a test asserts the thing
    /// that actually matters — that a settled frame shapes *nothing* — without a
    /// stopwatch and without a system property. `ParagraphCache.misses()` before
    /// and after two renders of an unchanged tree is the whole assertion.
    ///
    /// @return the cache, or null before the first render has created one
    public @Nullable ParagraphCache paragraphs() {
        return paragraphs;
    }

    /// `style` as a user who asked for less movement gets it: every transition
    /// instant and every keyframe animation gone.
    private static ComputedStyle reduced(ComputedStyle style) {
        return style.transitions(style.transitions().reduced())
                .animations(style.animations().reduced());
    }

    /// The style `element` enters from, or null when no `@starting-style` rule
    /// matches it.
    ///
    /// The widget's inline values are applied to it as they are to the element's
    /// real style. A segmented control's indicator position is the widget's last
    /// word on both, so it does not appear to move on the first frame.
    private @Nullable ComputedStyle startingStyle(Element element, @Nullable ComputedStyle inherited) {
        var declared = resolver.resolveStarting(element);
        if (declared == null) {
            return null;
        }
        var starting = ComputedStyle.of(declared, lengthsBelowRoot, inherited);
        return element.widget() instanceof Styled styled ? styled.restyle(starting) : starting;
    }

    /// The `@keyframes` block called `name`, resolved for `element` against
    /// `target`, or null when no stylesheet declares one.
    private @Nullable KeyframeTrack track(Element element, String name, ComputedStyle target) {
        var block = resolver.keyframes(name);
        if (block == null) {
            if (unknownKeyframes.size() < 512 && unknownKeyframes.add(name)) {
                LOG.warn("animation-name \"{}\" names no @keyframes block in any stylesheet", name);
            }
            return null;
        }
        return KeyframeTrack.resolve(
                block, target, frame -> resolver.resolveKeyframe(element, frame), lengthsBelowRoot);
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
    /// @param disabledAbove whether an ancestor is disabled, which makes this
    ///                  node disabled too: the router already walks up to find
    ///                  that for input, and the cascade owes a stylesheet the
    ///                  same answer
    private List<Box> render(Element element, @Nullable ComputedStyle inherited, boolean disabledAbove, double now) {
        // The pseudo-classes a widget owns rather than the router. `:disabled`,
        // `:checked` and `:indeterminate` are facts about the *description* —
        // what the widget was built with — so they are mirrored onto the element
        // here, before the cascade is asked. `:hover`, `:active` and `:focus` are
        // facts about the pointer and the keyboard, and input put those there.
        //
        // A widget cannot be both checked and indeterminate; `Styled` says so,
        // and mirroring `isChecked() && !isIndeterminate()` would hide a widget
        // that broke the rule instead of letting its stylesheet show it.
        // A hidden node contributes no box, and nothing under it is rendered:
        // its elements stay mounted and keep their state, which is what hiding
        // rather than removing is for.
        if (element.widget() instanceof Styled hidden && hidden.isHidden()) {
            return List.of();
        }
        var trace = FrameTrace.ENABLED ? element.tree().trace() : null;
        if (trace != null) {
            trace.countWalk();
        }
        if (element.widget() instanceof Styled styled) {
            // What the widget computes from the frame, before the cascade is
            // asked — the same mirroring the pseudo-classes below get, for the
            // same reason, with the frame added.
            element.frameClasses(styled.classes(frames));
            // Its own, **or** an ancestor's. A button inside a disabled `form`
            // says nothing about itself and is drawn disabled all the same,
            // which is the rule the router enforces for input — it walks up to
            // find it, and this walks down because the styles already resolve
            // that way.
            element.setPseudoClass(PseudoClass.DISABLED, disabledAbove || styled.isDisabled());
            element.setPseudoClass(PseudoClass.CHECKED, styled.isChecked());
            element.setPseudoClass(PseudoClass.INDETERMINATE, styled.isIndeterminate());
            element.setPseudoClass(PseudoClass.INVALID, styled.isInvalid());
            element.setPseudoClass(PseudoClass.AFFIXED, styled.isAffixed());
        }

        // A node the cascade can reach resolves a style; one it cannot passes its
        // ancestor's straight through. A widget that is neither `Styled` nor
        // `Paints` has no type, no id and no classes, so no selector names it and
        // resolving it would produce the inherited values it was handed anyway --
        // at the cost of a full cascade walk per composition node per frame.
        ComputedStyle self;
        // What the children key their cache on, which is `self` or an older
        // instance that agrees with it about everything they can read.
        ComputedStyle handDown;
        if (element.widget() instanceof Styled || element.widget() instanceof Paints) {
            // Style resolution, for invalidated nodes only. The cache is checked
            // against the resolver *and* the inherited style, both by identity:
            // a theme swap builds a new renderer and therefore a new resolver, so
            // every entry misses at once; and a parent that re-resolved hands
            // down a different instance, so its children re-resolve without
            // anything having to tell them to.
            self = element.cachedStyle(resolver, inherited);
            if (self == null) {
                var began = trace == null ? 0L : System.nanoTime();
                self = ComputedStyle.of(resolver.resolve(element), lengthsBelowRoot, inherited);
                element.cacheStyle(resolver, inherited, self);
                if (trace != null) {
                    trace.countResolve();
                    trace.cascade(System.nanoTime() - began);
                }
            }
            // The cascade's `inline` layer, typed: the widget's last word, applied after
            // the cascade and **after** the cache — a widget-computed value
            // changes when the widget does, and caching it would pin a segmented
            // control's indicator to whichever segment was selected first.
            //
            // Before the animation below rather than inside `render`, which is
            // the whole point: a value written here is part of what the
            // transition observes and therefore moves, where the same value
            // written in `render` would snap.
            var identityBegan = trace == null ? 0L : System.nanoTime();
            if (element.widget() instanceof Styled styled) {
                self = styled.restyle(self);
            }
            // The style handed to the children, kept as one **instance** for as
            // long as its *inherited* half keeps its value. Their cache is keyed
            // on this by identity, and `restyle` above hands back a new object
            // every frame for every widget that writes an inline value — so
            // without this the cache below a `scroll`, a `tab` or a `segmented`
            // never hit at all.
            //
            // **Not assigned back to `self`**: what this node paints has to be
            // what it actually resolved,
            // and what its children key on only has to agree about `color` and
            // `typography`. Folding the two together would paint a stale
            // transform the moment the comparison stopped being `equals`.
            handDown = element.stableStyle(self);
            if (trace != null) {
                trace.identity(System.nanoTime() - identityBegan);
            }
        } else {
            self = inherited;
            handDown = inherited;
        }

        // **`rem` gets its real meaning here, and it could not get it earlier.**
        // CSS says `rem` is the root *element's* computed `font-size`, and a
        // node is handed its parent's style and not the root's -- so nothing
        // inside `ComputedStyle.of` can recover it for a descendant. What can is
        // the thing that walks the tree, at the one moment it has the root's
        // style and has not yet descended.
        //
        // The field is "correct only after the root has resolved", and that is
        // the specification rather than a drawback: on the root's own
        // `font-size`, `rem` refers to the initial value, because the value
        // being computed cannot be its own input. That case is
        // `ComputedStyle.of`'s and is handled there; this is every other node.
        //
        // A root that paints nothing and styles nothing keeps the configured
        // value, which is right for the same reason: it computed no size, so
        // there is none to be the root's.
        if (element.parent() == null && self != null) {
            lengthsBelowRoot =
                    lengths.withRootFontSize((float) self.typography().size());
        }

        // The target the cascade just produced, and the values actually in
        // flight. `self` stays the target -- it is what the next frame diffs
        // against, and what children inherit -- while `painted` carries the
        // overlay. An animated value is never written back into computed
        // style, because a cascade that saw the halfway colour as the node's real
        // one would start a second transition from it and never arrive.
        var painted = self;
        // Asked on every element's first styled frame, whether or not it
        // transitions, so the flag means "has been styled" and not "has been
        // styled while something moved". The cascade behind it runs only when a
        // sheet has a starting rule and one matches.
        var entering = self != null && element.firstStyled();
        // `element.widget() instanceof Paints` first, and it is not an
        // optimisation. A composition node has no box, so `painted` is discarded
        // a few lines below — but `animations.observe` and `animations.settle`
        // are not free of consequence: observing the **inherited** style on a node
        // that paints nothing starts transitions keyed on an ancestor's values,
        // and `settle` then reports them as animating, which keeps the frame loop
        // awake for a node that could not draw a frame if it had one.
        if (element.widget() instanceof Paints
                && self != null
                && (!self.transitions().isEmpty() || !self.animations().isEmpty() || element.isAnimating())) {
            var motionBegan = trace == null ? 0L : System.nanoTime();
            var animations = element.animations();
            var target = reducedMotion ? reduced(self) : self;
            if (entering && !self.transitions().isEmpty()) {
                var starting = startingStyle(element, inherited);
                if (starting != null) {
                    // Observed first, so the target observed next is a change
                    // from it and every transition it declares starts from the
                    // starting value -- which is all `@starting-style` is.
                    animations.observe(reducedMotion ? reduced(starting) : starting, now);
                }
            }
            animations.observe(target, now);
            // Keyframes beneath transitions, CSS's order.
            var keyframed = animations.animate(target, self, now, (name, style) -> track(element, name, style));
            painted = animations.apply(keyframed, now);
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
            children.addAll(render(
                    child,
                    handDown,
                    disabledAbove || (element.widget() instanceof Styled owner && owner.isDisabled()),
                    now));
        }

        if (!(element.widget() instanceof Paints paints)) {
            // A composition node: it has no box of its own, so its children
            // become its parent's directly.
            return children;
        }

        // Tagged with the element that produced it, which is how a pointer
        // event gets from a rectangle on screen back to a node.
        var boxBegan = trace == null ? 0L : System.nanoTime();
        var style = Objects.requireNonNull(painted, "a node that paints resolved a style of its own above");
        // Set for the duration of the call and cleared after, so a context that
        // outlived the render -- a painter closing over it, which is exactly what
        // `canvas` does -- cannot read a stale node's tokens.
        currentElement = element;
        Box box;
        // Asked inside the same window as `render`, so a context read by the
        // answer -- a canvas's predicate is handed its `CanvasStyle` -- is still
        // this node's.
        boolean wantsFrame;
        try {
            box = paints.render(style, List.copyOf(children), paintContext).owner(element);
            wantsFrame = paints.isAnimating(style, paintContext);
        } finally {
            currentElement = null;
        }
        if (trace != null) {
            trace.boxes(System.nanoTime() - boxBegan);
        }

        // A widget that draws itself from the frame clock keeps the loop awake.
        // The idle loop stops asking for frames after the last transition
        // settles, and a spinner has no transition to settle -- so without this
        // it would be painted once and left there.
        //
        // **After `render`, and that is worth one frame of every animation in the
        // toolkit.** A clock-driven animation is a `Phase`, and a phase learns it
        // has finished by being *read* -- which happens in `render`, the only
        // place a widget is handed the frame clock. Asked beforehand, the frame
        // that finishes an arrival still answers "yes" and the frame after it is
        // the one that goes quiet: one wasted frame per arrival, per widget.
        animating |= wantsFrame;
        return List.of(box);
    }
}
