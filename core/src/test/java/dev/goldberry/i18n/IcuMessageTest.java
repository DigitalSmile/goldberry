package dev.goldberry.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("an ICU message")
class IcuMessageTest {

    private static final Locale RUSSIAN = Locale.forLanguageTag("ru");

    private static final IcuMessage FILES =
            IcuMessage.parse("{count, plural, one {# файл} few {# файла} many {# файлов} other {# файла}}");

    @Test
    @DisplayName("chooses a plural form by the language's own rules")
    void russianPlurals() {
        assertEquals("1 файл", FILES.format(RUSSIAN, Map.of("count", 1)));
        assertEquals("3 файла", FILES.format(RUSSIAN, Map.of("count", 3)));
        assertEquals("5 файлов", FILES.format(RUSSIAN, Map.of("count", 5)));
        assertEquals("21 файл", FILES.format(RUSSIAN, Map.of("count", 21)));
        assertEquals("1,5 файла", FILES.format(RUSSIAN, Map.of("count", new BigDecimal("1.5"))));
    }

    @Test
    @DisplayName("takes an exact value first, and an offset from the rest")
    void exactAndOffset() {
        var message = IcuMessage.parse("{n, plural, offset:1 =0 {nobody} =1 {{host}} one {{host} and # other} "
                + "other {{host} and # others}}");
        assertEquals("nobody", message.format(Locale.ENGLISH, Map.of("n", 0, "host", "Ann")));
        assertEquals("Ann", message.format(Locale.ENGLISH, Map.of("n", 1, "host", "Ann")));
        assertEquals("Ann and 1 other", message.format(Locale.ENGLISH, Map.of("n", 2, "host", "Ann")));
        assertEquals("Ann and 4 others", message.format(Locale.ENGLISH, Map.of("n", 5, "host", "Ann")));
    }

    @Test
    @DisplayName("orders with selectordinal, and chooses words with select")
    void ordinalsAndSelect() {
        var place = IcuMessage.parse("{n, selectordinal, one {#st} two {#nd} few {#rd} other {#th}}");
        assertEquals("1st", place.format(Locale.ENGLISH, Map.of("n", 1)));
        assertEquals("22nd", place.format(Locale.ENGLISH, Map.of("n", 22)));
        assertEquals("13th", place.format(Locale.ENGLISH, Map.of("n", 13)));
        var reply = IcuMessage.parse("{who, select, her {She replied} him {He replied} other {They replied}}");
        assertEquals("She replied", reply.format(Locale.ENGLISH, Map.of("who", "her")));
        assertEquals("They replied", reply.format(Locale.ENGLISH, Map.of("who", "team")));
    }

    @Test
    @DisplayName("formats numbers for the locale, keeps a missing argument visible, and quotes with apostrophes")
    void details() {
        var grouped = IcuMessage.format("{n}", RUSSIAN, Map.of("n", 1_234_567));
        assertEquals("1 234 567", grouped.replaceAll("[\u00A0\u202F]", " "), "grouped by no-break spaces");
        assertEquals("Hi {name}!", IcuMessage.format("Hi {name}!", Locale.ENGLISH, Map.of()));
        assertEquals("It's {literal}", IcuMessage.format("It''s '{literal}'", Locale.ENGLISH, Map.of()));
        assertEquals("# is a hash", IcuMessage.format("# is a hash", Locale.ENGLISH, Map.of()));
    }

    @Test
    @DisplayName("refuses a message that breaks ICU's rules when it is parsed")
    void refusals() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> IcuMessage.parse("{n, plural, one {x}}"))
                .getMessage()
                .contains("other"));
        assertThrows(IllegalArgumentException.class, () -> IcuMessage.parse("{n, date}"));
        assertThrows(IllegalArgumentException.class, () -> IcuMessage.parse("{n, plural, single {x} other {y}}"));
        assertThrows(IllegalArgumentException.class, () -> IcuMessage.parse("unbalanced }"));
        assertThrows(IllegalArgumentException.class, () -> IcuMessage.parse("{n"));
    }
}
