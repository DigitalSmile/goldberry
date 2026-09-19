package io.github.digitalsmile.goldberry.golden;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.opentest4j.AssertionFailedError;

import io.github.digitalsmile.goldberry.css.StyleElement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.overflow.OverflowLog;
import io.github.digitalsmile.goldberry.paint.overflow.Overrun;
import io.github.digitalsmile.goldberry.paint.tree.RenderTree;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.font.Fonts;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;

/// What §1.4's 150% text asserts, now that `text-overflow: ellipsis` means some
/// cutting is correct.
///
/// ## Why this is not a golden
///
/// The obvious answer is eleven more PNGs: the gallery, re-photographed with
/// `renderer.textScale(1.5)`. It is the wrong one, and for a sharper reason than
/// cost. A golden asserts *this is what it drew*, which a wrong picture satisfies
/// exactly as well as a right one. Before ellipsis shipped that was survivable —
/// the rule was "no text is clipped", a human could check it by eye once, and the
/// image then held the answer. It is not survivable now: at 150% some labels are
/// **supposed** to end in `…`, some are supposed to wrap, and which is which is a
/// decision per label. A picture of eleven screens would pin all of them at once,
/// in a form nobody can review, before anybody had taken a single one
/// ([ADR-0435]).
///
/// So the assertion is a **rule**, evaluated against the laid-out tree, and the
/// rule is one sentence:
///
/// > Every line of every paragraph either fits the box it was given, or is cut
/// > somewhere a stylesheet asked to be cut.
///
/// ## The three things this is not
///
/// - **Not "no text is clipped".** That sentence died with `text-overflow`. A
///   `nowrap` label with `ellipsis` on it is *meant* to run out of room; the
///   ellipsis is the designer saying so.
/// - **Not "every ellipsis at 150% was reachable at 100%".** That one is
///   backwards. Growing the text is exactly what makes a new ellipsis appear, and
///   a check that forbade it would forbid the feature.
/// - **Not "nothing overlaps".** Siblings overlap on purpose all over the
///   catalog — an absolute child, a popover over its anchor, a tab's underline
///   across its header, anything `elevated`. The exception list would be longer
///   than the rule.
///
/// ## How a cut is decided
///
/// By mirroring the painter, edge for edge, rather than by re-deriving it.
/// `BoxPainter` draws a paragraph **inside the padding**, at the width layout
/// settled on less that padding, wrapped when `white-space` wraps and laid out
/// unconstrained when it does not ([ADR-0255]). This asks the paragraph the same
/// question with the same numbers, and compares:
///
/// - **across** — the widest line against the content width. Over it is a cut,
///   and [Cut#marked] says whether `text-overflow` asked for one. This is the arm
///   that is *asserted*: under `nowrap` a line is measured at the width it wants
///   and drawn at the width it has ([ADR-0235]), so a line over its box is a line
///   somebody does not get to read.
/// - **down** — the layout's height against the content height, in
///   [Result#spills]. **Measured and not asserted**, and the distinction is the
///   one thing about this class worth arguing over. Nothing in the painter clips
///   a paragraph vertically; a fourth line past the bottom of a three-line box is
///   *drawn*, on top of whatever is below it, unless some ancestor's `overflow`
///   cuts it. So a spill on its own is not lost text — it is a box that overran,
///   wearing a paragraph's clothes, and the honest place for it is the overrun
///   arm. It is counted because it is the best evidence in the repository about
///   where 150% actually hurts, and [ADR-0435] spends it there.
///
/// A half logical pixel of slack, because Yoga rounds a computed edge to a whole
/// device pixel and a paragraph measured at 118.5 into a box rounded to 118 has
/// not lost anything anybody can read.
///
/// ## The other arm, and why it is not the whole answer
///
/// A box that overruns its **container** — a 150% label inside a `height: 32px`
/// button — is what the `OverflowWatch` inside
/// [io.github.digitalsmile.goldberry.paint.tree.RenderTree]
/// reports, and it runs inside every layout already, so
/// [Result#overruns] carries whatever it said for free. That makes it half of
/// what is asserted and it cannot be all of it: the walk is gated on the **root**
/// node's `hadOverflow` ([ADR-0375]), so it sees a window that ran out of room
/// and stays silent about a button that did while the window had space to spare.
/// Its noise is a fact; its silence is not evidence.
///
/// ## A book, and not a choice
///
/// There is no single-`Font` form here, and that is the hinge of the whole
/// design rather than an omission. `WidgetRenderer`'s one-font constructor builds
/// its paint context as `style -> font` — the style is discarded, so the text
/// scale, which is applied where a `ComputedStyle` becomes a `Font`, **is applied
/// to nothing**. A 150% audit of the gallery taken the way the gallery's goldens
/// are taken would render exactly the 100% tree and pass for ever.
///
/// So this opens a book. Which means it is also the first check in the repository
/// to lay the gallery out with `font-family`, `font-size` and `font-weight`
/// resolved per node — the blindness ADR-0118 recorded and ADR-0386 chipped one
/// screen off. That was not the goal and it is not free: a heading is 20px
/// SemiBold here and 13px Regular in the golden beside it, so the two are
/// looking at different trees and only this one is looking at the real one.
///
/// ## The scale it runs at
///
/// One. The display scale stays at 1.0 and the *text* scale is the axis under
/// test, deliberately: a text scale is not a zoom, and multiplying this by
/// [ScaleInvariance]'s multipliers would be a third axis over the whole corpus
/// for a question neither of them is asking ([ADR-0434]).
public final class TextScaleAudit {

    /// §1.4's number: 150%.
    public static final double LARGE = 1.5;

    /// How much of a logical pixel a paragraph may exceed its box by before it
    /// counts as cut.
    ///
    /// Half of one. Yoga rounds every computed edge to a whole device pixel, so a
    /// box is routinely a fraction narrower than the content it was measured
    /// around, and a check without slack would report every label in the catalog.
    private static final double SLACK = 0.5;

    /// How much of a paragraph a failure message quotes.
    private static final int QUOTE = 48;

    /// Which way a paragraph ran out of room.
    public enum Direction {

        /// The widest line is wider than the content box.
        ACROSS,

        /// The wrapped paragraph is taller than the content box, so a line at the
        /// bottom is not drawn at all.
        DOWN
    }

    /// One paragraph that did not fit the box it was laid out into.
    ///
    /// @param box       what to call the box — its type and id where the widget
    ///                  layer gave it one, as [Overrun] names its own
    /// @param text      the paragraph, quoted
    /// @param direction which way it ran out
    /// @param marked    whether `text-overflow` asked for the cut. Always false
    ///                  for [Direction#DOWN]: nothing marks a paragraph cut off at
    ///                  the bottom
    /// @param needed    the logical units the paragraph wanted
    /// @param available the logical units the content box had
    public record Cut(String box, String text, Direction direction, boolean marked, double needed, double available) {

        public Cut {
            Objects.requireNonNull(box, "box");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(direction, "direction");
        }

        /// What makes two cuts the same cut across two runs — everything but the
        /// numbers, which move with the scale by design.
        public String key() {
            return box + " " + direction + " \"" + text + "\"";
        }

        @Override
        public String toString() {
            return String.format(
                    Locale.ROOT,
                    "%s %s \"%s\" wants %.1f and has %.1f%s",
                    box,
                    direction == Direction.ACROSS ? "overruns across:" : "is cut off at the bottom:",
                    text,
                    needed,
                    available,
                    marked ? " (marked)" : "");
        }
    }

    /// What one audit found.
    ///
    /// @param textScale what the renderer's [WidgetRenderer#textScale(double)] was
    /// @param cuts      every paragraph that did not fit, marked or not
    /// @param overruns  what `OverflowWatch` said during the same layout, which is
    ///                  nothing at all unless the **root** overflowed
    /// @param paragraphs how many text boxes were looked at, so a rule that
    ///                  silently inspected none is a rule that fails
    public record Result(double textScale, List<Cut> cuts, List<Overrun> overruns, int paragraphs) {

        public Result {
            cuts = List.copyOf(cuts);
            overruns = List.copyOf(overruns);
        }

        /// A line wider than its box that nothing asked to cut — the arm that is
        /// asserted, because this is text the reader does not get.
        public List<Cut> silent() {
            return across().filter(cut -> !cut.marked()).toList();
        }

        /// The cuts `text-overflow: ellipsis` did ask for. Counted, never failed:
        /// a new `…` at 150% is the feature working.
        public List<Cut> marked() {
            return across().filter(Cut::marked).toList();
        }

        /// A paragraph taller than the box it was laid out into — **measured and
        /// not asserted**, and the note on [TextScaleAudit] says why: nothing
        /// clips it but an ancestor's `overflow`, so on its own it is a box that
        /// overran wearing a paragraph's clothes.
        public List<Cut> spills() {
            return cuts.stream()
                    .filter(cut -> cut.direction() == Direction.DOWN)
                    .toList();
        }

        private Stream<Cut> across() {
            return cuts.stream().filter(cut -> cut.direction() == Direction.ACROSS);
        }
    }

    private final int width;
    private final int height;
    private double textScale = 1.0;
    private List<Stylesheet> stylesheets = List.of();
    private @Nullable Fonts fonts;
    private int settleMillis = 200;

    private TextScaleAudit(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /// An audit of a tree laid out into `width` x `height` logical units.
    public static TextScaleAudit of(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("there is nothing to lay out: " + width + "x" + height);
        }
        return new TextScaleAudit(width, height);
    }

    /// The renderer's text scale. One by default, which is the control run.
    public TextScaleAudit textScale(double value) {
        this.textScale = value;
        return this;
    }

    /// The sheets the tree is cascaded against.
    public TextScaleAudit stylesheets(List<Stylesheet> sheets) {
        this.stylesheets = List.copyOf(Objects.requireNonNull(sheets, "sheets"));
        return this;
    }

    /// The book text is shaped against. **Owned by the caller** and not closed
    /// here; without one, a bundled book is opened for the call and closed after
    /// it.
    ///
    /// There is no single-`Font` form, and the note on this class says why: a
    /// renderer built over one font discards the style it would have applied the
    /// text scale to.
    public TextScaleAudit fonts(Fonts value) {
        this.fonts = Objects.requireNonNull(value, "fonts");
        return this;
    }

    /// How far the virtual clock moves between the settling passes.
    public TextScaleAudit settle(int millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("a settle time is not negative, and " + millis + " is");
        }
        this.settleMillis = millis;
        return this;
    }

    /// Builds, styles and lays `root` out at this audit's text scale, and reports
    /// what did not fit.
    ///
    /// The same settling sequence run by
    /// [io.github.digitalsmile.goldberry.offscreen.Offscreen#render(Widget)]
    /// — mount, lay out, feed the regions back, advance a frozen clock, lay out
    /// again — **and no paint at all**. That is not an optimization dressed as a
    /// principle: the question here is about rectangles, the rectangles come out
    /// of the layout pass, and eleven screens rasterized would cost more than the
    /// whole gallery's goldens to answer a question no pixel is evidence for.
    /// A self-measuring widget still needs its region fed back or a `masonry`
    /// would be audited on its first guess, which is why the passes are here at
    /// all.
    public Result audit(Widget root) {
        Objects.requireNonNull(root, "root");
        var ownFonts = fonts == null ? Fonts.bundled() : null;
        try {
            var clock = Clock.virtual();
            var renderer = renderer(ownFonts, clock).textScale(textScale);
            var target = TestFrames.of(width, height, 1.0f);
            var tree = new ElementTree(root);
            var cuts = new ArrayList<Cut>();
            var paragraphs = new int[1];
            OverflowLog.forget();
            try {
                try (var render = RenderTree.create()) {
                    var router = new PointerRouter();
                    router.windowBounds(LogicalRect.of(0, 0, width, height));
                    settle(target.frame(), renderer, tree, render, router);
                    clock.advance(settleMillis);
                    settle(target.frame(), renderer, tree, render, router);
                    // A third build and layout, for the reason `Offscreen` takes a
                    // third pass: a widget told its region may rearrange itself,
                    // and auditing the pass that delivered the news would audit
                    // the arrangement it is about to leave.
                    build(renderer, tree);
                    render.update(target.frame(), renderer.render(tree));
                    render.forEachPlacedBox(placed -> paragraphs[0] += inspect(placed, cuts));
                } finally {
                    target.end();
                }
            } finally {
                tree.unmount();
            }
            return new Result(textScale, cuts, OverflowLog.reported(), paragraphs[0]);
        } finally {
            if (ownFonts != null) {
                ownFonts.close();
            }
        }
    }

    /// Every paragraph in a hand-built `Box` tree that does not fit, with no
    /// widgets and no settling anywhere near it.
    ///
    /// The rule on its own, which is what a test of the rule wants: a box, a
    /// width, a `text-overflow`, and an answer.
    public static List<Cut> cuts(Frame frame, Box root) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(root, "root");
        var found = new ArrayList<Cut>();
        BoxPainter.forEachPlacedBox(frame, root, placed -> inspect(placed, found));
        return List.copyOf(found);
    }

    /// [#assertSurvivesLargeText(String, Result, Result, List)] for a scene with
    /// nothing wrong with it yet.
    public static void assertSurvivesLargeText(String name, Result normal, Result large) {
        assertSurvivesLargeText(name, normal, large, List.of());
    }

    /// Asserts that `large` breaks nothing `normal` had not already broken,
    /// except the breakages named in `accepted`.
    ///
    /// ## Differential rather than absolute
    ///
    /// An absolute "nothing is ever cut" would be a claim about the stylesheets as
    /// they stand, and it would fail on a label that has been a point too long
    /// since before any of this existed — which makes the 150% question hostage to
    /// an unrelated backlog. What §1.4 asks is narrower and answerable: **growing
    /// the text must not break what was not already broken.** A new `…` is
    /// allowed, because that is `text-overflow` working. A new silent cut is not,
    /// because that is a word nobody gets to read. A new overrun is not, because
    /// that is a control off the edge of the window.
    ///
    /// ## `accepted` is a ratchet, not an excuse list
    ///
    /// Every string in it is `container > child` for an overrun the screen has at
    /// 150% **today**, written out one per line with its reason beside it. It
    /// fails in both directions: a new overrun is a failure, and so is an accepted
    /// one that no longer happens — because a line left in this list after the
    /// defect is fixed is a line that will silently accept the defect coming back.
    ///
    /// @param name     what to call the scene in a failure message
    /// @param accepted `container > child` for each overrun this scene is known to
    ///                 have at 150% and is not fixed by this check
    public static void assertSurvivesLargeText(String name, Result normal, Result large, List<String> accepted) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(accepted, "accepted");
        if (normal.paragraphs() == 0) {
            throw new AssertionFailedError("\"" + name + "\" has no text in it at all, so this check asserted"
                    + " nothing; either the tree failed to build or it is the wrong tree");
        }
        var before = keys(normal.silent());
        var introduced = large.silent().stream()
                .filter(cut -> !before.contains(cut.key()))
                .toList();

        var known = Set.copyOf(accepted);
        var seen = new HashSet<String>();
        var newOverruns = new ArrayList<Overrun>();
        for (var overrun : large.overruns()) {
            var key = key(overrun);
            seen.add(key);
            if (known.contains(key) || normal.overruns().stream().anyMatch(was -> key(was).equals(key))) {
                continue;
            }
            newOverruns.add(overrun);
        }
        var fixed = accepted.stream().filter(key -> !seen.contains(key)).toList();

        if (introduced.isEmpty() && newOverruns.isEmpty() && fixed.isEmpty()) {
            return;
        }
        var message = new StringBuilder("\"")
                .append(name)
                .append("\" does not survive ")
                .append((int) Math.round(large.textScale() * 100))
                .append("% the way it survives 100%. Growing the text may add an ellipsis —")
                .append(" that is the feature — but it may not cut a paragraph nobody asked to cut,")
                .append(" and it may not push a box off the edge (ADR-0435).");
        for (var cut : introduced) {
            message.append("\n  ").append(cut);
        }
        for (var overrun : newOverruns) {
            message.append("\n  ").append(overrun);
        }
        for (var key : fixed) {
            message.append("\n  \"")
                    .append(key)
                    .append("\" is accepted here and no longer happens — delete the line, or the next")
                    .append(" time it breaks nothing will say so");
        }
        throw new AssertionFailedError(message.toString());
    }

    private static Set<String> keys(List<Cut> cuts) {
        var keys = new HashSet<String>();
        for (var cut : cuts) {
            keys.add(cut.key());
        }
        return keys;
    }

    /// What makes two overruns the same overrun — [OverflowLog]'s own key, because
    /// how far something overruns by moves with the scale and what overran does
    /// not.
    public static String key(Overrun overrun) {
        return overrun.container() + " > " + overrun.child();
    }

    /// Adds whatever `placed` cut, and answers whether it held a paragraph at all.
    private static int inspect(BoxPainter.Placed placed, List<Cut> into) {
        var box = placed.box();
        var text = box.text();
        if (text == null) {
            return 0;
        }
        var width = placed.layout().width();
        var height = placed.layout().height();
        // Exactly `BoxPainter`'s arithmetic, including that a top or bottom
        // percentage resolves against the height: the painter is the definition of
        // where the text goes, and a second opinion here would be a check that
        // passes while the picture is wrong.
        var left = Length.resolve(box.padding().left(), width);
        var right = Length.resolve(box.padding().right(), width);
        var top = Length.resolve(box.padding().top(), height);
        var bottom = Length.resolve(box.padding().bottom(), height);
        var contentWidth = Math.max(0, width - left - right);
        var contentHeight = Math.max(0, height - top - bottom);

        var flow = text.flow();
        var layout = text.paragraph().layout(flow.wraps() ? contentWidth : Paragraph.UNCONSTRAINED);
        var quoted = quote(text.paragraph().text());

        if (layout.width() > contentWidth + SLACK) {
            into.add(new Cut(name(box), quoted, Direction.ACROSS, flow.ellipsises(), layout.width(), contentWidth));
        }
        if (layout.height() > contentHeight + SLACK) {
            into.add(new Cut(name(box), quoted, Direction.DOWN, false, layout.height(), contentHeight));
        }
        return 1;
    }

    /// What to call a box in a report — `OverflowWatch`'s rule, because a cut and
    /// an overrun are read side by side and two naming schemes would read as two
    /// widgets.
    private static String name(Box box) {
        if (box.owner() instanceof StyleElement element) {
            var type = element.type();
            if (type != null) {
                return element.id() == null ? "`" + type + "`" : "`" + type + "#" + element.id() + "`";
            }
        }
        return "a box";
    }

    private static String quote(String text) {
        var flattened = text.replace('\n', ' ').strip();
        return flattened.length() <= QUOTE ? flattened : flattened.substring(0, QUOTE) + "…";
    }

    private WidgetRenderer renderer(@Nullable Fonts ownFonts, Clock clock) {
        var book = ownFonts != null ? ownFonts : Objects.requireNonNull(fonts, "fonts");
        return new WidgetRenderer(stylesheets, book).clock(clock);
    }

    private static void settle(
            Frame frame, WidgetRenderer renderer, ElementTree tree, RenderTree render, PointerRouter router) {

        build(renderer, tree);
        render.update(frame, renderer.render(tree));
        router.updateRegions(HitTest.capture(render));
    }

    private static void build(WidgetRenderer renderer, ElementTree tree) {
        renderer.prepare(tree);
        if (tree.needsBuild()) {
            tree.flush();
        }
    }
}
