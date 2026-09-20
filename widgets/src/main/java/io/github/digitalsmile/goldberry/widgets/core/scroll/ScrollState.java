package io.github.digitalsmile.goldberry.widgets.core.scroll;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.input.handler.Measured;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
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

    /// Whether this viewport is at the end, which is the whole of what
    /// [ScrollAnchor#END] needs to remember.
    private final ScrollStick stick = new ScrollStick();

    /// Attaches to the controller the application gave this viewport, if any.
    ///
    /// On mount rather than on every build, so the attachment survives rebuilds
    /// and a controller is never pointed at a state that is on its way out.
    @Override
    protected void initState() {
        attach(widget().controller());
        if (widget().anchor() == ScrollAnchor.END) {
            // Before the first layout there is nothing to be at the end *of*, so
            // this is not a position — it is the standing instruction that the
            // first measurement is to land there. "Opens at the end" and "stays
            // at the end" are then one piece of code rather than two, which is
            // what stops them disagreeing about the frame in between
            // (ADR-0392).
            stick.openAtEnd();
        }
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
    /// `docs/core-widgets.md` §2.4 discourages.
    ///
    /// Nothing enforced it, and chaining means a nested pair behaves reasonably
    /// rather than badly — so the rule cost nothing and the author heard nothing.
    /// This is the diagnostic, and it is deliberately *only* a diagnostic: the
    /// arrangement still works, because refusing to build it would turn a design
    /// rule into a crash.
    ///
    /// ## Why it is `debug` and not `warn`
    ///
    /// Because it was firing on the arrangement its own advice describes
    /// ([ADR-0394]). The showcase's `scroll.tall-list` is a virtual list given a
    /// height of 256 px and told not to grow, inside the gallery's viewport —
    /// which is "give the inner box a size and let the outer one scroll", done.
    /// It is also what every chat window, console and settings page is, and the
    /// engine's behaviour in it is defined rather than accidental: the inner one
    /// takes the wheel until it reaches its edge.
    ///
    /// What §2.4 is actually about is an inner viewport with **no size of its
    /// own** on the scrolling axis, which grows to its content and leaves the
    /// wheel ambiguous. Telling those two apart needs the inner box's resolved
    /// height, and this runs in `build`, before the cascade has resolved
    /// anything. So the message stays, at a level that does not claim something
    /// is broken, and the sharper rule waits for a layout-time signal.
    ///
    /// [BuildContext#findAncestorState] is the whole implementation. It exists
    /// for `scrollIntoView` and answers this question with nothing added — which
    /// is the argument for asking it here rather than teaching the renderer about
    /// scroll views.
    private void warnIfNestedOnTheSameAxis(BuildContext context) {
        context.findAncestorState(ScrollState.class).ifPresent(outer -> {
            if (outer.widget().axis() == widget().axis() && REPORTED_NESTING.add(widget().axis())) {
                LOG.debug(
                        "a {} `scroll` is inside another one; §2.4 discourages that, and the inner"
                                + " one takes the wheel until it reaches its edge. Give the inner"
                                + " box a size of its own and let the outer one scroll, or make"
                                + " them different axes.",
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
                scroll.preservesOnPrepend(),
                this::shiftBy,
                draggingVertical,
                this::drag,
                this::measured,
                line,
                this::lined,
                gutter,
                this::guttered,
                scroll.attributes());
    }

    /// What one wheel line moves, from `--gb-scroll-line` or its default.
    ///
    /// Held here because the widget is a value rebuilt every frame and the wheel
    /// arrives where there is no cascade to ask — the same reason `viewport` and
    /// `content` are here (ADR-0251).
    private double line = ScrollViewport.LINE;

    /// §2.4's reserved gutter, from `--gb-scrollbar-gutter`, banked for the same
    /// reason [#line] is (ADR-0364).
    private double gutter;

    private void guttered(double value) {
        if (value != gutter) {
            setState(() -> gutter = value);
        }
    }

    private void lined(double value) {
        if (value == line) {
            return;
        }
        setState(() -> line = value);
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
    private void measured(Extent bounds, Extent part) {
        if (bounds.equals(viewport) && part.equals(content)) {
            return;
        }
        setState(() -> {
            viewport = bounds;
            content = part;
            // Whether this viewport was at the end is a question about the sizes
            // it had a moment ago, and they have just been replaced -- so the
            // answer is taken from `stick`, which was written the last time the
            // offset moved. Asking it here instead would say "no" for every
            // viewport a message has just arrived in, which is precisely the
            // case ([ScrollStick]).
            if (widget().anchor() == ScrollAnchor.END) {
                keepToTheEnd();
            }
        });
        notifyController();
    }

    /// Puts the offset back at the end on whichever axes were at it.
    ///
    /// The three things [ScrollAnchor#END] promises are this one line applied at
    /// three moments: the first measurement (opened at the end because
    /// [#initState] said so), a message arriving while the reader is at the
    /// bottom (still at the end, so it follows), and a message arriving while
    /// they are reading history (not at the end, so nothing here runs and the
    /// offset is untouched).
    ///
    /// **No glide and no woken bars.** The offset is where the content *is*; a
    /// glide would draw a 240ms slide every time a line was logged, and bars
    /// that woke would say the user had scrolled when they had not (ADR-0363).
    private void keepToTheEnd() {
        if (widget().axis().isHorizontal() && stick.atEndX()) {
            offsetX = viewport.overflowX(content);
        }
        if (widget().axis().isVertical() && stick.atEndY()) {
            offsetY = viewport.overflowY(content);
        }
    }

    /// Told that the content slid by `dx`, `dy` under a viewport nobody touched
    /// — rows were inserted above what the reader was looking at.
    ///
    /// Adds the distance to the offset, which is what keeps the screen still:
    /// the content moved down by that much and the window onto it moves down by
    /// the same, so the reader's line is drawn exactly where it was.
    ///
    /// An `END` viewport that is **at the end** does nothing here.
    /// [#keepToTheEnd] has already put it at the new end, which for an insertion
    /// above is the same number — and adding the shift on top of it would be
    /// counting the insertion twice. A `START` viewport that happens to be
    /// scrolled to its bottom is a different thing entirely: nothing put it back
    /// at the end, so it wants the shift like any other.
    ///
    /// Not a [#scrollBy]: that one glides, wakes the bars and reports a move.
    /// Nothing moved.
    private void shiftBy(double dx, double dy) {
        var end = widget().anchor() == ScrollAnchor.END;
        var x = widget().axis().isHorizontal() && !(end && stick.atEndX())
                ? clamp(offsetX + dx, viewport.overflowX(content))
                : offsetX;
        var y = widget().axis().isVertical() && !(end && stick.atEndY())
                ? clamp(offsetY + dy, viewport.overflowY(content))
                : offsetY;
        if (x == offsetX && y == offsetY) {
            return;
        }
        setState(() -> {
            offsetX = x;
            offsetY = y;
        });
        notifyController();
    }

    /// Re-reads "am I at the end" from where the offset now is.
    ///
    /// Called from the two places an offset is written on purpose — [#moveTo] and
    /// [#scrollBy] — rather than from the setters, so that [#keepToTheEnd] and
    /// [#shiftBy], which move the offset precisely in order *not* to move the
    /// view, leave the flag alone.
    private void settle() {
        stick.moved(offsetX, offsetY, viewport.overflowX(content), viewport.overflowY(content));
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
            // A deliberate move decides whether this viewport is following the
            // end: `scrollIntoView` onto the last row is how a timeline is
            // caught up with, and a reveal of something in the middle is how it
            // is left behind ([ScrollStick]).
            settle();
        });
        notifyController();
    }

    /// Which way this viewport moves — [State#widget()] is `protected`, so a
    /// [ScrollScope] beside it in this package cannot read the widget itself.
    ScrollAxis axis() {
        return widget().axis();
    }

    /// Scrolls the least it can to bring `self` inside `clip`, along `axes`.
    ///
    /// The arithmetic lives **here** rather than on [ScrollController] because a
    /// controller is no longer the only way in: [ScrollScope] reaches a viewport
    /// by walking up from the target, and two copies of "how far is it out of
    /// view" is how two callers end up disagreeing about what *in view* means
    /// ([ADR-0439]).
    void reveal(LogicalRect self, LogicalRect clip, ScrollAxis axes) {
        // `self` was painted where a glide had got to, and the offset is already
        // where it ends. Measure the rectangle where it will be, or a reveal asked
        // again mid-glide would move the viewport a second time (ADR-0363).
        var ahead = LogicalRect.of(
                (float) (self.left() - glideRemainingX()),
                (float) (self.top() - glideRemainingY()),
                self.size().width(),
                self.size().height());
        var dx = axes.isHorizontal()
                ? distance(
                        ahead.left(),
                        ahead.left() + ahead.size().width(),
                        clip.left(),
                        clip.left() + clip.size().width())
                : 0;
        var dy = axes.isVertical()
                ? distance(
                        ahead.top(),
                        ahead.top() + ahead.size().height(),
                        clip.top(),
                        clip.top() + clip.size().height())
                : 0;
        if (dx != 0 || dy != 0) {
            scrollBy(dx, dy);
        }
    }

    /// How far a viewport must move to bring `near`..`far` inside
    /// `clipNear`..`clipFar`, or 0 when it already is.
    ///
    /// Positive means further down or right. **The least it can**: a reveal that
    /// centred its target would throw away everything the user was already
    /// looking at, and §1 asks for the target to be in view rather than for it to
    /// be anywhere in particular.
    ///
    /// The near edge wins when the target is larger than the viewport, because
    /// showing the top of something too big to fit is what every browser does —
    /// the alternative shows its bottom and hides the heading.
    private static double distance(float near, float far, float clipNear, float clipFar) {
        if (near < clipNear) {
            return near - clipNear;
        }
        if (far > clipFar) {
            return Math.min(far - clipFar, near - clipNear);
        }
        return 0;
    }

    /// Where this viewport is, for [ScrollController#position()].
    ScrollController.Position position() {
        return new ScrollController.Position(
                offsetX,
                offsetY,
                viewport.overflowX(content),
                viewport.overflowY(content),
                viewport.width(),
                viewport.height());
    }

    /// Tells the controller its position moved.
    private void notifyController() {
        if (held != null && held.attached == this) {
            held.changed();
        }
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
            // And it is the one place the user says whether they are still
            // following the end. `End` and a drag to the bottom turn the stick
            // back on; a single pixel up turns it off, because a pixel is more
            // than [ScrollStick#TOLERANCE] and a pixel up is a decision.
            settle();
        });
        notifyController();
    }
}
