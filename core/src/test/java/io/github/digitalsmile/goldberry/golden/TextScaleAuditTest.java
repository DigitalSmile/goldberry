package io.github.digitalsmile.goldberry.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.golden.TextScaleAudit.Cut;
import io.github.digitalsmile.goldberry.golden.TextScaleAudit.Direction;
import io.github.digitalsmile.goldberry.golden.TextScaleAudit.Result;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.paint.overflow.Overrun;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.flow.TextFlow;
import io.github.digitalsmile.goldberry.text.flow.TextOverflow;
import io.github.digitalsmile.goldberry.text.font.Font;

/// The rule [ADR-0435] decided on, on boxes small enough to argue about.
///
/// The gallery's eleven screens are where it is *used*; they are not where it can
/// be shown to work, because a screen that passes proves only that nothing was
/// wrong and a rule that reported nothing ever would pass it too. So the
/// assertions here are the four answers the rule has to be able to give — fits,
/// cut, cut on purpose, cut off the bottom — on a box whose numbers are in this
/// file.
class TextScaleAuditTest {

    /// Long enough that nothing narrow fits it, and ordinary enough that it says
    /// something when it is quoted into a failure message.
    private static final String TEXT = "Export the current selection to a file on disk";

    private static final int INK = 0xFFFFFFFF;

    private Font font;

    @BeforeEach
    void openFont() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 14);
    }

    @AfterEach
    void closeFont() {
        if (font != null) {
            font.close();
        }
    }

    /// A child of a column, never the root: Yoga sizes the root from the space it
    /// was handed and never asks a measure function at the top of the tree, which
    /// would make every one of these pass for the wrong reason.
    private static Box column(Box child) {
        return Box.of()
                .direction(FlexDirection.COLUMN)
                .size(Length.points(400), Length.points(300))
                .children(child);
    }

    private List<Cut> cutsOf(Box child) {
        var target = TestFrames.of(400, 300, 1.0f);
        try {
            return TextScaleAudit.cuts(target.frame(), column(child));
        } finally {
            target.end();
        }
    }

    @Nested
    @DisplayName("the rule")
    class TheRule {

        @Test
        @DisplayName("says nothing about a paragraph that wrapped into the room it had")
        void wrappedTextFits() {
            var box = Box.text(Paragraph.of(font, TEXT), INK);

            assertEquals(List.of(), cutsOf(box), "a paragraph sized around its own wrapping is not cut");
        }

        @Test
        @DisplayName("reports a `nowrap` label narrower than its own line, and says nobody asked")
        void aCutLabelIsReported() {
            // `nowrap` is what makes this possible at all: the measure function
            // reports the width the paragraph *wants* rather than the width it was
            // offered, so the box can be laid out narrower than its own content
            // (ADR-0235). Under `normal` the same 80 points would wrap instead.
            var box = Box.text(Paragraph.of(font, TEXT), INK, nowrap()).size(Length.points(80), Length.AUTO);

            var cuts = cutsOf(box);

            assertEquals(1, cuts.size(), () -> "one cut, and got " + cuts);
            var cut = cuts.getFirst();
            assertEquals(Direction.ACROSS, cut.direction());
            assertFalse(cut.marked(), "nothing in this box's flow asked for a cut");
            assertTrue(cut.needed() > cut.available(), cut::toString);
            assertTrue(cut.toString().contains("Export the current selection"), cut::toString);
        }

        @Test
        @DisplayName("and marks the same cut when `text-overflow: ellipsis` asked for it")
        void anEllipsisMarksIt() {
            var box = Box.text(Paragraph.of(font, TEXT), INK, TextFlow.ELLIPSIS).size(Length.points(80), Length.AUTO);

            var cuts = cutsOf(box);

            assertEquals(1, cuts.size(), () -> "still one cut, and got " + cuts);
            assertTrue(cuts.getFirst().marked(), "the ellipsis is the stylesheet saying it meant this");
            assertEquals(List.of(), cuts.stream().filter(cut -> !cut.marked()).toList());
        }

        @Test
        @DisplayName("reports a paragraph taller than a box that was given a height")
        void textCutOffTheBottomIsReported() {
            // One line's worth of height around text that needs several. Nothing
            // marks this: `text-overflow` is applied per line, so a line that fell
            // out of the bottom simply is not drawn.
            var box = Box.text(Paragraph.of(font, TEXT), INK)
                    .size(Length.points(120), Length.points(18))
                    .shrink(0);

            var down = cutsOf(box).stream()
                    .filter(cut -> cut.direction() == Direction.DOWN)
                    .toList();

            assertEquals(1, down.size(), () -> "one vertical cut, and got " + cutsOf(box));
            assertFalse(down.getFirst().marked(), "there is no property that marks this, so it is never asked for");
        }

        @Test
        @DisplayName("measures inside the padding, because that is where the painter draws")
        void paddingIsTakenOffFirst() {
            // The same box twice at the same width. The padded one has 60 points
            // less room for the same line, and a rule that compared against the
            // border box rather than the content box would call them both fine —
            // which is ADR-0111's bug, asserted from the other side.
            var bare = Box.text(Paragraph.of(font, TEXT), INK, nowrap()).size(Length.points(200), Length.AUTO);
            var padded = Box.text(Paragraph.of(font, TEXT), INK, nowrap())
                    .size(Length.points(200), Length.AUTO)
                    .padding(Length.points(30));

            assertTrue(cutsOf(padded).size() >= cutsOf(bare).size(), "padding never buys a paragraph more room");
            var across = cutsOf(padded).stream()
                    .filter(cut -> cut.direction() == Direction.ACROSS)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the padded box's line has 140 points and wants more"));
            assertEquals(140, across.available(), 1.0, "200 less 30 a side");
        }

        /// `nowrap` with nothing marking it — [TextFlow#ELLIPSIS]'s other half, and
        /// the combination a label gets when a stylesheet sets `white-space` and
        /// forgets `text-overflow`.
        private static TextFlow nowrap() {
            return TextFlow.ELLIPSIS.textOverflow(TextOverflow.CLIP);
        }
    }

    @Nested
    @DisplayName("the comparison between two scales")
    class TheComparison {

        private static Cut cut(String box, boolean marked) {
            return new Cut(box, TEXT, Direction.ACROSS, marked, 200, 100);
        }

        private static Result result(double scale, List<Cut> cuts) {
            return new Result(scale, cuts, List.of(), 12);
        }

        @Test
        @DisplayName("passes when 150% cuts nothing 100% did not")
        void unchangedPasses() {
            var normal = result(1.0, List.of(cut("`label`", false)));
            var large = result(1.5, List.of(cut("`label`", false)));

            TextScaleAudit.assertSurvivesLargeText("a screen", normal, large);
        }

        @Test
        @DisplayName("passes when 150% adds an ellipsis, because that is the feature working")
        void aNewEllipsisPasses() {
            var normal = result(1.0, List.of());
            var large = result(1.5, List.of(cut("`chip`", true)));

            TextScaleAudit.assertSurvivesLargeText("a screen", normal, large);
        }

        @Test
        @DisplayName("fails when 150% cuts a paragraph nobody asked to cut")
        void aNewSilentCutFails() {
            var normal = result(1.0, List.of());
            var large = result(1.5, List.of(cut("`chip`", false)));

            var failure = assertThrows(
                    AssertionFailedError.class,
                    () -> TextScaleAudit.assertSurvivesLargeText("the Forms screen", normal, large));

            assertTrue(failure.getMessage().contains("the Forms screen"), failure::getMessage);
            assertTrue(failure.getMessage().contains("`chip`"), failure::getMessage);
            assertTrue(failure.getMessage().contains("150%"), failure::getMessage);
        }

        @Test
        @DisplayName("and fails on a tree with no text in it, which is a rule that asserted nothing")
        void anEmptyTreeFails() {
            var empty = new Result(1.0, List.of(), List.of(), 0);

            assertThrows(
                    AssertionFailedError.class, () -> TextScaleAudit.assertSurvivesLargeText("a screen", empty, empty));
        }
    }

    @Nested
    @DisplayName("the accepted list")
    class TheRatchet {

        private static final String KEY = "`row#actions` > `button#undo`";

        private static Result with(double scale, List<Overrun> overruns) {
            return new Result(scale, List.of(), overruns, 12);
        }

        private static Overrun overrun() {
            return new Overrun("`row#actions`", "`button#undo`", 43, 0);
        }

        @Test
        @DisplayName("forgives an overrun it names")
        void anAcceptedOverrunPasses() {
            TextScaleAudit.assertSurvivesLargeText(
                    "the basic screen", with(1.0, List.of()), with(1.5, List.of(overrun())), List.of(KEY));
        }

        @Test
        @DisplayName("and does not forgive the one beside it")
        void anUnacceptedOverrunFails() {
            var other = new Overrun("`row#shapes-row`", "`button#circle-button`", 5, 0);

            var failure = assertThrows(
                    AssertionFailedError.class,
                    () -> TextScaleAudit.assertSurvivesLargeText(
                            "the basic screen",
                            with(1.0, List.of()),
                            with(1.5, List.of(overrun(), other)),
                            List.of(KEY)));

            assertTrue(failure.getMessage().contains("`button#circle-button`"), failure::getMessage);
            assertFalse(failure.getMessage().contains("`button#undo`"), failure::getMessage);
        }

        /// The half that makes it a ratchet rather than a list of excuses: a line
        /// left behind once the defect is fixed would silently welcome it back.
        @Test
        @DisplayName("and fails when an accepted overrun stops happening, so the line is deleted")
        void anAcceptedOverrunThatIsFixedFails() {
            var failure = assertThrows(
                    AssertionFailedError.class,
                    () -> TextScaleAudit.assertSurvivesLargeText(
                            "the basic screen", with(1.0, List.of()), with(1.5, List.of()), List.of(KEY)));

            assertTrue(failure.getMessage().contains("no longer happens"), failure::getMessage);
            assertTrue(failure.getMessage().contains(KEY), failure::getMessage);
        }

        @Test
        @DisplayName("and the key is the pair of names, not the distance, which moves with the scale")
        void theKeyIsTheShape() {
            assertEquals(KEY, TextScaleAudit.key(overrun()));
            assertEquals(KEY, TextScaleAudit.key(new Overrun("`row#actions`", "`button#undo`", 185, 0)));
        }
    }
}
