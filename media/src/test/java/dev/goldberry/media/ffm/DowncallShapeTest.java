package dev.goldberry.media.ffm;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.FunctionDescriptor;
import java.util.OptionalInt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A downcall's descriptor and where its `...` begins, which a native image
/// registers together.
@DisplayName("DowncallShape")
class DowncallShapeTest {

    private static final FunctionDescriptor CTL = FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT);

    @Test
    @DisplayName("a variadic call and a plain one of the same descriptor are two shapes")
    void variadicIsItsOwnShape() {
        assertNotEquals(DowncallShape.of(CTL), DowncallShape.variadic(CTL, 2));
        assertEquals(DowncallShape.variadic(CTL, 2), new DowncallShape(CTL, OptionalInt.of(2)));
        assertEquals(OptionalInt.empty(), DowncallShape.of(CTL).firstVariadic());
    }

    @Test
    @DisplayName("the variadic arguments may begin past the last one, and nowhere else outside them")
    void withinTheArguments() {
        // opus_decoder_ctl(st, OPUS_RESET_STATE) passes no variadic argument.
        assertEquals(OptionalInt.of(2), DowncallShape.variadic(CTL, 2).firstVariadic());
        assertThrows(IllegalArgumentException.class, () -> DowncallShape.variadic(CTL, 3));
        assertThrows(IllegalArgumentException.class, () -> DowncallShape.variadic(CTL, -1));
    }
}
