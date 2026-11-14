package dev.goldberry.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("a GPU power preference")
class GpuPowerTest {

    @Test
    @DisplayName("parses low and high in any case and refuses anything else")
    void parses() {
        assertEquals(Optional.of(GpuPower.LOW), GpuPower.parse("low"));
        assertEquals(Optional.of(GpuPower.HIGH), GpuPower.parse(" High "));
        assertEquals(Optional.empty(), GpuPower.parse("discrete"));
        assertEquals(Optional.empty(), GpuPower.parse(""));
        assertEquals(Optional.empty(), GpuPower.parse(null));
    }
}
