package dev.goldberry.vector.thorvg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.vector.thorvg.calls.CanvasCalls;
import dev.goldberry.vector.thorvg.calls.EngineCalls;
import dev.goldberry.vector.thorvg.calls.PictureCalls;
import dev.goldberry.vector.thorvg.calls.VectorCalls;

/// The export list and the bindings name the same functions: a symbol the list
/// has and nothing binds is surface for nobody, and one a binding looks up and
/// the list lacks fails at load.
@DisplayName("The export list")
class ExportListTest {

    private static final Path LIST = Path.of("src/main/cmake/exports/goldberry-vector.symbols");

    private static List<String> exported() throws IOException {
        return Files.readAllLines(LIST).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    @Test
    @DisplayName("is exactly what the bindings look up")
    void matchesTheBindings() throws IOException {
        var bound = Stream.of(VectorCalls.SYMBOLS, EngineCalls.SYMBOLS, CanvasCalls.SYMBOLS, PictureCalls.SYMBOLS)
                .flatMap(List::stream)
                .toList();
        assertEquals(new TreeSet<>(exported()), new TreeSet<>(bound));
        assertEquals(bound.size(), new TreeSet<>(bound).size(), "a symbol is bound twice");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(classes = {VectorCalls.class, EngineCalls.class, CanvasCalls.class, PictureCalls.class})
    @DisplayName("names one symbol per holder")
    void oneSymbolPerHolder(Class<? extends Record> calls) throws ReflectiveOperationException {
        var symbols = (List<?>) calls.getField("SYMBOLS").get(null);
        assertEquals(calls.getRecordComponents().length, symbols.size(), calls.getSimpleName());
    }
}
