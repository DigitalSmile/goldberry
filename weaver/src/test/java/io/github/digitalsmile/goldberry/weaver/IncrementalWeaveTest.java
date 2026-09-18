package io.github.digitalsmile.goldberry.weaver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.weaver.models.Detached;
import io.github.digitalsmile.goldberry.weaver.models.DetachedTools;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// The second weave over a tree javac only half recompiled.
///
/// `weaveModels` runs over the compile task's output directory, in place, on
/// every build. Which means the *usual* input is a mixture: the one class an
/// author just edited, freshly compiled and unwoven, sitting in a directory of
/// classes the weaver already went through on the last build. A weaver that only
/// understands "all unwoven" is a weaver that works the first time and quietly
/// stops working after that.
///
/// It did. `rewired` returned null for a class that was already woven, so on the
/// second pass the model beside the recompiled sibling was not in the map of
/// models at all — the sibling's assignments stayed assignments, its values moved
/// without notifying anybody, and the model's setters were never opened to the
/// package that had started calling them. Nothing failed; a build that changed
/// one file silently produced a class that binds nothing.
///
/// Both directions are here, because the mixture cuts both ways: the sibling
/// recompiled and the model not, and the model recompiled and the sibling not.
@DisplayName("a second weave over a half-recompiled tree")
class IncrementalWeaveTest {

    private static final String MODEL = Detached.class.getName().replace('.', '/');

    /// A directory of compiled classes, as javac would have left it.
    private record Tree(Path root) {

        static Tree of(Path root, Class<?>... types) {
            var tree = new Tree(root);
            for (var type : types) {
                tree.write(type, Woven.bytesOf(type));
            }
            return tree;
        }

        /// Puts `type` back the way javac compiles it — what a recompile does.
        void recompile(Class<?> type) {
            write(type, Woven.bytesOf(type));
        }

        void write(Class<?> type, byte[] bytes) {
            var file = file(type);
            try {
                Files.createDirectories(file.getParent());
                Files.write(file, bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        byte[] read(Class<?> type) {
            try {
                return Files.readAllBytes(file(type));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        Path file(Class<?> type) {
            return root.resolve(type.getName().replace('.', '/') + ".class");
        }

        /// One `--models` pass over the whole tree, and what it says it changed.
        ///
        /// Sorted, because the order is `Files.walk`'s and that is the file
        /// system's business rather than the weaver's.
        List<String> weave() {
            var woven = new ArrayList<String>();
            try {
                WeaverMain.weaveTree(root, woven, true, false);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return woven.stream().sorted().toList();
        }
    }

    /// The flags on the setter the weaver synthesised for `field`.
    private static int setterFlags(byte[] bytes, String field) {
        for (var method : ClassFile.of().parse(bytes).methods()) {
            if (method.methodName().equalsString("goldberry$set$" + field)) {
                return method.flags().flagsMask();
            }
        }
        throw new AssertionError("no setter for " + field + "; the class was never woven");
    }

    /// Whether any method of `bytes` still assigns straight to `owner.field`.
    private static boolean assignsDirectly(byte[] bytes, String owner, String field) {
        for (var method : ClassFile.of().parse(bytes).methods()) {
            var code = method.code().orElse(null);
            if (code == null) {
                continue;
            }
            for (var element : code) {
                if (element instanceof FieldInstruction instruction
                        && instruction.opcode() == Opcode.PUTFIELD
                        && instruction.owner().asInternalName().equals(owner)
                        && instruction.name().equalsString(field)) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Whether any method of `bytes` calls `owner`'s setter for `field`.
    private static boolean callsSetter(byte[] bytes, String owner, String field) {
        for (var method : ClassFile.of().parse(bytes).methods()) {
            var code = method.code().orElse(null);
            if (code == null) {
                continue;
            }
            for (var element : code) {
                if (element instanceof InvokeInstruction call
                        && call.owner().asInternalName().equals(owner)
                        && call.name().equalsString("goldberry$set$" + field)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    @DisplayName("the first pass is the baseline: the sibling's writes are rewritten and the setters open up")
    void firstPass(@TempDir Path dir) {
        var tree = Tree.of(dir, Detached.class, DetachedTools.class);

        assertEquals(List.of("Detached", "DetachedTools"), tree.weave());

        assertFalse(assignsDirectly(tree.read(DetachedTools.class), MODEL, "count"));
        assertTrue(callsSetter(tree.read(DetachedTools.class), MODEL, "count"));
        // Not nestmates, so a private setter would be an IllegalAccessError at
        // the first click (ADR-0137).
        assertEquals(0, setterFlags(tree.read(Detached.class), "count") & ClassFile.ACC_PRIVATE);
    }

    @Test
    @DisplayName("and a pass over a tree that changed nothing writes nothing")
    void idempotent(@TempDir Path dir) {
        var tree = Tree.of(dir, Detached.class, DetachedTools.class);
        tree.weave();

        assertEquals(List.of(), tree.weave(),
                "the weaver rewrites in place and runs on every build, so a second pass over its"
                        + " own output has to be silent");
    }

    @Nested
    @DisplayName("when javac recompiled only the sibling")
    class SiblingOnly {

        @Test
        @DisplayName("its writes are rewritten against the model that was already woven")
        void theSiblingIsRewired(@TempDir Path dir) {
            var tree = Tree.of(dir, Detached.class, DetachedTools.class);
            tree.weave();
            // What an author who edited DetachedTools.java and nothing else
            // leaves behind: one unwoven class file in a woven directory.
            tree.recompile(DetachedTools.class);

            tree.weave();

            var tools = tree.read(DetachedTools.class);
            assertFalse(assignsDirectly(tools, MODEL, "count"),
                    "the recompiled sibling still assigns straight to the model's field, so the"
                            + " value moves and nothing is notified");
            assertTrue(callsSetter(tools, MODEL, "count"));
            assertTrue(callsSetter(tools, MODEL, "label"));
        }

        @Test
        @DisplayName("and when the sibling is new, the model already woven beside it opens its setters")
        void aNewSiblingOpensTheModel(@TempDir Path dir) {
            // The sharpest form of it: the model was woven on a build where
            // nothing wrote to it from outside its nest, so it got private
            // setters -- correctly, then. Now there is a class beside it that
            // does, and the model is a file the weaver has already been through.
            // Nothing recompiles a class because a *different* file appeared, so
            // if the second pass will not reopen it, nothing ever will, and the
            // sibling's call site is an IllegalAccessError at the first click.
            var tree = Tree.of(dir, Detached.class);
            tree.weave();
            assertTrue((setterFlags(tree.read(Detached.class), "count") & ClassFile.ACC_PRIVATE) != 0,
                    "with nothing writing to it, its setters start private");

            tree.write(DetachedTools.class, Woven.bytesOf(DetachedTools.class));
            assertEquals(List.of("Detached", "DetachedTools"), tree.weave());

            assertEquals(0, setterFlags(tree.read(Detached.class), "count") & ClassFile.ACC_PRIVATE);
            assertEquals(0, setterFlags(tree.read(Detached.class), "label") & ClassFile.ACC_PRIVATE);
            assertTrue(callsSetter(tree.read(DetachedTools.class), MODEL, "count"));
            // And having opened them, it settles: a third pass has nothing to say.
            assertEquals(List.of(), tree.weave());
        }
    }

    @Nested
    @DisplayName("when javac recompiled only the model")
    class ModelOnly {

        @Test
        @DisplayName("it is woven again with setters the sibling can still call")
        void theModelReopens(@TempDir Path dir) {
            var tree = Tree.of(dir, Detached.class, DetachedTools.class);
            tree.weave();
            // The mirror image, and the harder half: the only class that writes
            // to this model is one the weaver already rewrote, so its `putfield`
            // is gone and the write it stands for is a call to the setter. A
            // weaver that only counts `putfield`s sees nobody writing here and
            // hands the model private setters -- which the sibling's existing
            // call site cannot reach.
            tree.recompile(Detached.class);

            assertEquals(List.of("Detached"), tree.weave());

            assertEquals(0, setterFlags(tree.read(Detached.class), "count") & ClassFile.ACC_PRIVATE,
                    "the sibling beside it still calls this setter, so it may not be private");
            assertEquals(0, setterFlags(tree.read(Detached.class), "label") & ClassFile.ACC_PRIVATE);
        }

        @Test
        @DisplayName("and the sibling, which nothing recompiled, is left exactly as it was")
        void theSiblingIsUntouched(@TempDir Path dir) {
            var tree = Tree.of(dir, Detached.class, DetachedTools.class);
            tree.weave();
            var before = tree.read(DetachedTools.class);
            tree.recompile(Detached.class);

            tree.weave();

            assertArrayEquals(before, tree.read(DetachedTools.class),
                    "nothing about the sibling changed, so nothing about its class file should");
        }
    }

    @Nested
    @DisplayName("a model on its own")
    class Alone {

        @Test
        @DisplayName("keeps its private setters, because widening is what a writer outside the nest asks for")
        void nobodyWritesFromOutside(@TempDir Path dir) {
            // The counterweight to every assertion above: opening a setter is a
            // decision made about a *writer*, not something the second pass does
            // to everything it meets.
            var tree = Tree.of(dir, Detached.class);

            assertEquals(List.of("Detached"), tree.weave());
            assertTrue(ClassFile.of().parse(tree.read(Detached.class)).methods().stream()
                            .filter(m -> m.methodName().stringValue().startsWith("goldberry$set$"))
                            .allMatch(m -> m.flags().has(AccessFlag.PRIVATE)),
                    "no class in this tree writes to it, so its setters stay private");
            assertEquals(List.of(), tree.weave());
        }
    }
}
