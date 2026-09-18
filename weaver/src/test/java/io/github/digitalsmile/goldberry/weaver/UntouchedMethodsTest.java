package io.github.digitalsmile.goldberry.weaver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.weaver.models.Attributed;
import io.github.digitalsmile.goldberry.weaver.models.Counter;
import io.github.digitalsmile.goldberry.weaver.models.Merged;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Which methods the weaver rebuilds, and which it copies straight through.
///
/// A rebuilt method has its stack map frames regenerated, and a regenerated
/// frame where two of the author's own types meet — `Base x = flag ? new A() :
/// new B()` — has to be told what `A` and `B` have in common. That is a question
/// for the class hierarchy resolver, and the resolver only answers it if it can
/// see the classes. So a weaver that rebuilt *every* method made every method's
/// frames its own problem: an application whose model happened to sit in a class
/// with an ordinary ternary failed the build with `Could not resolve class`, on
/// code the weaver had no business touching.
///
/// The rule here is the narrow one: rebuild a method if and only if it contains
/// a write this weaver replaces. Everything else is copied out of the original
/// class file exactly as javac verified it — down to the order its own
/// attributes sit in, which is what these assertions read, because a rebuild
/// writes `LocalVariableTable` before `LineNumberTable` and javac writes them
/// the other way round.
///
/// The methods that *are* rebuilt still need the resolver to work, which is the
/// other half of the fix and lives in `goldberry.weave.gradle`: the weaver's
/// JavaExec now runs with the classes being woven and their compile classpath
/// beside it, so the hierarchy it is asked about is one it can see. No unit test
/// holds that half — a woven-mode build of a real module does.
@DisplayName("what the weaver rebuilds, and what it copies through")
class UntouchedMethodsTest {

    /// Everything about a method that a rebuild disturbs.
    ///
    /// Not a hash of the bytes, because the class-file API gives no way to ask
    /// for a method's bytes; this is every axis it does expose, and the
    /// attribute *order* is the sharp one — it is the fingerprint of who wrote
    /// the method last.
    private record Shape(List<String> attributes, List<String> codeAttributes,
            int maxStack, int maxLocals, List<String> frames, List<String> code) {

        static Shape of(MethodModel method) {
            var attributes = new ArrayList<String>();
            for (var attribute : method.attributes()) {
                attributes.add(attribute.attributeName().stringValue());
            }
            var code = (CodeAttribute) method.code().orElseThrow();
            var codeAttributes = new ArrayList<String>();
            for (var attribute : code.attributes()) {
                codeAttributes.add(attribute.attributeName().stringValue());
            }
            var frames = new ArrayList<String>();
            code.findAttribute(Attributes.stackMapTable()).ifPresent(table -> {
                for (var frame : table.entries()) {
                    frames.add(frame.frameType() + " locals=" + types(frame.locals())
                            + " stack=" + types(frame.stack()));
                }
            });
            var body = new ArrayList<String>();
            for (var element : code) {
                var rendered = render(element);
                if (rendered != null) {
                    body.add(rendered);
                }
            }
            return new Shape(attributes, codeAttributes,
                    code.maxStack(), code.maxLocals(), frames, body);
        }

        private static String types(List<StackMapFrameInfo.VerificationTypeInfo> infos) {
            var names = new ArrayList<String>();
            for (var info : infos) {
                names.add(info instanceof StackMapFrameInfo.ObjectVerificationTypeInfo object
                        ? object.className().asInternalName()
                        : info.tag() + "");
            }
            return names.toString();
        }

        private static String render(CodeElement element) {
            return switch (element) {
                case FieldInstruction field -> field.opcode() + " "
                        + field.owner().asInternalName() + "." + field.name().stringValue();
                case InvokeInstruction invoke -> invoke.opcode() + " "
                        + invoke.owner().asInternalName() + "." + invoke.name().stringValue()
                        + invoke.type().stringValue();
                case Instruction instruction -> instruction.opcode().toString();
                // Labels, line numbers and local-variable spans, which say
                // nothing about whether the body was rewritten.
                default -> null;
            };
        }
    }

    /// Every method of a compiled class, shaped, by name and descriptor.
    private static Map<String, Shape> shapes(byte[] bytes) {
        var shapes = new LinkedHashMap<String, Shape>();
        for (var method : ClassFile.of().parse(bytes).methods()) {
            if (method.code().isPresent()) {
                shapes.put(method.methodName().stringValue()
                        + method.methodTypeSymbol().descriptorString(), Shape.of(method));
            }
        }
        return shapes;
    }

    /// The names of the methods that assign to one of `owner`'s fields.
    ///
    /// Read off the *unwoven* class, so it is the question the weaver asks
    /// rather than the answer it gave.
    private static List<String> writersIn(byte[] bytes, Class<?> owner) {
        var internal = owner.getName().replace('.', '/');
        var writers = new ArrayList<String>();
        for (var method : ClassFile.of().parse(bytes).methods()) {
            var code = method.code().orElse(null);
            if (code == null || method.methodName().equalsString("<init>")) {
                continue;
            }
            for (var element : code) {
                if (element instanceof FieldInstruction field
                        && field.opcode() == Opcode.PUTFIELD
                        && field.owner().asInternalName().equals(internal)) {
                    writers.add(method.methodName().stringValue()
                            + method.methodTypeSymbol().descriptorString());
                    break;
                }
            }
        }
        return writers;
    }

    @Nested
    @DisplayName("a method with no write to a bound field")
    class Untouched {

        @Test
        @DisplayName("comes through byte for byte, frames and all, even when it merges two of the author's types")
        void aJoinIsLeftAlone() {
            // Merged.pick is `Base picked = flag ? new A() : new B()`. Its frame
            // at the join names Base because javac knew it from the source; a
            // rebuild would have had to work it out, and in the build -- where
            // the weaver ran with only its own jar on the classpath -- it could
            // not.
            var raw = shapes(Woven.bytesOf(Merged.class));
            var woven = shapes(Woven.weave(Merged.class));

            assertEquals(raw.get("pick(Z)Ljava/lang/String;"), woven.get("pick(Z)Ljava/lang/String;"));
            assertTrue(raw.get("pick(Z)Ljava/lang/String;").frames().stream()
                            .anyMatch(frame -> frame.contains("Merged$Base")),
                    "the fixture is supposed to have a frame naming the merged type: "
                            + raw.get("pick(Z)Ljava/lang/String;").frames());
        }

        @Test
        @DisplayName("and so does every other one, on a model with nine actions")
        void everyUntouchedMethodOfAModel() {
            var raw = shapes(Woven.bytesOf(Counter.class));
            var woven = shapes(Woven.weave(Counter.class));
            var writers = writersIn(Woven.bytesOf(Counter.class), Counter.class);

            for (var name : raw.keySet()) {
                if (writers.contains(name)) {
                    continue;
                }
                assertEquals(raw.get(name), woven.get(name),
                        "Counter." + name + " has no write to rewrite and should not have been"
                                + " rebuilt at all");
            }
        }

        @Test
        @DisplayName("on a class that is not a model either -- only the method that writes is rebuilt")
        void onAPlainWriter() {
            var raw = shapes(Woven.bytesOf(Attributed.Helper.class));
            var woven = shapes(Woven.groupBytes(Attributed.class, Attributed.Helper.class)
                    .get(Attributed.Helper.class.getName()));

            assertEquals(raw.get("<init>(Lio/github/digitalsmile/goldberry/weaver/models/Attributed;)V"),
                    woven.get("<init>(Lio/github/digitalsmile/goldberry/weaver/models/Attributed;)V"));
            assertNotEquals(raw.get("reset(Ljava/lang/Object;)Ljava/lang/Object;"),
                    woven.get("reset(Ljava/lang/Object;)Ljava/lang/Object;"),
                    "reset() does write to the model, so it is one of the methods that is rebuilt");
        }
    }

    @Nested
    @DisplayName("a method that does write")
    class Rebuilt {

        @Test
        @DisplayName("is rebuilt, and its write becomes a setter call")
        void theWriteIsRewritten() {
            var woven = Woven.weave(Merged.class);
            var calls = new ArrayList<String>();
            for (var method : ClassFile.of().parse(woven).methods()) {
                if (!method.methodName().equalsString("bump")) {
                    continue;
                }
                for (var element : method.code().orElseThrow()) {
                    if (element instanceof InvokeInstruction invoke) {
                        calls.add(invoke.name().stringValue());
                    }
                }
            }

            assertTrue(calls.contains("goldberry$set$count"),
                    "the write in bump() should have become a setter call: " + calls);
        }

        @Test
        @DisplayName("and it keeps its frames, which is the resolver's question and the build's half of the fix")
        void aRebuiltJoinStillVerifies() {
            // bump() has the same join as pick() *and* a write, so it is rebuilt
            // and its frames are regenerated. That it comes out naming Base again
            // is the resolver answering -- here because the fixture is on the
            // test classpath, and in a build because `goldberry.weave.gradle` now
            // puts the classes being woven on the weaver's.
            var frames = shapes(Woven.weave(Merged.class))
                    .get("bump(Z)V").frames();

            assertTrue(frames.stream().anyMatch(frame -> frame.contains("Merged$Base")),
                    "the regenerated frame should still name the merged type: " + frames);
        }
    }
}
