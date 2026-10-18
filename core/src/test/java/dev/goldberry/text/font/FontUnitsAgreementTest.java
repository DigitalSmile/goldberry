package dev.goldberry.text.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The agreement between the two text libraries, checked against every class
/// `:core` compiles rather than held by convention.
///
/// HarfBuzz reports positions in whatever scale its font was set to, and Blend2D
/// multiplies them by `size / units-per-em`. Setting a size on both applies it
/// twice and draws text off the window with every call succeeding. The rule is
/// that the shaping font is never scaled, so its positions stay in design units.
///
/// Outside `:core` the packages are not exported, so nothing else can build
/// either font. Inside it, this reads the bytecode: no class calls
/// `ShapedFont.setScale`, and the fonts are built in exactly the places that
/// keep the rule.
class FontUnitsAgreementTest {

    private static final String SHAPED_FONT = "dev/goldberry/natives/harfbuzz/ShapedFont";

    private static final String BLEND_FONT = "dev/goldberry/natives/blend2d/BlendFont";

    /// A call, as `owner.name`, and the class it appears in.
    private record Call(String caller, String callee) {}

    private static List<Call> calls;

    @BeforeAll
    static void readCore() throws IOException, URISyntaxException {
        var location = Path.of(
                Font.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        calls = new ArrayList<>();
        if (Files.isDirectory(location)) {
            read(location);
        } else {
            try (var jar = FileSystems.newFileSystem(location)) {
                read(jar.getPath("/"));
            }
        }
    }

    private static void read(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (var file :
                    files.filter(path -> path.toString().endsWith(".class")).toList()) {
                record(ClassFile.of().parse(bytes(file)));
            }
        }
    }

    private static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void record(ClassModel model) {
        var caller = model.thisClass().asInternalName();
        for (var method : model.methods()) {
            method.findAttribute(Attributes.code()).ifPresent(code -> invocations(caller, code));
        }
    }

    private static void invocations(String caller, CodeModel code) {
        for (var element : code) {
            if (element instanceof InvokeInstruction invoke) {
                var owner = invoke.owner().asInternalName();
                if (owner.equals(SHAPED_FONT) || owner.equals(BLEND_FONT)) {
                    calls.add(new Call(caller, owner + "." + invoke.name().stringValue()));
                }
            }
        }
    }

    /// The classes calling `callee`, without their nested-class suffixes.
    private static Set<String> callersOf(String callee) {
        var out = new TreeSet<String>();
        for (var call : calls) {
            if (call.callee().equals(callee)) {
                var name = call.caller();
                var nested = name.indexOf('$');
                out.add(nested < 0 ? name : name.substring(0, nested));
            }
        }
        return out;
    }

    @Test
    @DisplayName("the classes were read, and they do call both libraries")
    void somethingWasRead() {
        assertTrue(calls.size() > 2, () -> "too few calls were found to say anything: " + calls);
    }

    @Test
    @DisplayName("nothing in :core scales the shaping font")
    void theShaperIsNeverScaled() {
        assertEquals(Set.of(), callersOf(SHAPED_FONT + ".setScale"));
    }

    @Test
    @DisplayName("each font is built in one place, beside the other half of the agreement")
    void eachFontIsBuiltInOnePlace() {
        var expected = Map.of(
                SHAPED_FONT + ".fromBytes", Set.of("dev/goldberry/text/font/FontFace"),
                BLEND_FONT + ".on", Set.of("dev/goldberry/paint/GlyphPen"),
                BLEND_FONT + ".fromBytes", Set.<String>of());
        expected.forEach((callee, callers) -> assertEquals(callers, callersOf(callee), callee));
    }
}
