package dev.goldberry.text.mark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.text.edit.EditHistory;
import dev.goldberry.text.mark.MarkKind.Link;
import dev.goldberry.text.mark.MarkKind.Mention;
import dev.goldberry.text.mark.MarkKind.Style;

/// Random edits over random marked text, checking what must hold after every
/// one of them: the normal form, that no atom is ever split or changed, and
/// that undoing every step gives back the first value.
///
/// Seeded, so a failure is the same failure on every machine and names the
/// seed that found it.
class MarkedEditPropertyTest {

    /// Fewer than [EditHistory#DEPTH], so undoing every step reaches the start.
    private static final int STEPS = 150;

    private static final List<MarkKind> TOGGLES = List.of(
            Style.BOLD,
            Style.ITALIC,
            Style.UNDERLINE,
            Style.STRIKE,
            Style.CODE,
            Style.SPOILER,
            new Link(URI.create("https://goldberry.dev")),
            new Link(URI.create("https://example.org")));

    private static final List<String> TYPED = List.of("a", "b", " ", "\n", "é", "é", "😀", "word ");

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1, 2, 3, 5, 8, 13, 21, 34, 55, 89})
    void invariantsHoldAfterEveryEdit(long seed) {
        RandomGenerator random = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
        var atoms = new HashMap<String, String>();
        var history = new EditHistory<MarkedEdit>();
        var first = MarkedEdit.of(MarkedText.of("The quick brown fox"));
        var edit = first;
        var mentions = 0;

        for (var step = 0; step < STEPS; step++) {
            var before = edit;
            var length = edit.value().length();
            var kind = EditHistory.Kind.OTHER;
            switch (random.nextInt(12)) {
                case 0, 1, 2 -> {
                    edit = edit.insert(TYPED.get(random.nextInt(TYPED.size())));
                    kind = EditHistory.Kind.TYPING;
                }
                case 3 -> {
                    edit = edit.backspace();
                    kind = EditHistory.Kind.DELETING;
                }
                case 4 -> {
                    edit = edit.delete();
                    kind = EditHistory.Kind.DELETING;
                }
                case 5 -> edit = random.nextBoolean() ? edit.deleteWordBefore() : edit.deleteWordAfter();
                case 6 -> edit = edit.caretTo(random.nextInt(length + 1), random.nextBoolean());
                case 7 -> edit = random.nextBoolean() ? edit.left(random.nextBoolean()) : edit.right(true);
                case 8 -> {
                    edit = edit.toggle(TOGGLES.get(random.nextInt(TOGGLES.size())));
                    kind = EditHistory.Kind.FORMATTING;
                }
                case 9 -> {
                    var id = "user:" + mentions++;
                    var name = "@" + id;
                    atoms.put(id, name);
                    var mention = MarkedText.of(name).mark(0, name.length(), new Mention(id));
                    edit = edit.replace(edit.start(), edit.end(), mention);
                }
                case 10 -> edit = edit.insert(edit.selected());
                default -> edit = edit.wordAt(random.nextInt(length + 1));
            }
            history.record(before, edit, kind);
            check(edit, atoms, seed, step);
        }

        while (history.canUndo()) {
            edit = history.undo(edit);
        }
        assertEquals(first.value(), edit.value(), "seed " + seed + ": undoing every step gives back the start");
    }

    private static void check(MarkedEdit edit, Map<String, String> atoms, long seed, int step) {
        var where = "seed " + seed + ", step " + step + ": ";
        var value = edit.value();
        var marks = value.marks();

        var sorted = new ArrayList<>(marks);
        sorted.sort(Mark.ORDER);
        assertEquals(sorted, marks, where + "sorted");

        for (var i = 0; i < marks.size(); i++) {
            var mark = marks.get(i);
            assertTrue(mark.start() >= 0 && mark.end() <= value.length(), where + mark + " inside the text");
            for (var j = i + 1; j < marks.size(); j++) {
                var other = marks.get(j);
                if (mark.kind().equals(other.kind()) && !mark.kind().isAtomic()) {
                    assertFalse(
                            mark.start() <= other.end() && other.start() <= mark.end(),
                            where + mark + " and " + other + " should be one");
                }
                if (MarkKind.conflict(mark.kind(), other.kind())) {
                    assertFalse(
                            mark.overlaps(other.start(), other.end()), where + mark + " and " + other + " conflict");
                }
            }
            if (mark.kind() instanceof Mention(var id)) {
                assertEquals(
                        atoms.get(id),
                        value.text().substring(mark.start(), mark.end()),
                        where + "an atom's text never changes");
            }
        }

        assertTrue(value.atomAt(edit.caret()).isEmpty(), where + "the caret is never inside an atom");
        assertTrue(value.atomAt(edit.anchor()).isEmpty(), where + "nor is the anchor");
        assertEquals(value, new MarkedText(value.text(), marks.reversed()), where + "normal form is a fixed point");
    }
}
