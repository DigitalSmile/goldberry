package dev.goldberry.text.itemize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The split by coverage, with faces that are names and coverage that is a set
/// of characters: what is under test is where runs start and end, which is the
/// text's business, and not what any real face holds.
@DisplayName("the itemizer, splitting by coverage")
class ItemizerCoverageTest {

    /// Faces in search order, each the set of characters it has.
    private static final class Faces implements FaceChoice<String> {

        private final Map<String, Set<Integer>> faces = new LinkedHashMap<>();

        Faces with(String name, String characters) {
            faces.put(name, characters.codePoints().boxed().collect(Collectors.toSet()));
            return this;
        }

        @Override
        public boolean covers(String face, String text, int start, int end) {
            var has = faces.get(face);
            return text.substring(start, end).codePoints().allMatch(has::contains);
        }

        @Override
        public @Nullable String fallback(String text, int start, int end) {
            for (var face : faces.keySet()) {
                if (!face.equals(BASE) && covers(face, text, start, end)) {
                    return face;
                }
            }
            var first = text.codePointAt(start);
            for (var face : faces.entrySet()) {
                if (!face.getKey().equals(BASE) && face.getValue().contains(first)) {
                    return face.getKey();
                }
            }
            return null;
        }
    }

    private static final String BASE = "base";
    private static final String LATIN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ .,()0123456789\u0301";

    private static List<FaceRun<String>> split(String text, Faces faces) {
        return Itemizer.byCoverage(text, 0, text.length(), BASE, faces);
    }

    private static Faces latinAndHan() {
        return new Faces().with(BASE, LATIN).with("han", "中文名字 .,").with("arabic", "محمدعلي ");
    }

    @Test
    @DisplayName("text the base face covers is one run in it")
    void covered() {
        assertEquals(List.of(new FaceRun<>(0, 11, BASE)), split("Hello there", latinAndHan()));
    }

    @Test
    @DisplayName("a run the base face lacks goes to the fallback that has it, and the words before it stay")
    void routed() {
        assertEquals(List.of(new FaceRun<>(0, 4, BASE), new FaceRun<>(4, 6, "han")), split("Ann 中文", latinAndHan()));
    }

    @Test
    @DisplayName("a space between two runs of one fallback joins them, so a name is one shaping")
    void neutralsJoin() {
        assertEquals(List.of(new FaceRun<>(0, 5, "han")), split("中文 名字", latinAndHan()));
        assertEquals(List.of(new FaceRun<>(0, 8, "arabic")), split("محمد علي", latinAndHan()));
    }

    @Test
    @DisplayName("but a space between a fallback run and the base face's words stays in the base face")
    void neutralsAtAnEdgeStay() {
        assertEquals(
                List.of(new FaceRun<>(0, 4, BASE), new FaceRun<>(4, 6, "han"), new FaceRun<>(6, 10, BASE)),
                split("Ann 中文 Bob", latinAndHan()));
    }

    @Test
    @DisplayName("a letter the base face has stays in it even where the fallback has it too")
    void baseWins() {
        // Every real CJK face has Latin in it. Drawing `Ann` in it because the
        // name after it is in Han would change a word nobody asked to change.
        var faces = new Faces().with(BASE, LATIN).with("han", "中文" + LATIN);
        assertEquals(
                List.of(new FaceRun<>(0, 4, BASE), new FaceRun<>(4, 6, "han"), new FaceRun<>(6, 8, BASE)),
                split("Ann 中文ab", faces));
    }

    @Test
    @DisplayName("a mark is never split from its letter: the cluster goes where all of it is")
    void clusterWhole() {
        // The base face has `e` and not the fatha; the fallback has both.
        var faces = new Faces().with(BASE, LATIN).with("arabic", "e\u064E");
        assertEquals(List.of(new FaceRun<>(0, 1, BASE), new FaceRun<>(1, 3, "arabic")), split("xe\u064E", faces));
    }

    @Test
    @DisplayName("and when no face has all of it, it goes where its letter is, mark and all")
    void clusterFollowsItsLetter() {
        // The fallback has 中 and not U+0301; the base face has U+0301 and not 中.
        assertEquals(List.of(new FaceRun<>(0, 2, "han")), split("中\u0301", latinAndHan()));
    }

    @Test
    @DisplayName("a surrogate pair is one character, and is routed as one")
    void surrogatePairs() {
        var faces = new Faces().with(BASE, LATIN).with("math", "𝕳𝖊");
        assertEquals(List.of(new FaceRun<>(0, 1, BASE), new FaceRun<>(1, 5, "math")), split("a𝕳𝖊", faces));
    }

    @Test
    @DisplayName("a word stays with the fallback it started in, even where an earlier one has a letter of it")
    void aWordStaysInOneFace() {
        // The first fallback has ح and nothing else of the word; splitting the word
        // there would break its joining in the middle.
        var faces = new Faces().with(BASE, LATIN).with("partial", "ح").with("arabic", "محمد");
        assertEquals(List.of(new FaceRun<>(0, 4, "arabic")), split("محمد", faces));
    }

    @Test
    @DisplayName("a character nobody has stays in the base face, as `.notdef`, in the run around it")
    void nobodyHasIt() {
        assertEquals(List.of(new FaceRun<>(0, 3, BASE)), split("a☃b", latinAndHan()));
    }

    @Test
    @DisplayName("a range inside the text is split on its own offsets, and an empty one is nothing")
    void ranges() {
        var text = "xx中文yy";
        assertEquals(
                List.of(new FaceRun<>(2, 4, "han"), new FaceRun<>(4, 5, BASE)),
                Itemizer.byCoverage(text, 2, 5, BASE, latinAndHan()));
        assertEquals(List.of(), Itemizer.byCoverage(text, 3, 3, BASE, latinAndHan()));
        assertThrows(IndexOutOfBoundsException.class, () -> Itemizer.byCoverage(text, 2, 9, BASE, latinAndHan()));
    }
}
