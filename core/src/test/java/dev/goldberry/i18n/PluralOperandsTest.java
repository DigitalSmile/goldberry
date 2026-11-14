package dev.goldberry.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("a number's plural operands")
class PluralOperandsTest {

    @Test
    @DisplayName("count visible fraction digits with and without their trailing zeros")
    void fractions() {
        assertEquals(new PluralOperands(1.5, 1, 2, 1, 50, 5, 0), PluralOperands.parse("1.50"));
        assertEquals(new PluralOperands(1.5, 1, 2, 1, 50, 5, 0), PluralOperands.of(new BigDecimal("1.50")));
        assertEquals(new PluralOperands(3, 3, 0, 0, 0, 0, 0), PluralOperands.of(-3), "the absolute value");
    }

    @Test
    @DisplayName("read a compact number's exponent as an operand of its own")
    void compact() {
        var number = PluralOperands.parse("1.2c6");
        assertEquals(1_200_000, number.n());
        assertEquals(1_200_000, number.i());
        assertEquals(0, number.v());
        assertEquals(6, number.e());
        assertEquals(PluralOperands.parse("1.2c6"), PluralOperands.parse("1.2e6"));
    }

    @Test
    @DisplayName("refuse what is not a number")
    void refuses() {
        assertThrows(NumberFormatException.class, () -> PluralOperands.parse("one"));
    }
}
