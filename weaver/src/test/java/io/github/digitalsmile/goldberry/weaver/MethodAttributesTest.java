package io.github.digitalsmile.goldberry.weaver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.weaver.models.Attributed;
import io.github.digitalsmile.goldberry.weaver.models.Counter;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// What a method carries *beside* its code, after the weaver has been through it.
///
/// A method is not its instructions. `Signature` is how a generic method is
/// generic to anything that reads it back — `Method.getGenericReturnType`, an
/// IDE, a serialiser. `RuntimeVisibleAnnotations` is how `@Action` survives to
/// the reflective binder an unwoven jar uses (ADR-0155). `MethodParameters` is
/// how a parameter keeps the name its author gave it. `Exceptions` is the
/// `throws` clause. All four are attributes of the method, none of them is
/// reachable from the code array, and the weaver rebuilt every method of every
/// class it touched from the code array alone — so all four were dropped from
/// every woven model and from every class that writes to one.
///
/// [NativeImageComplianceTest#annotationRetention] claims to hold the third of
/// those and does not: it reads `Counter.class` off the test classpath, which is
/// the class javac produced and not the one the weaver wrote. Every assertion
/// here reads the **woven bytes** instead, which is the only place the loss was
/// ever visible.
@DisplayName("the attributes a method carries beside its code")
class MethodAttributesTest {

    /// The method of that name in a compiled class.
    private static MethodModel method(byte[] bytes, String name) {
        for (var method : ClassFile.of().parse(bytes).methods()) {
            if (method.methodName().equalsString(name)) {
                return method;
            }
        }
        throw new AssertionError("no method " + name + " in "
                + ClassFile.of().parse(bytes).thisClass().asInternalName());
    }

    /// The names of the attributes on it, in the order they appear.
    private static List<String> attributeNames(byte[] bytes, String name) {
        var names = new ArrayList<String>();
        for (var attribute : method(bytes, name).attributes()) {
            names.add(attribute.attributeName().stringValue());
        }
        return names;
    }

    /// The woven bytes of a class that only *writes* to a model, which needs the
    /// model beside it to be rewritten at all.
    private static byte[] wovenHelper() {
        return Woven.groupBytes(Attributed.class, Attributed.Helper.class)
                .get(Attributed.Helper.class.getName());
    }

    @Nested
    @DisplayName("on a woven model")
    class OnAModel {

        @Test
        @DisplayName("an @Action method keeps its RuntimeVisibleAnnotations, which is what an unwoven jar binds from")
        void actionsKeepTheirAnnotations() {
            // The reviewer's case, verbatim: weaving Counter lost click()'s
            // annotations, and the test that was supposed to notice read the
            // unwoven class.
            var annotations = method(Woven.weave(Counter.class), "click")
                    .findAttribute(Attributes.runtimeVisibleAnnotations())
                    .orElseThrow(() -> new AssertionError(
                            "woven Counter.click() carries no RuntimeVisibleAnnotations"))
                    .annotations();

            assertEquals(List.of("io/github/digitalsmile/goldberry/bind/Action"),
                    annotations.stream()
                            .map(a -> a.classSymbol().descriptorString())
                            .map(d -> d.substring(1, d.length() - 1))
                            .toList());
        }

        @Test
        @DisplayName("a generic method keeps its Signature, so it is still generic to whoever reads it back")
        void genericMethodsKeepTheirSignature() {
            var raw = method(Woven.bytesOf(Attributed.class), "sorted")
                    .findAttribute(Attributes.signature()).orElseThrow()
                    .signature().stringValue();
            var woven = method(Woven.weave(Attributed.class), "sorted")
                    .findAttribute(Attributes.signature())
                    .orElseThrow(() -> new AssertionError(
                            "woven Attributed.sorted() lost its Signature"))
                    .signature().stringValue();

            assertEquals(raw, woven);
            assertTrue(woven.contains("Comparable"), "the bound is part of the signature: " + woven);
        }

        @Test
        @DisplayName("a throws clause and its parameter names survive too")
        void throwsAndParameterNamesSurvive() {
            var woven = Woven.weave(Attributed.class);

            assertEquals(List.of("java/io/IOException"),
                    method(woven, "sorted").findAttribute(Attributes.exceptions())
                            .orElseThrow(() -> new AssertionError("the throws clause is gone"))
                            .exceptions().stream().map(e -> e.asInternalName()).toList());
            assertEquals(List.of("items"),
                    method(woven, "sorted").findAttribute(Attributes.methodParameters())
                            .orElseThrow(() -> new AssertionError("the parameter names are gone"))
                            .parameters().stream()
                            .map(p -> p.name().orElseThrow().stringValue()).toList());
        }

        @Test
        @DisplayName("and nothing at all is lost: every method carries what it started with")
        void nothingIsLost() {
            // Written as a sweep rather than as four names, so a fifth attribute
            // -- a `@Deprecated`, a type annotation, whatever javac writes next --
            // is covered the day somebody adds one.
            var raw = Woven.bytesOf(Attributed.class);
            var woven = Woven.weave(Attributed.class);

            for (var before : ClassFile.of().parse(raw).methods()) {
                var name = before.methodName().stringValue();
                var expected = new ArrayList<String>();
                for (var attribute : before.attributes()) {
                    expected.add(attribute.attributeName().stringValue());
                }
                assertEquals(expected, attributeNames(woven, name),
                        "Attributed." + name + " came out of the weaver with different attributes");
            }
        }
    }

    @Nested
    @DisplayName("on a class that merely writes to one")
    class OnAWriter {

        @Test
        @DisplayName("the same four survive, because the rebuild was the same rebuild")
        void aWriterKeepsThemToo() {
            // Attributed.Helper is not a model and carries no marker at all. It is
            // rewritten only because one of its methods assigns to a @Bind field
            // next door (ADR-0134) -- and that rewrite used to cost it every
            // attribute on every method, model or not.
            var woven = wovenHelper();

            assertTrue(method(woven, "reset").findAttribute(Attributes.signature()).isPresent(),
                    "Helper.reset() lost its Signature");
            assertEquals(List.of("java/io/IOException"),
                    method(woven, "reset").findAttribute(Attributes.exceptions())
                            .orElseThrow(() -> new AssertionError("Helper.reset() lost its throws clause"))
                            .exceptions().stream().map(e -> e.asInternalName()).toList());
            assertEquals(List.of("token"),
                    method(woven, "reset").findAttribute(Attributes.methodParameters())
                            .orElseThrow(() -> new AssertionError("Helper.reset() lost its parameter names"))
                            .parameters().stream()
                            .map(p -> p.name().orElseThrow().stringValue()).toList());
        }

        @Test
        @DisplayName("and the write itself is still rewritten, so this is not a class the weaver skipped")
        void theWriteIsStillRewritten() {
            var calls = new ArrayList<String>();
            for (var element : method(wovenHelper(), "reset").code().orElseThrow()) {
                if (element instanceof InvokeInstruction invoke) {
                    calls.add(invoke.name().stringValue());
                }
            }

            assertTrue(calls.contains("goldberry$set$count"),
                    "the assignment to Attributed.count should have become a setter call: " + calls);
        }
    }
}
