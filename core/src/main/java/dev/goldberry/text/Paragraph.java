package dev.goldberry.text;

import java.text.Bidi;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.layout.Direction;
import dev.goldberry.layout.Measure;
import dev.goldberry.layout.MeasureMode;
import dev.goldberry.layout.MeasuredSize;
import dev.goldberry.paint.Frame;
import dev.goldberry.text.bidi.BidiLevels;
import dev.goldberry.text.bidi.Directionality;
import dev.goldberry.text.bidi.LevelRun;
import dev.goldberry.text.flow.OverflowWrap;
import dev.goldberry.text.flow.TextAlign;
import dev.goldberry.text.flow.TextDecoration;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.text.flow.TextOverflow;
import dev.goldberry.text.flow.WordBreak;
import dev.goldberry.text.font.Fallbacks;
import dev.goldberry.text.font.Font;
import dev.goldberry.text.itemize.FaceChoice;
import dev.goldberry.text.itemize.Itemizer;
import dev.goldberry.text.itemize.Slot;
import dev.goldberry.text.itemize.TextRun;

/// A run of text in one font that wraps itself at any width, measures itself for
/// layout, and paints its lines.
///
/// ```java
/// var prose = Paragraph.of(font, "Prose that has to fit somewhere.");
/// TextLayout lines = prose.layout(240);        // wrapped at 240 logical px
/// prose.paint(frame, 16, 16, 240, 0xFFECEFF4);
/// ```
///
/// A widget never calls [#of] itself: `Paints.Context.paragraph(style, text)`
/// shapes through a [ParagraphCache] and returns the same instance each frame
/// for the same text. [#measureFunction] attaches a paragraph to a layout node
/// as its content, and [#widthBetween] and [#offsetAt] are what a caret and a
/// click are built on.
///
/// The text is shaped once, when the paragraph is created, and never again.
/// Wrapping is then arithmetic over that one [ShapedRun]: a line is a range of
/// glyphs, and re-wrapping at a new width produces new ranges over the same
/// glyphs, so a measure callback costs a scan rather than a shaping pass. That
/// is possible because shaping happens in the font's design units, which makes
/// the run independent of the size it is drawn at and so of the width it is
/// wrapped to. The layout engine asks for a paragraph's size several times a
/// pass, so the answer has to be that cheap.
///
/// One font unless it was joined. Text that Unicode draws as
/// a picture is shaped in the emoji face when [Font#emoji()] names one. A
/// cluster the font's face has no glyph for is shaped in the first of
/// [Font#fallbacks()] that has it, so a name in Han or Arabic in a Latin face
/// is drawn in letters rather than in `.notdef` boxes, and a run of one script
/// stays in one face so it shapes and joins as a word. Everything else is in
/// the face the cascade chose. Measurements are prefix sums in logical order
/// over one array of advances, concatenated from the shapings with each
/// rescaled into the font's design units. The line box is the font's own: a
/// glyph from another face may reach above or below it, as it may in a browser.
///
/// ## Styled paragraphs
///
/// [#join] puts paragraphs end to end as one: a keyword in bold in the middle of
/// a sentence in regular, wrapping as one text. Each piece keeps the shaping it
/// already has, emoji and all, so joining shapes nothing. The line breaker then
/// runs over the whole text, and a line is as tall as the tallest font on it.
/// The colour each stretch is drawn in is not part of the paragraph: it is a
/// [SpanPaint] handed to the painter, so a colour can change without a shaping.
///
/// ## Right-to-left text
///
/// A paragraph has a base direction, from the [Directionality] it was made
/// with, and text with right-to-left script in it is split into runs by the
/// bidirectional algorithm ([BidiLevels]). Each run is shaped in its own
/// direction. Measurement stays in logical order: a right-to-left run's glyphs
/// are held reversed in [#glyphs()], so a width is still one subtraction and
/// wrapping does not know the runs exist. Each line is then placed in visual
/// order, its runs left to right as rule L2 orders them, and [#caretX],
/// [#offsetAt] and [#stretchesOf] answer in that order. A paragraph with nothing
/// right to left in it under a left-to-right base has no levels at all and is
/// shaped, measured and drawn exactly as before bidi existed.
///
/// Breaks are not re-shaped. Each line is a slice of the whole paragraph's
/// shaping, so a kern between the last character of one line and the first of
/// the next is included where a per-line shaping would drop it. The error is a
/// fraction of a pixel and it buys wrapping that costs no shaping.
///
/// Confined to its font's thread. Not immutable, since it memoises the last
/// wrap, but it holds no native resources of its own, so there is nothing to
/// close. It must not outlive its font.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#paragraphs).
public final class Paragraph {

    /// What [#layout] is passed when there is no width constraint at all.
    public static final double UNCONSTRAINED = Double.POSITIVE_INFINITY;

    private final Font font;
    private final String text;

    /// The whole paragraph, shaped once, in the base font's design units.
    ///
    /// One run even when it took several faces to shape: an emoji or a
    /// fallback run's advances are scaled into this font's grid as they are
    /// appended, so every measurement below stays a prefix sum over one array.
    private final ShapedRun run;

    /// The pieces [#run] was concatenated from, each with the face that shaped it.
    ///
    /// One element for the paragraph that took one face, which is nearly every
    /// paragraph. More than one only when the text has emoji in it and something
    /// attached an emoji face to [#font], or has characters its face lacks and
    /// something attached fallbacks, or when it was joined.
    private final Segment[] segments;

    /// `advanceBeforeGlyph[g]` is the advance, in [#font]'s design units, of every
    /// glyph before index `g` — the prefix sums [#advanceBefore] is, indexed by
    /// glyph rather than by text offset.
    ///
    /// **Empty unless there is more than one segment.** It exists to place a
    /// segment's pen inside a line, and a paragraph with one segment never asks.
    private final int[] advanceBeforeGlyph;

    /// `advanceBefore[o]` is the advance, in design units, of every glyph whose
    /// cluster is before text offset `o`. Prefix sums, so the width of any range
    /// is one subtraction — which is what keeps the measure callback cheap.
    private final int[] advanceBefore;

    /// `glyphBefore[o]` is how many glyphs come before text offset `o`. The same
    /// trick, for turning a text range into a glyph range.
    private final int[] glyphBefore;

    /// The last wrap, kept because Yoga asks for the same width repeatedly within
    /// a pass and the paint that follows asks for it once more. One entry rather
    /// than a map: the access pattern is a run of identical widths, and a map
    /// would cost a hash of a `double` to serve the same hit.
    private double memoWidth = Double.NaN;
    private @Nullable TextLayout memo;

    /// How [#memo] was broken: [#breaking]'s answer for the flow it was laid out
    /// under. A paragraph is shared through the cache by every box drawing the
    /// same text in the same font, so two boxes may ask with different flows.
    private int memoBreaking = -1;

    /// The embedding levels, or null when every character is at level 0
    /// under a left-to-right base, which is nearly every paragraph.
    private final @Nullable BidiLevels bidi;

    /// What this paragraph's base direction came from, kept for [#join].
    private final Directionality directionality;

    /// Where each joined piece starts in [#text], and one past the last: null for
    /// a paragraph that was shaped in one font and never joined, which is nearly
    /// every paragraph.
    private final int @Nullable [] spanStarts;

    /// The font each joined piece was shaped in, index for index with
    /// [#spanStarts]. Null exactly when that is.
    private final Font @Nullable [] spanFonts;

    /// Each piece's ascent, and what its font's line takes below the baseline,
    /// read once at the join: a line's height is asked on every paint, and each
    /// answer is a downcall into the rasterizer. Null when every piece is in
    /// [#font], whose own two numbers answer.
    private final double @Nullable [] spanAscents;

    private final double @Nullable [] spanBelows;

    /// Whether every line is measured by [#font] alone: true for a paragraph
    /// that was never joined, and for a join whose pieces all share one font.
    /// A uniform paragraph is laid out and drawn exactly as a paragraph was
    /// before joining existed, one line height per line.
    private final boolean uniform;

    private Paragraph(Font font, String text, Directionality directionality, double letterSpacing, double wordSpacing) {
        var levels = BidiLevels.of(text, directionality);
        var bidi = levels.isLeftToRight() ? null : levels;
        this(
                font,
                text,
                directionality,
                bidi,
                spaced(shapeSegments(font, text, bidi), text, letterSpacing, wordSpacing),
                null,
                null);
    }

    /// `segments` with `letterSpacing` added after every character and
    /// `wordSpacing` to every space, in each segment's own design units.
    ///
    /// After shaping and before anything is measured, so line breaking, the
    /// caret, hit testing and the painter all see one set of advances. Added to
    /// the last glyph of each cluster, so a ligature or a letter with its
    /// combining mark takes the spacing once, after itself, as CSS says.
    private static Segment[] spaced(Segment[] segments, String text, double letterSpacing, double wordSpacing) {
        if (letterSpacing == 0 && wordSpacing == 0) {
            return segments;
        }
        var spaced = new Segment[segments.length];
        for (var s = 0; s < segments.length; s++) {
            var segment = segments[s];
            var run = segment.run();
            var font = segment.font();
            var perPixel = font.unitsPerEm() / font.size();
            var letter = (int) Math.round(letterSpacing * perPixel);
            var word = (int) Math.round(wordSpacing * perPixel);
            var advances = new int[run.length()];
            for (var i = 0; i < run.length(); i++) {
                var advance = run.xAdvance(i);
                if (i == run.length() - 1 || run.cluster(i + 1) != run.cluster(i)) {
                    advance += letter;
                    var at = segment.textStart() + run.cluster(i);
                    if (at < text.length() && isWordSeparator(text.charAt(at))) {
                        advance += word;
                    }
                }
                advances[i] = advance;
            }
            spaced[s] = new Segment(
                    font, run.withXAdvances(advances), segment.glyphStart(), segment.textStart(), segment.rtl());
        }
        return spaced;
    }

    /// CSS's word-separator characters that a desktop text meets: the space
    /// and the no-break space.
    private static boolean isWordSeparator(char c) {
        return c == ' ' || c == '\u00A0';
    }

    private Paragraph(
            Font font,
            String text,
            Directionality directionality,
            @Nullable BidiLevels bidi,
            Segment[] segments,
            int @Nullable [] spanStarts,
            Font @Nullable [] spanFonts) {
        this.font = font;
        this.text = text;
        this.directionality = directionality;
        this.bidi = bidi;
        this.segments = segments;
        this.run = segments.length == 1 && segments[0].font() == font && !segments[0].rtl()
                ? segments[0].run()
                : concatenate(font, segments);
        this.advanceBeforeGlyph = segments.length == 1 ? EMPTY_PREFIX : glyphPrefix(run);
        this.spanStarts = spanStarts;
        this.spanFonts = spanFonts;
        if (spanFonts == null || allIn(font, spanFonts)) {
            this.uniform = true;
            this.spanAscents = null;
            this.spanBelows = null;
        } else {
            this.uniform = false;
            var ascents = new double[spanFonts.length];
            var belows = new double[spanFonts.length];
            for (var k = 0; k < spanFonts.length; k++) {
                ascents[k] = spanFonts[k].ascent();
                belows[k] = spanFonts[k].lineHeight() - ascents[k];
            }
            this.spanAscents = ascents;
            this.spanBelows = belows;
        }

        var length = text.length();
        this.advanceBefore = new int[length + 1];
        this.glyphBefore = new int[length + 1];

        // Glyphs are in logical order here -- guaranteed, because a run shaped
        // right to left was reversed into it, and a paragraph with no levels
        // forced `LTR` on anything HarfBuzz would have ordered visually.
        // Several glyphs can share a cluster (a mark over a base) and a cluster
        // can span several characters (a ligature, a surrogate pair), so this
        // walks glyphs and fills the offsets each one covers.
        var advance = 0;
        var glyph = 0;
        var offset = 0;
        for (var i = 0; i < run.length(); i++) {
            var cluster = Math.clamp(run.cluster(i), 0, length);
            // Every offset from the last cluster up to this one is "before" the
            // glyphs counted so far. Offsets inside a ligature land here too and
            // get the width up to its start, which is the only honest answer:
            // there is no width for half a ligature, and no legal break inside
            // one either.
            while (offset <= cluster) {
                advanceBefore[offset] = advance;
                glyphBefore[offset] = glyph;
                offset++;
            }
            advance += run.xAdvance(i);
            glyph++;
        }
        while (offset <= length) {
            advanceBefore[offset] = advance;
            glyphBefore[offset] = glyph;
            offset++;
        }
    }

    /// What [#advanceBeforeGlyph] is when a paragraph has one segment and
    /// therefore never places a pen inside itself.
    private static final int[] EMPTY_PREFIX = new int[0];

    /// Whether every font in `fonts` is `font` itself. The same object, because
    /// a bold and a regular face at one size are two sets of metrics.
    private static boolean allIn(Font font, Font[] fonts) {
        for (var each : fonts) {
            if (each != font) {
                return false;
            }
        }
        return true;
    }

    /// One stretch of the text, and the face that shaped it.
    ///
    /// `run` is that face's **own** shaping, in its **own** design units — which
    /// is what it has to be to be drawn, because the rasterizer scales a run by
    /// the face's matrix. The concatenated [#run] holds the same glyphs scaled
    /// into the base font's grid instead, which is what it has to be to be
    /// measured. Two representations of one shaping, each in the units of the
    /// thing that reads it.
    ///
    /// A right-to-left segment's `run` is in the order HarfBuzz returns it,
    /// visual, which is what drawing wants; its glyphs in [#run] are the same
    /// ones reversed into logical order.
    ///
    /// @param glyphStart where this segment's glyphs start in the concatenated run
    /// @param textStart  where its text starts, for rebasing the clusters
    /// @param rtl        whether it was shaped right to left
    private record Segment(Font font, ShapedRun run, int glyphStart, int textStart, boolean rtl) {}

    /// Shapes `text` by runs, each in the face that should draw it and the
    /// direction its level says.
    ///
    /// One segment and one shaping unless the text has emoji in it and an emoji
    /// face is attached, or has characters the font's face lacks and fallbacks
    /// are attached, or has levels. A paragraph of prose costs what it cost
    /// before any of them existed: one `Itemizer` pass over the string when
    /// there is an emoji face, and one coverage scan when there are fallbacks,
    /// neither of which allocates for text that needs neither.
    ///
    /// With no levels, text that HarfBuzz would order visually, under
    /// `bidi-override: ltr`, is forced left to right, and anything else is
    /// guessed as it always was, so every paragraph the toolkit drew before
    /// bidi is shaped exactly as before.
    private static Segment[] shapeSegments(Font font, String text, @Nullable BidiLevels bidi) {
        if (bidi == null) {
            var direction = Bidi.requiresBidi(text.toCharArray(), 0, text.length()) ? TextDirection.LTR : null;
            return shapeSegments(font, text, direction);
        }
        var segments = new ArrayList<Segment>();
        var glyph = 0;
        for (var run : bidi.runs(0, text.length())) {
            var direction = run.isRightToLeft() ? TextDirection.RTL : TextDirection.LTR;
            glyph = shapeRange(segments, font, text, run.start(), run.end(), glyph, direction);
        }
        if (segments.isEmpty()) {
            segments.add(new Segment(font, ShapedRun.EMPTY, 0, 0, false));
        }
        return segments.toArray(Segment[]::new);
    }

    /// [#shapeSegments(Font, String, BidiLevels)] for a paragraph with no
    /// levels: the whole text in one direction.
    private static Segment[] shapeSegments(Font font, String text, @Nullable TextDirection direction) {
        var emoji = font.emoji();
        var fallbacks = font.fallbacks();
        var length = text.length();
        if (text.isEmpty() || (emoji == null && (fallbacks.isEmpty() || covers(font, text, 0, length)))) {
            return new Segment[] {new Segment(font, font.shape(text, direction), 0, 0, false)};
        }
        var pieces = emoji == null ? List.of(new TextRun(0, length, Slot.TEXT)) : Itemizer.runs(text);
        if (pieces.size() == 1
                && pieces.getFirst().slot() == Slot.TEXT
                && (fallbacks.isEmpty() || covers(font, text, 0, length))) {
            return new Segment[] {new Segment(font, font.shape(text, direction), 0, 0, false)};
        }

        var segments = new ArrayList<Segment>(pieces.size());
        var glyph = 0;
        for (var piece : pieces) {
            glyph = shapePiece(segments, font, text, piece, 0, glyph, direction);
        }
        return segments.toArray(Segment[]::new);
    }

    /// Shapes `[start, end)` of `text`, one level run, in `direction`: split
    /// into the faces that draw it as [#shapeSegments(Font, String, TextDirection)]
    /// splits a whole text, and appended. Answers the glyph count so far.
    private static int shapeRange(
            List<Segment> segments, Font font, String text, int start, int end, int glyph, TextDirection direction) {
        var emoji = font.emoji();
        var fallbacks = font.fallbacks();
        if (emoji == null && (fallbacks.isEmpty() || covers(font, text, start, end))) {
            return shapeInto(segments, font, text, start, end, glyph, direction);
        }
        var pieces = emoji == null
                ? List.of(new TextRun(0, end - start, Slot.TEXT))
                : Itemizer.runs(text.substring(start, end));
        for (var piece : pieces) {
            glyph = shapePiece(segments, font, text, piece, start, glyph, direction);
        }
        return glyph;
    }

    /// Shapes one itemized piece, whose offsets count from `base`, in the
    /// emoji face, the font, or the fallbacks that cover it.
    private static int shapePiece(
            List<Segment> segments,
            Font font,
            String text,
            TextRun piece,
            int base,
            int glyph,
            @Nullable TextDirection direction) {
        var start = base + piece.start();
        var end = base + piece.end();
        var emoji = font.emoji();
        var fallbacks = font.fallbacks();
        if (piece.slot() == Slot.EMOJI && emoji != null) {
            return shapeInto(segments, emoji, text, start, end, glyph, direction);
        }
        if (fallbacks.isEmpty() || covers(font, text, start, end)) {
            return shapeInto(segments, font, text, start, end, glyph, direction);
        }
        for (var run : Itemizer.byCoverage(text, start, end, font, new ByCoverage(fallbacks))) {
            glyph = shapeInto(segments, run.face(), text, run.start(), run.end(), glyph, direction);
        }
        return glyph;
    }

    /// Shapes `[start, end)` of `text` in `face`, appends it as a segment, and
    /// answers the glyph count so far.
    ///
    /// The run is shaped on its own, so a kern across the seam is lost. That seam
    /// is between a word and a picture, between two scripts, or between two
    /// directions, where there was never a kerning pair to lose.
    private static int shapeInto(
            List<Segment> segments,
            Font face,
            String text,
            int start,
            int end,
            int glyph,
            @Nullable TextDirection direction) {
        var shaped = face.shape(text.subSequence(start, end), direction);
        segments.add(new Segment(face, shaped, glyph, start, direction == TextDirection.RTL));
        return glyph + shaped.length();
    }

    /// Whether `font`'s face has every character of `[start, end)` that needs a
    /// glyph. A face whose `cmap` could not be read covers everything, so it
    /// draws what it drew before fallbacks existed.
    private static boolean covers(Font font, String text, int start, int end) {
        var coverage = font.face().coverage();
        return coverage.isEmpty() || coverage.coversAll(text, start, end);
    }

    /// The itemizer's question about faces, answered from the faces' coverage and
    /// the font's fallbacks.
    private record ByCoverage(Fallbacks fallbacks) implements FaceChoice<Font> {

        @Override
        public boolean covers(Font face, String text, int start, int end) {
            return Paragraph.covers(face, text, start, end);
        }

        @Override
        public @Nullable Font fallback(String text, int start, int end) {
            return fallbacks.fontFor(text, start, end);
        }
    }

    /// The font the character at `offset` was shaped in: the paragraph's own,
    /// the emoji face, a fallback, or a joined piece's.
    ///
    /// @throws IndexOutOfBoundsException if `offset` is not a character of the text
    Font shapedIn(int offset) {
        Objects.checkIndex(offset, text.length());
        var found = segments[0];
        for (var segment : segments) {
            if (segment.textStart() > offset) {
                break;
            }
            found = segment;
        }
        return found.font();
    }

    /// The segments as one run, in `base`'s design units and the text's offsets,
    /// in logical order.
    ///
    /// Three corrections, all of them the reason this is not an array copy:
    ///
    /// - **The clusters are rebased.** Each face shaped a *substring*, so its
    ///   clusters count from that substring's start and every offset here counts
    ///   from the paragraph's.
    /// - **The advances are rescaled.** A design unit is a fraction of an em and
    ///   the fraction differs per face — Inter is 2048 to the em and Noto Color
    ///   Emoji is 1024 — so appending one face's numbers to another's would make
    ///   an emoji half as wide as it is. Every measurement in this class is a prefix sum
    ///   over this array, and a prefix sum needs one unit. A joined piece at
    ///   another size is rescaled by the ratio of the two sizes as well.
    /// - **A right-to-left segment is reversed**, from the visual order it was
    ///   shaped in into the logical order the prefix sums are taken in.
    ///
    /// The rounding is to the nearest design unit, which at 2048 to the em is a
    /// thousandth of a pixel at any size anybody reads text at.
    ///
    /// **The glyph ids in the result belong to two different faces.** Nothing may
    /// draw from it directly, which is why drawing goes through [#segments].
    private static ShapedRun concatenate(Font base, Segment[] segments) {
        var total = 0;
        for (var segment : segments) {
            total += segment.run().length();
        }
        var glyphIds = new int[total];
        var clusters = new int[total];
        var xAdvances = new int[total];
        var yAdvances = new int[total];
        var xOffsets = new int[total];
        var yOffsets = new int[total];

        var at = 0;
        for (var segment : segments) {
            var piece = segment.run();
            var scale = (double) base.unitsPerEm() / segment.font().unitsPerEm();
            if (segment.font().size() != base.size()) {
                // A joined piece at another size: a design unit is a fraction of
                // an em, and an em is the size. Never true of an emoji face, which
                // is opened at its text face's size, so a paragraph that was not
                // joined is measured exactly as before.
                scale = scale * segment.font().size() / base.size();
            }
            var count = piece.length();
            for (var k = 0; k < count; k++, at++) {
                // A right-to-left segment is in visual order; reversed, its
                // clusters ascend as every measurement here needs.
                var i = segment.rtl() ? count - 1 - k : k;
                glyphIds[at] = piece.glyphId(i);
                clusters[at] = piece.cluster(i) + segment.textStart();
                xAdvances[at] = rescaled(piece.xAdvance(i), scale);
                yAdvances[at] = rescaled(piece.yAdvance(i), scale);
                xOffsets[at] = rescaled(piece.xOffset(i), scale);
                yOffsets[at] = rescaled(piece.yOffset(i), scale);
            }
        }
        return new ShapedRun(glyphIds, clusters, xAdvances, yAdvances, xOffsets, yOffsets);
    }

    private static int rescaled(int designUnits, double scale) {
        return scale == 1.0 ? designUnits : (int) Math.round(designUnits * scale);
    }

    /// The advance before each glyph, in the base font's design units.
    private static int[] glyphPrefix(ShapedRun run) {
        var prefix = new int[run.length() + 1];
        for (var i = 0; i < run.length(); i++) {
            prefix[i + 1] = prefix[i] + run.xAdvance(i);
        }
        return prefix;
    }

    /// Shapes `text` with `font`, ready to be wrapped.
    ///
    /// Each paragraph of it, each hard line, takes the direction of its first
    /// strong character, left to right when it has none: [Directionality#AUTO].
    /// A label of Arabic drawn with no stylesheet then reads from the right.
    public static Paragraph of(Font font, String text) {
        return of(font, text, 0, 0, Directionality.AUTO);
    }

    /// Shapes `text` with `font`, with `letterSpacing` added after every
    /// character and `wordSpacing` to every space, in logical pixels:
    /// `letter-spacing` and `word-spacing`.
    ///
    /// The spacing is part of every advance, so the paragraph wraps, measures
    /// and is hit-tested with it, as it is drawn. The base direction is the
    /// first strong character's, as [#of(Font, String)] has it.
    public static Paragraph of(Font font, String text, double letterSpacing, double wordSpacing) {
        return of(font, text, letterSpacing, wordSpacing, Directionality.AUTO);
    }

    /// Shapes `text` with `font` and the spacing, its base direction from
    /// `directionality`: what a box's `direction` and `unicode-bidi` say.
    ///
    /// ```java
    /// var label = Paragraph.of(font, "שלום, world", 0, 0, Directionality.RTL);
    /// ```
    public static Paragraph of(
            Font font, String text, double letterSpacing, double wordSpacing, Directionality directionality) {
        Objects.requireNonNull(font, "font");
        if (!Double.isFinite(letterSpacing) || !Double.isFinite(wordSpacing)) {
            throw new IllegalArgumentException(
                    "a spacing is a finite length, not " + letterSpacing + " or " + wordSpacing);
        }
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(directionality, "directionality");
        return new Paragraph(font, text, directionality, letterSpacing, wordSpacing);
    }

    /// `spans`, end to end, as one paragraph that wraps as one text.
    ///
    /// ```java
    /// var sentence = Paragraph.join(List.of(
    ///         Paragraph.of(regular, "Give it "),
    ///         Paragraph.of(bold, "Bleeding"),
    ///         Paragraph.of(regular, " equal to the boost it lost.")));
    /// ```
    ///
    /// **Nothing is shaped again.** Each span keeps the shaping it was made with,
    /// its emoji in the emoji face included, and the joined paragraph measures and
    /// draws those glyphs where they are. A kern across a seam is lost, which is
    /// the seam between two styles, where a reader expects none. The line breaker
    /// runs over the whole text, so a line can end inside one span and the next
    /// start in the middle of it, and a line is as tall as the tallest font on it:
    /// the deepest ascent above its baseline and the deepest descent below.
    ///
    /// Measurements are in the first span's font, which is what [#font()] then
    /// answers; a span in another face or at another size is rescaled into it.
    /// Spans that are themselves joined are flattened. A list of one span answers
    /// that span itself, so a styled paragraph with one style is the plain one.
    ///
    /// The joined text is one paragraph to the bidirectional algorithm, under
    /// the first span's [Directionality]: a bold Arabic word inside an Arabic
    /// sentence is placed among its neighbours as one text. Each span keeps the
    /// directions it was shaped in.
    ///
    /// A widget joins through `Paints.Context.join`, which keeps the result for
    /// as long as the spans are the same instances, for the reason
    /// [ParagraphCache] keeps a shaping: the render tree keeps a measure callback
    /// bound for as long as its paragraph is the same instance.
    ///
    /// @throws IllegalArgumentException if `spans` is empty: there is no font to
    ///         measure nothing in
    public static Paragraph join(List<Paragraph> spans) {
        Objects.requireNonNull(spans, "spans");
        if (spans.isEmpty()) {
            throw new IllegalArgumentException("a paragraph joins one span or more; an empty one is"
                    + " Paragraph.of(font, \"\"), in the font it would be measured in");
        }
        if (spans.size() == 1) {
            return Objects.requireNonNull(spans.getFirst(), "span");
        }

        var text = new StringBuilder();
        var segments = new ArrayList<Segment>();
        var starts = new ArrayList<Integer>();
        var fonts = new ArrayList<Font>();
        var glyph = 0;
        for (var span : spans) {
            Objects.requireNonNull(span, "span");
            var offset = text.length();
            for (var segment : span.segments) {
                segments.add(new Segment(
                        segment.font(),
                        segment.run(),
                        glyph + segment.glyphStart(),
                        offset + segment.textStart(),
                        segment.rtl()));
            }
            for (var k = 0; k < span.spanCount(); k++) {
                starts.add(offset + span.spanStart(k));
                fonts.add(span.spanFont(k));
            }
            glyph += span.run.length();
            text.append(span.text);
        }
        starts.add(text.length());

        var base = spans.getFirst().font;
        var joined = text.toString();
        var directionality = spans.getFirst().directionality;
        var levels = BidiLevels.of(joined, directionality);
        return new Paragraph(
                base,
                joined,
                directionality,
                levels.isLeftToRight() ? null : levels,
                segments.toArray(Segment[]::new),
                starts.stream().mapToInt(Integer::intValue).toArray(),
                fonts.toArray(Font[]::new));
    }

    /// How many pieces this paragraph was joined from: 1 for one that was not.
    private int spanCount() {
        return spanFonts == null ? 1 : spanFonts.length;
    }

    /// Where piece `k` starts in the text.
    private int spanStart(int k) {
        return spanStarts == null ? 0 : spanStarts[k];
    }

    /// The font piece `k` was shaped in.
    private Font spanFont(int k) {
        return spanFonts == null ? font : spanFonts[k];
    }

    /// The font the character at `offset` was shaped in, or the last piece's
    /// at the end of the text. The emoji face and a fallback are not pieces: a
    /// picture or a borrowed glyph is measured and ruled in the font of the text
    /// around it.
    private Font fontAt(int offset) {
        var starts = spanStarts;
        var fonts = spanFonts;
        if (starts == null || fonts == null) {
            return font;
        }
        for (var k = 0; k < fonts.length; k++) {
            if (starts[k] <= offset && offset < starts[k + 1]) {
                return fonts[k];
            }
        }
        return fonts[fonts.length - 1];
    }

    /// The first offset after `offset` where another piece starts, or the end of
    /// the text.
    private int nextSpanStart(int offset) {
        var starts = spanStarts;
        if (starts != null) {
            for (var start : starts) {
                if (start > offset) {
                    return start;
                }
            }
        }
        return text.length();
    }

    /// How far `line`'s baseline is below its top: the font's ascent, or for a
    /// joined paragraph the largest ascent of the fonts the line holds.
    ///
    /// A blank line takes the font of the piece it stands in.
    public double ascentOf(TextLine line) {
        Objects.requireNonNull(line, "line");
        return uniform ? font.ascent() : lineMetric(line, spanAscents);
    }

    /// How tall `line` is: the font's line height, or for a joined paragraph the
    /// largest ascent on the line plus the largest share below the baseline, so
    /// that a large word neither overlaps the line above it nor the one below.
    ///
    /// The sum of these over a layout's lines is its [TextLayout#height()].
    public double heightOf(TextLine line) {
        Objects.requireNonNull(line, "line");
        return uniform ? font.lineHeight() : lineMetric(line, spanAscents) + lineMetric(line, spanBelows);
    }

    /// The largest of `metric` over the pieces `line` holds text from.
    private double lineMetric(TextLine line, double @Nullable [] metric) {
        var starts = spanStarts;
        if (starts == null || metric == null) {
            throw new IllegalStateException("only a paragraph joined from several fonts has per-piece metrics");
        }
        var largest = 0.0;
        var found = false;
        for (var k = 0; k < metric.length; k++) {
            var start = starts[k];
            var end = starts[k + 1];
            if (start == end) {
                continue;
            }
            var holds = line.start() == line.end()
                    ? start <= line.start() && line.start() < end
                    : start < line.end() && end > line.start();
            if (holds) {
                largest = found ? Math.max(largest, metric[k]) : metric[k];
                found = true;
            }
        }
        if (found) {
            return largest;
        }
        // A blank line at the very end of the text, after its last newline.
        var last = fontAt(text.length());
        return metric == spanAscents ? last.ascent() : last.lineHeight() - last.ascent();
    }

    /// What this paragraph's base direction came from.
    public Directionality directionality() {
        return directionality;
    }

    /// The base direction of the paragraph, the hard line, `offset` is in:
    /// which side its lines start at.
    ///
    /// @throws IndexOutOfBoundsException if `offset` is outside `[0, length]`
    public Direction directionAt(int offset) {
        Objects.checkIndex(offset, text.length() + 1);
        var levels = bidi;
        return levels == null ? Direction.LTR : levels.directionAt(offset);
    }

    /// Whether any character runs right to left or the base direction is
    /// right to left anywhere: whether lines are placed in an order other
    /// than the text's.
    public boolean isBidirectional() {
        return bidi != null;
    }

    /// How far in from the box's left edge `line` starts when it is drawn under
    /// `align` in a box `available` wide.
    ///
    /// `start` and `end` are the sides of the line's own paragraph, which under
    /// `unicode-bidi: plaintext` is each hard line's. A justified line that is
    /// placed rather than respaced, the last of its paragraph, starts at the
    /// start. This is the indent the paint uses, so a caret built on it sits
    /// on the glyphs.
    public double indentOf(TextLine line, TextAlign align, double available) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(align, "align");
        return placed(align, line).indentOf(line.width(), available);
    }

    /// `align` as the side `line` is placed at.
    private TextAlign placed(TextAlign align, TextLine line) {
        return align.placing(directionAt(line.start()));
    }

    /// Breaks the text into lines that fit in `maxWidth` logical units, between
    /// words only.
    ///
    /// Greedy, which is what every browser does: each line takes as much as fits
    /// and no more. A word longer than the whole width is **not** broken here — it
    /// overflows on a line of its own, because mid-word breaking is a decision a
    /// style makes rather than a layout engine. [#layout(double, TextFlow)] is the
    /// form that takes the style's answer.
    ///
    /// **Always a layout, and always at least one line.** Text with nothing in
    /// it breaks into a single empty line rather than into none: a blank line
    /// takes a line's height, and a caret in an empty editor still has to be
    /// somewhere. Callers therefore need no null branch — the one failure this
    /// has is a NaN width, which throws, because a NaN would wrap every line to
    /// nothing and report it as a successful layout.
    ///
    /// @param maxWidth the width to fit in, or [#UNCONSTRAINED] for one line per
    ///                 explicit newline and no wrapping at all
    public TextLayout layout(double maxWidth) {
        return layout(maxWidth, NORMAL_BREAKING);
    }

    /// The same, breaking inside words where `flow` allows it.
    ///
    /// `overflow-wrap: anywhere` breaks a word only when it is wider than the
    /// whole line, between two grapheme clusters, as late as the line allows.
    /// `word-break: break-all` may break between any two grapheme clusters, so
    /// every line is filled to the edge. Either way a line always takes at least
    /// one grapheme, so a box narrower than one character still makes progress.
    ///
    /// Only the breaking half of `flow` is read here. Whether the paragraph wraps
    /// at all is the caller's question, answered by the width it passes.
    ///
    /// @param maxWidth the width to fit in, or [#UNCONSTRAINED]
    /// @param flow     what the cascade said about breaking inside a word
    public TextLayout layout(double maxWidth, TextFlow flow) {
        return layout(maxWidth, breaking(Objects.requireNonNull(flow, "flow")));
    }

    /// Breaking between words only.
    private static final int NORMAL_BREAKING = 0;

    /// Breaking a word that does not fit on a line of its own.
    private static final int OVERFLOW_BREAKING = 1;

    /// Breaking between any two grapheme clusters.
    private static final int ANYWHERE_BREAKING = 2;

    /// Which of the three ways of breaking `flow` asks for. `break-all` wins
    /// over `overflow-wrap`, because every place the second may break the first
    /// may break too.
    private static int breaking(TextFlow flow) {
        if (flow.wordBreak() == WordBreak.BREAK_ALL) {
            return ANYWHERE_BREAKING;
        }
        return flow.overflowWrap() == OverflowWrap.ANYWHERE ? OVERFLOW_BREAKING : NORMAL_BREAKING;
    }

    private TextLayout layout(double maxWidth, int breaking) {
        if (Double.isNaN(maxWidth)) {
            throw new IllegalArgumentException(
                    "a NaN width would wrap every line to nothing; pass Paragraph.UNCONSTRAINED"
                            + " for no constraint");
        }
        // NaN never equals itself, so the first call always misses.
        if (maxWidth == memoWidth && breaking == memoBreaking) {
            return Objects.requireNonNull(memo, "a width is remembered only together with its layout");
        }

        var lines = new ArrayList<TextLine>();
        // Hard breaks first: BreakIterator offers a break after a newline but
        // does not say it is mandatory, and a paragraph that silently joined its
        // own lines would be wrong in a way only long text reveals.
        var paragraphStart = 0;
        while (paragraphStart <= text.length()) {
            var newline = text.indexOf('\n', paragraphStart);
            var paragraphEnd = newline < 0 ? text.length() : newline;
            wrap(paragraphStart, paragraphEnd, maxWidth, breaking, lines);
            if (newline < 0) {
                break;
            }
            paragraphStart = newline + 1;
        }

        var widest = 0.0;
        for (var line : lines) {
            widest = Math.max(widest, line.width());
        }

        var height = 0.0;
        if (uniform) {
            height = lines.size() * font.lineHeight();
        } else {
            for (var line : lines) {
                height += heightOf(line);
            }
        }
        var layout = new TextLayout(lines, widest, height);
        memoWidth = maxWidth;
        memoBreaking = breaking;
        memo = layout;
        return layout;
    }

    /// A [Measure] that reports this paragraph's size to the layout engine.
    ///
    /// Attach it to a leaf node and the flexbox algorithm treats the text as
    /// content: it proposes a width, this wraps at that width, and the height
    /// that comes back is what the row or column is sized around.
    ///
    /// The width reported back is the widest line, except under
    /// [MeasureMode#EXACTLY] where the parent has already decided. Reporting the
    /// *available* width instead would make every paragraph claim the full
    /// column even when it wrapped well short of it, and a centred parent would
    /// then centre empty space.
    public Measure measureFunction() {
        return measureFunction(TextFlow.NORMAL);
    }

    /// The same, told what the box's `white-space` resolved to.
    ///
    /// Under [dev.goldberry.text.flow.WhiteSpace#NOWRAP] the
    /// width the layout engine offers is ignored: the paragraph reports the
    /// width it actually wants, and the box is then free to be laid out narrower
    /// than its own content. That is what makes a cut label possible at all. A
    /// box with text is a measured leaf, so narrowing it re-measures the
    /// paragraph, and a paragraph that answered "as wide as I was offered" could
    /// never overflow anything.
    ///
    /// `text-overflow` is deliberately not read here. An ellipsised line is drawn
    /// short and measured long, because measuring the truncation would let the
    /// ellipsis decide the width that caused it.
    ///
    /// [MeasureMode#EXACTLY] still wins under either value, because a parent that
    /// has already decided a width is not asking.
    public Measure measureFunction(TextFlow flow) {
        Objects.requireNonNull(flow, "flow");
        var wraps = flow.wraps();
        return (width, widthMode, height, heightMode) -> {
            var available = switch (widthMode) {
                // Yoga passes NaN with UNDEFINED, so `width` must not be read.
                case UNDEFINED -> UNCONSTRAINED;
                case EXACTLY, AT_MOST -> wraps ? (double) width : UNCONSTRAINED;
            };
            var layout = layout(available, flow);
            var measured = widthMode == MeasureMode.EXACTLY ? width : (float) layout.width();
            return new MeasuredSize(measured, (float) keptHeight(layout, flow));
        };
    }

    /// How many of `layout`'s lines `flow` keeps: all of them, or its
    /// `line-clamp`.
    private static int kept(TextLayout layout, TextFlow flow) {
        var lines = layout.lines().size();
        return flow.clamps() ? Math.min(flow.maxLines(), lines) : lines;
    }

    /// The height of the lines `flow` keeps of `layout`: what a clamped
    /// paragraph is measured at, so the box ends under its last kept line.
    public double keptHeight(TextLayout layout, TextFlow flow) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(flow, "flow");
        var kept = kept(layout, flow);
        if (kept == layout.lines().size()) {
            return layout.height();
        }
        if (uniform) {
            return kept * font.lineHeight();
        }
        var height = 0.0;
        for (var i = 0; i < kept; i++) {
            height += heightOf(layout.lines().get(i));
        }
        return height;
    }

    /// Draws the paragraph with its first line's **top** at `(x, top)`.
    ///
    /// The top, not the baseline — a paragraph is placed against a box, and a box
    /// has a top. Each line's baseline is derived from it by the font's ascent,
    /// which is the one place that conversion belongs.
    ///
    /// @param maxWidth the width to wrap at; pass what layout gave the box
    /// @param argb     a colour as `0xAARRGGBB`, not premultiplied
    public void paint(Frame frame, double x, double top, double maxWidth, int argb) {
        paint(frame, x, top, maxWidth, argb, TextFlow.NORMAL);
    }

    /// The same, told what the box's `white-space` and `text-overflow` resolved
    /// to.
    ///
    /// Three drawings, from one pair of keywords:
    ///
    /// - **wrapping** — the paragraph is laid out at `maxWidth` and every line is
    ///   drawn. What every box did before either property existed.
    /// - **`nowrap`** — laid out unconstrained and drawn at its natural width,
    ///   past `maxWidth` where it is longer. Whether that overhang is visible is
    ///   an ancestor's `overflow` to decide, and this method neither knows nor
    ///   needs to: the clip is already on the context when it is called.
    /// - **`nowrap` with `ellipsis`** — the same, except that a line wider than
    ///   `maxWidth` is drawn up to the last grapheme that leaves room for
    ///   [TextOverflow#MARK], and the mark is drawn after it.
    ///
    /// The ellipsis is applied **per line** rather than to the last line of the
    /// box, which is where CSS puts it. Every consumer in the catalog is a
    /// single-line label, so the two agree wherever it is used today, and per-line
    /// is the reading that stays true of a `nowrap` paragraph with hard newlines
    /// in it — where CSS would leave every line but the last running off the edge.
    ///
    /// @param maxWidth the width to wrap at, or to truncate at; pass what layout
    ///                 gave the box
    /// @param argb     a colour as `0xAARRGGBB`, not premultiplied
    /// @param flow     what the cascade said about breaking and marking
    public void paint(Frame frame, double x, double top, double maxWidth, int argb, TextFlow flow) {
        paint(frame, x, top, maxWidth, argb, flow, List.of());
    }

    /// The same, with stretches of the text in colours and rules of their own.
    ///
    /// Each [SpanPaint] replaces `argb` and `flow`'s decorations over its range,
    /// and the first one that covers an offset wins. Text outside every range is
    /// drawn as the forms without spans draw it. A line is drawn piece by piece,
    /// split wherever a span or a joined piece begins or ends, and each piece's
    /// rules sit where the font it was shaped in puts them.
    ///
    /// With no spans, a paragraph that was not joined from several fonts is
    /// drawn by exactly the path the other forms take, so a plain paragraph
    /// passed through here draws the same pixels.
    ///
    /// @param spans the stretches drawn differently, in any order; their offsets
    ///              index [#text()]
    public void paint(
            Frame frame, double x, double top, double maxWidth, int argb, TextFlow flow, List<SpanPaint> spans) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(flow, "flow");
        Objects.requireNonNull(spans, "spans");
        if (uniform && spans.isEmpty()) {
            paintPlain(frame, x, top, maxWidth, argb, flow);
        } else {
            paintStyled(frame, x, top, maxWidth, argb, flow, spans);
        }
    }

    /// One font and one colour: how every paragraph was drawn before it could be
    /// styled, kept as it was so that none of them moves by a pixel.
    private void paintPlain(Frame frame, double x, double top, double maxWidth, int argb, TextFlow flow) {
        var layout = layout(flow.wraps() ? maxWidth : UNCONSTRAINED, flow);
        var lineHeight = font.lineHeight();
        var ascent = font.ascent();
        var ellipsis = flow.ellipsises();
        var align = flow.textAlign();
        // Read once per paint rather than once per line: it is a downcall into the
        // rasterizer, and it is the same answer for every line of one font. Null
        // when nothing is decorated, which is nearly every paragraph.
        var rules = flow.isDecorated() ? font.decorations().orElse(font.size(), ascent) : null;

        var kept = kept(layout, flow);
        for (var i = 0; i < kept; i++) {
            var line = layout.lines().get(i);
            var baseline = top + ascent + i * lineHeight;
            if (i == kept - 1 && kept < layout.lines().size()) {
                // The last line `line-clamp` keeps, marked because more were cut,
                // whether or not it was full.
                paintTruncated(frame, x, baseline, maxWidth, argb, line);
                decorate(frame, x, baseline, maxWidth, argb, flow, rules);
                continue;
            }
            if (line.isEmpty()) {
                // And therefore undecorated: a blank line between two paragraphs is
                // not a rule a reader can explain.
                continue;
            }
            if (!ellipsis || line.width() <= maxWidth) {
                var room = gapRoom(line, align, maxWidth);
                if (room > 0) {
                    eachFragment(
                            line.start(),
                            visibleEnd(line),
                            x,
                            room,
                            (from, to, pen, rtl) ->
                                    drawGlyphs(frame, pen, baseline, glyphBefore[from], glyphBefore[to], argb));
                    decorate(frame, x, baseline, maxWidth, argb, flow, rules);
                    continue;
                }
                var indent = indentOf(line, align, maxWidth);
                eachFragment(
                        line.start(),
                        visibleEnd(line),
                        x + indent,
                        0,
                        (from, to, pen, rtl) ->
                                drawGlyphs(frame, pen, baseline, glyphBefore[from], glyphBefore[to], argb));
                decorate(frame, x + indent, baseline, line.width(), argb, flow, rules);
                continue;
            }
            // A truncated line fills the box by construction, so there is no
            // slack to share and `text-align` has nothing to say about it.
            paintTruncated(frame, x, baseline, maxWidth, argb, line);
            // The rule runs under the ellipsis too, because the mark is part of the
            // line — a decorated label whose rule stopped short of its own `…`
            // would read as two words, one of them underlined.
            decorate(frame, x, baseline, maxWidth, argb, flow, rules);
        }
    }

    /// Draws glyphs `[from, to)` of the paragraph, each in the face that shaped
    /// it, with `(x, baseline)` on the baseline at the range's left.
    ///
    /// The range is logical and runs one way, which a fragment from
    /// [#eachFragment] does. One call for the paragraph that took one face. For
    /// the one that took two, a call per segment the range touches, each with
    /// its pen moved along by the advances of everything left of it in the
    /// range — which is why [#advanceBeforeGlyph] exists at all. A right-to-left
    /// segment's glyphs for a logical range are one stretch of its own visual
    /// run, counted from its far end.
    private void drawGlyphs(Frame frame, double x, double baseline, int from, int to, int argb) {
        if (segments.length == 1) {
            var only = segments[0];
            var count = only.run().length();
            if (only.rtl()) {
                only.font().draw(frame, x, baseline, only.run(), count - to, count - from, argb);
            } else {
                only.font().draw(frame, x, baseline, only.run(), from, to, argb);
            }
            return;
        }
        for (var segment : segments) {
            var start = Math.max(from, segment.glyphStart());
            var end = Math.min(to, segment.glyphStart() + segment.run().length());
            if (start >= end) {
                continue;
            }
            var count = segment.run().length();
            var local = start - segment.glyphStart();
            var localEnd = end - segment.glyphStart();
            if (segment.rtl()) {
                var pen = x + font.toLogical(advanceBeforeGlyph[to] - advanceBeforeGlyph[end]);
                segment.font().draw(frame, pen, baseline, segment.run(), count - localEnd, count - local, argb);
            } else {
                var pen = x + font.toLogical(advanceBeforeGlyph[start] - advanceBeforeGlyph[from]);
                segment.font().draw(frame, pen, baseline, segment.run(), local, localEnd, argb);
            }
        }
    }

    /// Draws every line with its own height, each piece in its own colour.
    ///
    /// A uniform paragraph's lines are still one line height apart, counted by
    /// multiplying as [#paintPlain] does, so a paragraph in one font with a
    /// coloured word puts its baselines where the uncoloured one does.
    private void paintStyled(
            Frame frame, double x, double top, double maxWidth, int argb, TextFlow flow, List<SpanPaint> spans) {
        var layout = layout(flow.wraps() ? maxWidth : UNCONSTRAINED, flow);
        var ellipsis = flow.ellipsises();
        var align = flow.textAlign();
        var lineTop = 0.0;
        var kept = kept(layout, flow);
        for (var i = 0; i < kept; i++) {
            var line = layout.lines().get(i);
            var offsetDown = uniform ? i * font.lineHeight() : lineTop;
            lineTop += heightOf(line);
            var baseline = top + ascentOf(line) + offsetDown;
            if (i == kept - 1 && kept < layout.lines().size()) {
                paintTruncatedStyled(frame, x, baseline, maxWidth, argb, flow, spans, line);
                continue;
            }
            if (line.isEmpty()) {
                continue;
            }
            if (!ellipsis || line.width() <= maxWidth) {
                var room = gapRoom(line, align, maxWidth);
                var indent = room > 0 ? 0 : indentOf(line, align, maxWidth);
                eachFragment(
                        line.start(),
                        visibleEnd(line),
                        x + indent,
                        room,
                        (from, to, pen, rtl) -> drawPieces(frame, pen, baseline, from, to, rtl, argb, flow, spans));
                continue;
            }
            paintTruncatedStyled(frame, x, baseline, maxWidth, argb, flow, spans, line);
        }
    }

    /// Draws `[from, to)`, a fragment that runs one way, as pieces that each
    /// have one colour, one set of rules and one font, the pen at `x` for the
    /// fragment's left edge: its first character's when it runs left to right,
    /// its last's when it runs right to left.
    private void drawPieces(
            Frame frame,
            double x,
            double baseline,
            int from,
            int to,
            boolean rtl,
            int argb,
            TextFlow flow,
            List<SpanPaint> spans) {
        var at = from;
        while (at < to) {
            var paint = paintAt(spans, at);
            var next = Math.min(to, nextSpanStart(at));
            next = Math.min(next, paint != null ? paint.end() : nextPaintStart(spans, at));
            var colour = paint != null ? paint.argb() : argb;
            var pen = rtl ? x + widthOf(next, to) : x + widthOf(from, at);
            drawGlyphs(frame, pen, baseline, glyphBefore[at], glyphBefore[next], colour);
            var decorations = paint != null ? paint.decorations() : flow.decorations();
            rule(frame, pen, baseline, widthOf(at, next), colour, decorations, fontAt(at));
            at = next;
        }
    }

    /// [#paintTruncated] for a styled line: as much as fits, then the mark in
    /// the colour, the font and the rules of the last character drawn.
    private void paintTruncatedStyled(
            Frame frame,
            double x,
            double baseline,
            double maxWidth,
            int argb,
            TextFlow flow,
            List<SpanPaint> spans,
            TextLine line) {
        var room = maxWidth - font.ellipsisWidth();
        var cut = room > 0 ? offsetFitting(line.start(), line.end(), room) : line.start();
        while (cut > line.start() && Character.isWhitespace(text.charAt(cut - 1))) {
            cut--;
        }
        var kept = widthOf(line.start(), cut);
        var rtl = endsLeft(line, maxWidth);
        var left = rtl ? x + maxWidth - kept : x;
        eachFragment(
                line.start(),
                cut,
                left,
                0,
                (from, to, pen, runsLeft) -> drawPieces(frame, pen, baseline, from, to, runsLeft, argb, flow, spans));

        var last = cut > line.start() ? cut - 1 : line.start();
        var paint = paintAt(spans, last);
        var colour = paint != null ? paint.argb() : argb;
        var face = fontAt(last);
        var pen = rtl ? left - face.ellipsisWidth() : left + kept;
        face.draw(frame, pen, baseline, TextOverflow.MARK, colour);
        var decorations = paint != null ? paint.decorations() : flow.decorations();
        rule(frame, pen, baseline, face.ellipsisWidth(), colour, decorations, face);
    }

    /// The first span that covers `offset`, or null.
    private static @Nullable SpanPaint paintAt(List<SpanPaint> spans, int offset) {
        for (var span : spans) {
            if (span.covers(offset)) {
                return span;
            }
        }
        return null;
    }

    /// The first offset after `offset` where a span starts, or past the end.
    private static int nextPaintStart(List<SpanPaint> spans, int offset) {
        var next = Integer.MAX_VALUE;
        for (var span : spans) {
            if (span.start() > offset && span.start() < span.end()) {
                next = Math.min(next, span.start());
            }
        }
        return next;
    }

    /// One past the line's last character that is not trailing whitespace: the
    /// end of what is drawn, which is where the glyph range of a [TextLine]
    /// already stops.
    /// The room each word gap on `line` takes when `align` justifies it in a
    /// box `maxWidth` wide, or 0 when the line is placed rather than respaced:
    /// it is not justified, it is the last line of its paragraph (the last of
    /// the text, or one a newline ends), it has no gap, or it has no slack.
    private double gapRoom(TextLine line, TextAlign align, double maxWidth) {
        if (align != TextAlign.JUSTIFY || !Double.isFinite(maxWidth)) {
            return 0;
        }
        var end = line.end();
        if (end >= text.length() || text.charAt(end) == '\n') {
            return 0;
        }
        var slack = maxWidth - line.width();
        var gaps = gaps(line);
        return gaps == 0 || !(slack > 0) ? 0 : slack / gaps;
    }

    /// How many word gaps `line` has between its first word and its last
    /// visible character. Spaces before the first word are not gaps.
    private int gaps(TextLine line) {
        var count = 0;
        var seenWord = false;
        for (var i = line.start(); i < visibleEnd(line); i++) {
            var separator = isWordSeparator(text.charAt(i));
            if (separator && seenWord) {
                count++;
            }
            seenWord |= !separator;
        }
        return count;
    }

    /// Hands `draw` the stretches of `[from, to)`, part of one line, in the
    /// order they are placed from left to right, each with the pen at its left
    /// edge, starting at `x`, and `room` added at every word gap.
    ///
    /// A stretch runs one way: a level run of the line, cut after every word
    /// gap when `room` is not 0. A paragraph with no levels hands over the
    /// whole range at `x` when there is no room, which is the one call it
    /// always made.
    ///
    /// A gap is a word separator after the line's first word, as [#gaps]
    /// counts them, and its room goes on the side of it the line reads
    /// towards: after it in a left-to-right run, before it in a right-to-left
    /// one, so the room is always between the separator and the next word.
    private void eachFragment(int from, int to, double x, double room, Fragment draw) {
        if (from >= to) {
            return;
        }
        var firstWord = room == 0 ? to : firstWord(from, to);
        var levels = bidi;
        if (levels == null) {
            if (room == 0) {
                draw.at(from, to, x, false);
            } else {
                eachWord(from, to, firstWord, false, x, room, draw);
            }
            return;
        }
        var pen = x;
        for (var run : levels.visualRuns(from, to)) {
            if (room == 0) {
                draw.at(run.start(), run.end(), pen, run.isRightToLeft());
                pen += widthOf(run.start(), run.end());
                continue;
            }
            eachWord(run.start(), run.end(), firstWord, run.isRightToLeft(), pen, room, draw);
            pen += widthOf(run.start(), run.end()) + room * gapsIn(run.start(), run.end(), firstWord);
        }
    }

    /// One level run of a justified line, `[start, end)`, its left edge at
    /// `x`: each word with the gap after it, at its pen, past the words and
    /// gaps left of it — which in a right-to-left run are the ones after it in
    /// the text.
    private void eachWord(int start, int end, int firstWord, boolean rtl, double x, double room, Fragment draw) {
        var gaps = rtl ? gapsIn(start, end, firstWord) : 0;
        var gapsBefore = 0;
        var from = start;
        for (var i = start; i <= end; i++) {
            var cut = i == end ? from < end : isGap(i, firstWord);
            if (!cut) {
                continue;
            }
            var to = Math.min(i + 1, end);
            var pen = rtl
                    ? x + widthOf(to, end) + room * (gaps - gapsBefore)
                    : x + widthOf(start, from) + room * gapsBefore;
            draw.at(from, to, pen, rtl);
            gapsBefore++;
            from = to;
        }
    }

    /// The first character of `[from, to)` that is not a word separator, or
    /// `to`: the line's first word, before which a separator is no gap.
    private int firstWord(int from, int to) {
        var i = from;
        while (i < to && isWordSeparator(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /// Whether the character at `i` is a word gap: a separator after the
    /// line's first word.
    private boolean isGap(int i, int firstWord) {
        return i > firstWord && isWordSeparator(text.charAt(i));
    }

    /// How many word gaps `[start, end)` holds.
    private int gapsIn(int start, int end, int firstWord) {
        var count = 0;
        for (var i = start; i < end; i++) {
            if (isGap(i, firstWord)) {
                count++;
            }
        }
        return count;
    }

    /// One stretch of a line that runs one way: its text range, its pen at its
    /// left edge, and whether it runs right to left.
    @FunctionalInterface
    private interface Fragment {
        void at(int from, int to, double pen, boolean rtl);
    }

    private int visibleEnd(TextLine line) {
        return visibleEnd(line.start(), line.end());
    }

    /// Draws `decorations` along one piece, at the place and thickness `face`
    /// gives them. Nothing for no decorations, which asks the face nothing.
    private static void rule(
            Frame frame,
            double x,
            double baseline,
            double width,
            int argb,
            Set<TextDecoration> decorations,
            Font face) {
        if (decorations.isEmpty() || !(width > 0)) {
            return;
        }
        var rules = face.decorations().orElse(face.size(), face.ascent());
        if (decorations.contains(TextDecoration.UNDERLINE)) {
            fillRule(frame, x, baseline + rules.underlinePosition(), width, rules.underlineThickness(), argb);
        }
        if (decorations.contains(TextDecoration.LINE_THROUGH)) {
            fillRule(frame, x, baseline + rules.strikethroughPosition(), width, rules.strikethroughThickness(), argb);
        }
    }

    /// Draws the rules `flow` asks for along one line, `width` wide from `x`.
    ///
    /// In the text's own colour and at the face's own thickness. A decoration is
    /// part of the glyphs rather than a box behind them, which is why it takes
    /// `argb` and not a second colour, and why the position and the thickness
    /// come from [dev.goldberry.text.font.Font#decorations()] rather than from
    /// any arithmetic here.
    ///
    /// @param rules the face's metrics, already substituted for by
    ///              [dev.goldberry.text.font.Font.Decorations#orElse],
    ///              or null when there is nothing to draw
    private static void decorate(
            Frame frame,
            double x,
            double baseline,
            double width,
            int argb,
            TextFlow flow,
            Font.@Nullable Decorations rules) {

        if (rules == null || !(width > 0)) {
            return;
        }
        if (flow.has(TextDecoration.UNDERLINE)) {
            fillRule(frame, x, baseline + rules.underlinePosition(), width, rules.underlineThickness(), argb);
        }
        if (flow.has(TextDecoration.LINE_THROUGH)) {
            fillRule(frame, x, baseline + rules.strikethroughPosition(), width, rules.strikethroughThickness(), argb);
        }
    }

    private static void fillRule(Frame frame, double x, double top, double width, double thickness, int argb) {
        frame.fillRect((float) x, (float) top, (float) width, (float) thickness, argb);
    }

    /// Draws one over-long line as much of itself as fits, then the ellipsis.
    ///
    /// The mark is measured through [Font#ellipsisWidth()] rather than laid out
    /// as text, because it is not part of this paragraph: it belongs to the box
    /// the paragraph did not fit in. Room for it is taken off the top, so the
    /// mark always lands inside `maxWidth` — an ellipsis that itself overflowed
    /// would say "there is more" by hanging off the edge, which is the thing it
    /// exists to stop.
    ///
    /// **A line with no room even for the mark still draws the mark.** The
    /// alternative is a cell that goes blank as it narrows, which reads as a
    /// missing value rather than as a truncated one.
    ///
    /// What is kept is the start of the text, in logical order, placed as a
    /// line is. In a right-to-left paragraph it sits at the right of the box
    /// and the mark at its left, where the text would have gone on.
    private void paintTruncated(Frame frame, double x, double baseline, double maxWidth, int argb, TextLine line) {
        var mark = font.ellipsisWidth();
        var room = maxWidth - mark;
        var cut = room > 0 ? offsetFitting(line.start(), line.end(), room) : line.start();

        // Trailing whitespace before the mark, so a cut at a word boundary reads
        // as `Save as…` rather than `Save as …`. The glyph range already drops
        // it from the *width*; what it does not do is stop the pen advancing
        // over it, which is what would put the gap in.
        while (cut > line.start() && Character.isWhitespace(text.charAt(cut - 1))) {
            cut--;
        }

        var kept = widthOf(line.start(), cut);
        var rtl = endsLeft(line, maxWidth);
        var left = rtl ? x + maxWidth - kept : x;
        eachFragment(
                line.start(),
                cut,
                left,
                0,
                (from, to, pen, runsLeft) ->
                        drawGlyphs(frame, pen, baseline, glyphBefore[from], glyphBefore[to], argb));
        font.draw(frame, rtl ? left - mark : left + kept, baseline, TextOverflow.MARK, argb);
    }

    /// Whether a cut `line` ends at its left: its paragraph runs right to left
    /// and the box has a right edge to start from.
    private boolean endsLeft(TextLine line, double maxWidth) {
        return bidi != null && Double.isFinite(maxWidth) && directionAt(line.start()) == Direction.RTL;
    }

    /// The last grapheme boundary in `[lineStart, lineEnd]` whose prefix is no
    /// wider than `width`.
    ///
    /// [#offsetAt]'s sibling and its opposite: that one rounds to the *nearest*
    /// caret position, which is what a click means, and this one never rounds up,
    /// which is what "as much as fits" means. A truncation that rounded to the
    /// nearest would return half a character more than it had room for on every
    /// second label.
    ///
    /// Steps by grapheme cluster for [#offsetAt]'s reason — a cut between the two
    /// halves of a surrogate pair, or between a letter and the accent over it, is
    /// not a place text can end.
    ///
    /// @param lineStart the first offset of the line, from [TextLine#start()]
    /// @param lineEnd   one past its last, from [TextLine#end()]
    /// @param width     the room available, in logical units
    /// @return an offset in `[lineStart, lineEnd]`; `lineStart` when not even one
    ///         grapheme fits
    /// @throws IndexOutOfBoundsException if either offset is outside the text
    /// @throws IllegalArgumentException  if `lineEnd` is before `lineStart`
    public int offsetFitting(int lineStart, int lineEnd, double width) {
        Objects.checkIndex(lineStart, text.length() + 1);
        Objects.checkIndex(lineEnd, text.length() + 1);
        if (lineEnd < lineStart) {
            throw new IllegalArgumentException("a line cannot end before it starts: " + lineStart + ".." + lineEnd);
        }
        if (lineStart == lineEnd || !(width > 0)) {
            // NaN lands here too, and "no room" is the honest answer to an
            // unknown width — the caller draws the mark alone.
            return lineStart;
        }

        var graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);

        var fitting = lineStart;
        for (var offset = graphemes.following(lineStart);
                offset != BreakIterator.DONE && offset <= lineEnd;
                offset = graphemes.next()) {

            if (widthOf(lineStart, offset) > width) {
                // Advances are non-negative, so once a prefix is too wide every
                // longer one is too. Stopping here is what keeps truncating a
                // short label out of a long paragraph from walking the paragraph.
                break;
            }
            fitting = offset;
        }
        return fitting;
    }

    /// The font this paragraph was shaped with, and is measured in.
    ///
    /// For a joined paragraph, the first span's: every width is in its design
    /// units, and the other spans were rescaled into them.
    public Font font() {
        return font;
    }

    /// The text, unchanged.
    public String text() {
        return text;
    }

    /// The whole paragraph as one shaped run, in the base font's design units.
    ///
    /// A [TextLine]'s glyph range indexes into this.
    ///
    /// Its glyph ids may not all belong to [#font()]. A paragraph with emoji or
    /// fallback text in it was shaped by several faces, and this is them
    /// concatenated: the advances, offsets and clusters are all in one
    /// coordinate system and are what every measurement here is built on, but a
    /// glyph id is only meaningful to the face that produced it. Drawing from
    /// this directly would draw another face's glyph numbers out of the prose
    /// face; [#paint] is what knows which is which.
    public ShapedRun glyphs() {
        return run;
    }

    // --- wrapping -------------------------------------------------------------

    /// Breaks `[start, end)` — one hard line — into as many soft lines as it
    /// takes, appending each.
    private void wrap(int start, int end, double maxWidth, int breaking, List<TextLine> lines) {
        if (start == end) {
            // A blank line. It draws nothing and still takes a line's height,
            // which is what a reader means by a blank line.
            lines.add(new TextLine(start, end, glyphBefore[start], glyphBefore[start], 0));
            return;
        }

        // Under `break-all` every grapheme boundary is a place to break, which
        // is a superset of the line breaker's opportunities: a space is a
        // grapheme too.
        var breaks =
                breaking == ANYWHERE_BREAKING ? BreakIterator.getCharacterInstance() : BreakIterator.getLineInstance();
        breaks.setText(text.substring(start, end));

        var lineStart = start;
        // The furthest break that still fits on the line being built. Below
        // `lineStart` means "nothing yet", which is the case that decides
        // whether an over-long word overflows or is dropped.
        var lastFitting = -1;

        // `first()` is always offset zero, which is the line start rather than a
        // place to break, so the walk begins at the one after it.
        breaks.first();
        for (var candidate = breaks.next(); candidate != BreakIterator.DONE; candidate = breaks.next()) {

            var offset = start + candidate;
            if (widthOf(lineStart, offset) <= maxWidth) {
                lastFitting = offset;
                continue;
            }

            if (lastFitting > lineStart) {
                // Break at the last place that fitted, then reconsider this
                // candidate against the new line -- it is the next line's
                // content, not something to skip.
                lines.add(lineFor(lineStart, lastFitting));
                lineStart = lastFitting;
                lastFitting = -1;

                if (widthOf(lineStart, offset) <= maxWidth) {
                    lastFitting = offset;
                    continue;
                }
            }

            // A single unbreakable chunk wider than the whole line. Unless the
            // style said `overflow-wrap: anywhere`, it goes on a line of its own
            // and overflows: breaking inside it is a style's decision, not a
            // layout engine's.
            if (breaking == OVERFLOW_BREAKING) {
                lineStart = breakInside(lineStart, offset, maxWidth, lines);
                // What is left of the word fits, and is the start of the line
                // the next word is tried against.
                lastFitting = offset;
                continue;
            }
            lines.add(lineFor(lineStart, offset));
            lineStart = offset;
        }

        if (lineStart < end) {
            lines.add(lineFor(lineStart, end));
        }
    }

    /// Cuts `[start, end)`, one word too wide for the line, into lines of as many
    /// grapheme clusters as fit, and returns where the remainder starts.
    ///
    /// The remainder is no wider than `maxWidth`, and is left for the caller to
    /// put on the line it is building, so the next word can join it.
    ///
    /// At least one grapheme per line, even when one alone is wider than the
    /// box, or a box narrower than a character would never finish.
    private int breakInside(int start, int end, double maxWidth, List<TextLine> lines) {
        var lineStart = start;
        while (widthOf(lineStart, end) > maxWidth) {
            var cut = offsetFitting(lineStart, end, maxWidth);
            if (cut <= lineStart) {
                var graphemes = BreakIterator.getCharacterInstance();
                graphemes.setText(text);
                cut = Math.min(end, graphemes.following(lineStart));
            }
            lines.add(lineFor(lineStart, cut));
            lineStart = cut;
        }
        return lineStart;
    }

    /// Builds a line, trimming trailing whitespace out of the glyph range and the
    /// width but not out of the text range.
    private TextLine lineFor(int start, int end) {
        var visible = end;
        while (visible > start && Character.isWhitespace(text.charAt(visible - 1))) {
            visible--;
        }
        return new TextLine(start, end, glyphBefore[start], glyphBefore[visible], widthOf(start, visible));
    }

    /// The width of `[start, end)` in logical units — one subtraction, which is
    /// what the prefix sums are for.
    private double widthOf(int start, int end) {
        return font.toLogical(advanceBefore[end] - advanceBefore[start]);
    }

    // --- caret geometry -------------------------------------------------------

    /// The width of the text in `[start, end)`, in logical units.
    ///
    /// The public form of the subtraction wrapping is built on. A width is the
    /// same whichever way the text's runs are placed, so this is what sizes a
    /// box; where a caret or a highlight goes on a line is [#caretX] and
    /// [#stretchesOf], which agree with this on a line that runs left to right
    /// throughout: a caret before offset `o` is `widthBetween(line.start(), o)`
    /// from the line's left edge there.
    ///
    /// Offsets inside a ligature or a surrogate pair report the width up to the
    /// cluster's start, which is the same answer wrapping gets and for the same
    /// reason: there is no width for half a ligature. Callers that want a caret
    /// to land somewhere legal ask [#offsetAt] rather than rounding themselves.
    ///
    /// @throws IndexOutOfBoundsException if either offset is outside the text
    /// @throws IllegalArgumentException  if `end` is before `start`
    public double widthBetween(int start, int end) {
        Objects.checkIndex(start, text.length() + 1);
        Objects.checkIndex(end, text.length() + 1);
        if (end < start) {
            throw new IllegalArgumentException("a text range cannot end before it starts: " + start + ".." + end);
        }
        return widthOf(start, end);
    }

    /// Where the caret before `offset` stands on the line `[lineStart,
    /// lineEnd)`, from the line's left edge as the paint places it, before any
    /// alignment indent.
    ///
    /// On a line that runs left to right throughout this is
    /// `widthBetween(lineStart, offset)`. On a line with runs in both
    /// directions the caret is at the edge of the character after it, the side
    /// that character's run reads from: its left in a left-to-right run, its
    /// right in a right-to-left one. At the end of the visible text it is the
    /// last character's other edge. Past it, in the trailing whitespace, the
    /// caret moves on the side the paragraph reads towards, which in a
    /// right-to-left paragraph is left of the line's left edge, a negative x.
    ///
    /// An offset outside the line is clamped to it.
    ///
    /// @throws IndexOutOfBoundsException if either line offset is outside the text
    /// @throws IllegalArgumentException  if `lineEnd` is before `lineStart`
    public double caretX(int lineStart, int lineEnd, int offset) {
        checkLine(lineStart, lineEnd);
        var at = Math.clamp(offset, lineStart, lineEnd);
        var levels = bidi;
        if (levels == null) {
            return widthOf(lineStart, at);
        }
        var visible = visibleEnd(lineStart, lineEnd);
        return caretX(levels.visualRuns(lineStart, visible), lineStart, visible, at);
    }

    /// [#caretX(int, int, int)] over runs already placed.
    private double caretX(List<LevelRun> runs, int lineStart, int visible, int offset) {
        if (offset > visible) {
            var beyond = widthOf(visible, offset);
            return directionAt(lineStart) == Direction.RTL ? -beyond : widthOf(lineStart, visible) + beyond;
        }
        if (visible == lineStart) {
            return 0;
        }
        var probe = offset < visible ? offset : offset - 1;
        var left = 0.0;
        for (var run : runs) {
            if (run.start() <= probe && probe < run.end()) {
                return left + (run.isRightToLeft() ? widthOf(offset, run.end()) : widthOf(run.start(), offset));
            }
            left += widthOf(run.start(), run.end());
        }
        return left;
    }

    /// The stretches of the line `[lineStart, lineEnd)` that the text `[from,
    /// to)` covers, left to right, from the line's left edge as [#caretX]
    /// measures it: what a selection highlights.
    ///
    /// One stretch on a line that runs one way; one for each run the range
    /// touches on a line that does not, since a logical range over two
    /// directions is two pieces on screen. Touching stretches are joined. The
    /// range is clamped to the line, and empty when nothing of it is left.
    ///
    /// @throws IndexOutOfBoundsException if either line offset is outside the text
    /// @throws IllegalArgumentException  if `lineEnd` is before `lineStart`
    public List<Stretch> stretchesOf(int lineStart, int lineEnd, int from, int to) {
        checkLine(lineStart, lineEnd);
        var a = Math.clamp(Math.min(from, to), lineStart, lineEnd);
        var b = Math.clamp(Math.max(from, to), lineStart, lineEnd);
        if (a == b) {
            return List.of();
        }
        var levels = bidi;
        if (levels == null) {
            return List.of(new Stretch(widthOf(lineStart, a), widthOf(lineStart, b)));
        }
        var visible = visibleEnd(lineStart, lineEnd);
        var stretches = new ArrayList<Stretch>();
        var left = 0.0;
        for (var run : levels.visualRuns(lineStart, visible)) {
            var s = Math.max(a, run.start());
            var e = Math.min(b, run.end());
            if (s < e) {
                stretches.add(
                        run.isRightToLeft()
                                ? new Stretch(left + widthOf(e, run.end()), left + widthOf(s, run.end()))
                                : new Stretch(left + widthOf(run.start(), s), left + widthOf(run.start(), e)));
            }
            left += widthOf(run.start(), run.end());
        }
        var s = Math.max(a, visible);
        if (s < b) {
            stretches.add(
                    directionAt(lineStart) == Direction.RTL
                            ? new Stretch(-widthOf(visible, b), -widthOf(visible, s))
                            : new Stretch(left + widthOf(visible, s), left + widthOf(visible, b)));
        }
        return Stretch.joined(stretches);
    }

    private void checkLine(int lineStart, int lineEnd) {
        Objects.checkIndex(lineStart, text.length() + 1);
        Objects.checkIndex(lineEnd, text.length() + 1);
        if (lineEnd < lineStart) {
            throw new IllegalArgumentException("a line cannot end before it starts: " + lineStart + ".." + lineEnd);
        }
    }

    /// One past the last character of `[lineStart, lineEnd)` that is not
    /// trailing whitespace.
    private int visibleEnd(int lineStart, int lineEnd) {
        var visible = lineEnd;
        while (visible > lineStart && Character.isWhitespace(text.charAt(visible - 1))) {
            visible--;
        }
        return visible;
    }

    /// The offset in `[lineStart, lineEnd]` whose caret sits nearest `x`.
    ///
    /// The other direction of [#caretX], and what a click in a text field
    /// asks: `x` is measured from the **line's** left edge, and the answer is a
    /// text offset the caret can legally occupy.
    ///
    /// ## Nearest, and why that is the whole rule
    ///
    /// Every editor puts the caret *after* a character clicked on its right half
    /// and *before* one clicked on its left. That is not a separate rule — it is
    /// what "nearest caret position" already means, because the two caret
    /// positions bracketing a glyph are its edges and the midpoint is where the
    /// nearer one changes. So there is no half-advance arithmetic here, only a
    /// walk and a minimum.
    ///
    /// ## Boundaries, not offsets
    ///
    /// The walk steps by **grapheme cluster** — `java.text.BreakIterator`'s
    /// character instance, the same class the wrap uses for lines — so a click
    /// can never land between the two halves of a surrogate pair or between a
    /// letter and the accent over it. Those offsets exist in the string and are
    /// not places a caret can be; returning one would put the next keystroke
    /// inside a character.
    ///
    /// An `x` left of the line is `lineStart` and one right of it is `lineEnd`,
    /// which is what dragging a selection off the end of a field should do.
    /// On a line with runs in both directions the walk is over every boundary
    /// and the nearest [#caretX] wins, so an `x` past either edge is the offset
    /// whose caret is at that edge.
    ///
    /// @param lineStart the first offset of the line, from [TextLine#start()]
    /// @param lineEnd   one past its last, from [TextLine#end()]
    /// @param x         the distance from the line's left edge, in logical units
    /// @throws IndexOutOfBoundsException if either offset is outside the text
    /// @throws IllegalArgumentException  if `lineEnd` is before `lineStart`
    public int offsetAt(int lineStart, int lineEnd, double x) {
        Objects.checkIndex(lineStart, text.length() + 1);
        Objects.checkIndex(lineEnd, text.length() + 1);
        if (lineEnd < lineStart) {
            throw new IllegalArgumentException("a line cannot end before it starts: " + lineStart + ".." + lineEnd);
        }
        if (lineStart == lineEnd || Double.isNaN(x)) {
            // A click at an unknown position is a click at the start.
            return lineStart;
        }
        var levels = bidi;
        if (levels != null) {
            return nearestCaret(levels, lineStart, lineEnd, x);
        }
        if (!(x > 0)) {
            return lineStart;
        }

        var graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);

        var best = lineStart;
        var bestDistance = Math.abs(x - widthOf(lineStart, lineStart));
        for (var offset = graphemes.following(lineStart);
                offset != BreakIterator.DONE && offset <= lineEnd;
                offset = graphemes.next()) {

            var distance = Math.abs(x - widthOf(lineStart, offset));
            if (distance > bestDistance) {
                // Advances are non-negative, so the distance to the target falls
                // and then rises. Once it has risen the answer is behind us --
                // and stopping here is what keeps a click near the start of a
                // long line from walking the whole line.
                break;
            }
            bestDistance = distance;
            best = offset;
        }
        return best;
    }

    /// [#offsetAt] on a line placed in visual order: every grapheme boundary,
    /// and the one whose caret is nearest. The first wins a tie.
    private int nearestCaret(BidiLevels levels, int lineStart, int lineEnd, double x) {
        var visible = visibleEnd(lineStart, lineEnd);
        var runs = levels.visualRuns(lineStart, visible);
        var graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);
        var best = lineStart;
        var bestDistance = Math.abs(x - caretX(runs, lineStart, visible, lineStart));
        for (var offset = graphemes.following(lineStart);
                offset != BreakIterator.DONE && offset <= lineEnd;
                offset = graphemes.next()) {
            var distance = Math.abs(x - caretX(runs, lineStart, visible, offset));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = offset;
            }
        }
        return best;
    }
}
