package io.github.digitalsmile.goldberry.widgets.core.scroll;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.input.handler.Measured;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// Where a [Scroll] is — the whole of what it remembers.
///
/// `docs/core-widgets.md` §1: "scroll position is retained state surviving
/// rebuilds". This is that state, and it needs no key and no application field to
/// survive one: the element tree keeps a state across every rebuild of the widget
/// that described it, which is what the element layer is for
/// (ADR-0052).
///
/// **Unlike a control's value, this is genuinely the widget's own.** ADR-0063
/// sends every *value* up to the application and reads it back down through
/// `bind`, and a scroll position is the exception that proves the rule: it is not
/// a value the application has an opinion about, it is where a rectangle happens
/// to be. An application that made a list scroll to the top would be doing so
/// through `scrollIntoView`, not by owning the offset.
///
/// Nothing here clamps. The clamp is [ScrollViewport]'s, because clamping needs
/// the two extents and only the viewport is handed them — this stores what it is
/// told ([ADR-0116]).
final class ScrollState extends State<Scroll> {

    private double offsetX;
    private double offsetY;

    /// What the last frame laid this viewport and its content out as.
    ///
    /// The clamp does not need these — the extents arrive on the event that asks
    /// to move (ADR-0116) — but a **scrollbar** does: a thumb whose length says
    /// how much of the document is visible has to be right before anything has
    /// been touched. They arrive through [Measured], which is the other direction
    /// ([ADR-0117]).
    private Extent viewport = Extent.NONE;
    private Extent content = Extent.NONE;

    /// When the bars were last woken, and whether something is holding them open.
    private final ScrollFade fade = new ScrollFade();

    /// A programmatic scroll on its way, drawn by the viewport (ADR-0363).
    private final ScrollGlide glide = new ScrollGlide();

    /// Which bar the pointer is dragging, or null.
    private @Nullable Boolean draggingVertical;

    /// Attaches to the controller the application gave this viewport, if any.
    ///
    /// On mount rather than on every build, so the attachment survives rebuilds
    /// and a controller is never pointed at a state that is on its way out.
    @Override
    protected void initState() {
        attach(widget().controller());
    }

    @Override
    protected void didUpdateWidget(Scroll previous) {
        if (previous.controller() != widget().controller()) {
            // A viewport handed a different controller lets go of the old one
            // first, or two controllers would both believe they drive this and
            // the stale one would scroll a viewport nobody expects.
            if (previous.controller() != null && previous.controller().attached == this) {
                previous.controller().attached = null;
            }
            attach(widget().controller());
        }
    }

    /// Lets go, so a controller an application still holds cannot scroll a tree
    /// that has been unmounted.
    ///
    /// The reference is kept here rather than read back off `widget()`, which
    /// throws once a state is disposed — and a leak whose cleanup depends on the
    /// thing being cleaned up is not cleanup.
    @Override
    protected void dispose() {
        if (held != null && held.attached == this) {
            held.attached = null;
        }
        held = null;
    }

    /// The controller currently pointed at this state.
    private ScrollController held;

    private void attach(ScrollController controller) {
        held = controller;
        if (controller != null) {
            controller.attached = this;
        }
    }

    private static final org.slf4j.Logger LOG = io.github.digitalsmile.goldberry.log.Logs.of(ScrollState.class);

    /// The axes already reported nested, so the canon's ban is a message rather
    /// than a stream ([ADR-0251]).
    ///
    /// `build` runs per element per invalidation, so an unguarded warning here
    /// would be the log ADR-0243 has just finished quietening. Static and by axis
    /// because the thing worth saying is *"this application nests scrollers"* and
    /// it is worth saying once — a document that does it in four places has one
    /// mistake, not four.
    private static final java.util.Set<ScrollAxis> REPORTED_NESTING =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /// Forgets what has been reported, for a test that drives the same nesting
    /// twice. `ComputedStyle.forgetReportedDrops`'s reason exactly.
    static void forgetReportedNesting() {
        REPORTED_NESTING.clear();
    }

    /// Whether anything has been reported nested, for the test — see
    /// [#REPORTED_NESTING]. There is no appender on the classpath to read the log
    /// back from, so the set is what an assertion can see.
    static boolean reportedNesting() {
        return !REPORTED_NESTING.isEmpty();
    }

    /// How many axes have been reported, so a test can say *once* rather than
    /// merely *at all*.
    static int reportedNestingCount() {
        return REPORTED_NESTING.size();
    }

    /// Whether this viewport is inside another one **on the same axis**, which
    /// `docs/core-widgets.md` §2.4 rules out.
    ///
    /// Nothing enforced it, and chaining means a nested pair behaves reasonably
    /// rather than badly — so the ban cost nothing and the author heard nothing.
    /// This is the diagnostic, and it is deliberately *only* a diagnostic: the
    /// arrangement still works, because refusing to build it would turn a design
    /// rule into a crash.
    ///
    /// [BuildContext#findAncestorState] is the whole implementation. It exists
    /// for `scrollIntoView` and answers this question with nothing added — which
    /// is the argument for asking it here rather than teaching the renderer about
    /// scroll views.
    private void warnIfNestedOnTheSameAxis(BuildContext context) {
        context.findAncestorState(ScrollState.class).ifPresent(outer -> {
            if (outer.widget().axis() == widget().axis() && REPORTED_NESTING.add(widget().axis())) {
                LOG.warn(
                        "a {} `scroll` is inside another one; §2.4 rules that out, and the inner"
                                + " one takes the wheel until it reaches its edge. Give the inner"
                                + " box a size and let the outer one scroll, or make them"
                                + " different axes.",
                        widget().axis().toString().toLowerCase(java.util.Locale.ROOT));
            }
        });
    }

    @Override
    public Widget build(BuildContext context) {
        warnIfNestedOnTheSameAxis(context);
        var scroll = widget();
        return new ScrollViewport(
                scroll.children(),
                scroll.axis(),
                scroll.height(),
                offsetX,
                offsetY,
                viewport,
                content,
                fade,
                glide,
                this::moveTo,
                draggingVertical,
                this::drag,
                this::measured,
                line,
                this::lined,
                scroll.attributes());
    }

    /// Told what the last frame produced, by the router that holds the painted
    /// rectangles.
    ///
    /// ## Why this rebuilds, and why that terminates
    ///
    /// A thumb's length is drawn from these, so they have to reach a `build` —
    /// and nothing else would take them there. The obvious worry is the loop:
    /// a measurement causes a rebuild, which causes a frame, which produces a
    /// measurement.
    ///
    /// It terminates because **nothing this rebuild draws can change what was
    /// measured**. The bars are absolutely positioned, so they take no space from
    /// the content and none from the viewport; the second frame measures exactly
    /// what the first did, the router sees no change and notifies nobody
    /// ([ADR-0117]). One extra frame when a window resizes, and none after it.
    ///
    /// The guard here is belt to the router's braces. It is cheap, and the thing
    /// it protects against — a scroll view repainting forever — is expensive
    /// enough to be worth two comparisons.
    /// What one wheel line moves, from `--gb-scroll-line` or its default.
    ///
    /// Held here because the widget is a value rebuilt every frame and the wheel
    /// arrives where there is no cascade to ask — the same reason `viewport` and
    /// `content` are here (ADR-0251).
    private double line = ScrollViewport.LINE;

    private void lined(double value) {
        if (value == line) {
            return;
        }
        setState(() -> line = value);
    }

    private void measured(Extent bounds, Extent part) {
        if (bounds.equals(viewport) && part.equals(content)) {
            return;
        }
        setState(() -> {
            viewport = bounds;
            content = part;
        });
    }

    /// Starts or ends a thumb drag, holding the bars open for its duration.
    private void drag(Boolean vertical, Boolean active) {
        setState(() -> {
            draggingVertical = active ? vertical : null;
            fade.hold(active);
        });
    }

    /// Moves by `dx`, `dy` from wherever it is, clamped to what there is to show.
    ///
    /// What [Reveal] calls, and the only thing a descendant may ask of a scroll
    /// view. A **distance** rather than a target, so the viewport needs to know
    /// nothing about what asked or why ([ADR-0120]).
    ///
    /// The clamp is the same one every other path takes, so a child asking to be
    /// revealed cannot scroll past the end any more than a wheel can.
    void scrollBy(double dx, double dy) {
        var x = clamp(offsetX + dx, viewport.overflowX(content));
        var y = clamp(offsetY + dy, viewport.overflowY(content));
        if (x == offsetX && y == offsetY) {
            return;
        }
        // A programmatic move glides: the offset goes to the target now, and the
        // viewport draws the way there on the frame clock (ADR-0363).
        glide.start(offsetX, offsetY, x, y);
        setState(() -> {
            offsetX = x;
            offsetY = y;
            fade.woken();
        });
    }

    /// How much further right the viewport will draw once the glide lands —
    /// what a rectangle painted this frame still has to travel.
    double glideRemainingX() {
        return offsetX - glide.shownX(offsetX);
    }

    /// How much further down it will draw.
    double glideRemainingY() {
        return offsetY - glide.shownY(offsetY);
    }

    private static double clamp(double value, double max) {
        return value < 0 ? 0 : value > max ? max : value;
    }

    /// Takes the offset the viewport arrived at and asks for a frame.
    ///
    /// `setState` rather than a plain assignment, so the move reaches the screen
    /// by the route every other change takes: the element is marked dirty and the
    /// tree rebuilds it once, however many wheel events arrived in one frame.
    private void moveTo(double x, double y) {
        if (x == offsetX && y == offsetY) {
            return;
        }
        // Direct input: whatever was gliding stops where the pointer took over.
        glide.cancel();
        setState(() -> {
            offsetX = x;
            offsetY = y;
            // The bars wake on any movement, whatever caused it -- a wheel, a
            // key, a drag or a track click. `moveTo` is the one place all four
            // arrive, which is why the wake is here rather than in each handler.
            fade.woken();
        });
    }
}
