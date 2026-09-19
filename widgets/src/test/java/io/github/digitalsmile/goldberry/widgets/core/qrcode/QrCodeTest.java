package io.github.digitalsmile.goldberry.widgets.core.qrcode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.qr.Level;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// §1's `qr-code`: the value, what a document says to make one, and what it
/// tells a reader.
///
/// The picture is [QrModulesTest]'s and the encoding is `:core`'s. What is left
/// is the widget's own contract, and the half of it that is easy to get wrong is
/// at the bottom: a widget is described again on every frame, and the dialog
/// this exists for is rebuilt on every keystroke.
class QrCodeTest {

    @BeforeEach
    void setUp() {
        QrCache.clear();
    }

    private static QrCode inflate(String markup) {
        var node = KdlParser.parse(markup).getFirst();
        return (QrCode) QrCode.inflate(node, List.of(), Wiring.none());
    }

    @Test
    @DisplayName("a code takes level M and the standard quiet zone unless told otherwise")
    void defaultsAreTheStandards() {
        var code = new QrCode("tg://login?token=abc");

        assertEquals(Level.M, code.level());
        assertEquals(4, code.quietZone());
    }

    @Test
    @DisplayName("a payload no version holds is refused where the widget is built")
    void anOversizedPayloadIsRefused() {
        var tooLong = "a".repeat(3000);

        var refusal = assertThrows(IllegalArgumentException.class, () -> new QrCode(tooLong));

        assertTrue(refusal.getMessage().contains("does not fit"), refusal.getMessage());
    }

    @Test
    @DisplayName("a negative quiet zone is refused")
    void aNegativeQuietZoneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new QrCode("x").quietZone(-1));
    }

    @Test
    @DisplayName("a higher level is a bigger code for the same payload")
    void aHigherLevelIsABiggerCode() {
        var payload = "https://goldberry.example/invite/9f2c1b";

        assertTrue(new QrCode(payload).level(Level.H).matrix().size()
                >= new QrCode(payload).level(Level.L).matrix().size());
    }

    @Test
    @DisplayName("a document builds a code from value, level and quiet-zone")
    void markupCarriesTheThreeAttributes() {
        var code = inflate("qr-code value=\"HELLO WORLD\" level=\"Q\" quiet-zone=2 id=\"sign-in\"");

        assertEquals("HELLO WORLD", code.payload());
        assertEquals(Level.Q, code.level());
        assertEquals(2, code.quietZone());
        assertEquals("sign-in", code.id());
    }

    @Test
    @DisplayName("a level a document misspells falls back to M rather than failing")
    void anUnknownLevelFallsBack() {
        // A document is reloaded on every keystroke while it is being written,
        // and `level="Q` half typed should not take the window down.
        assertEquals(Level.M, inflate("qr-code value=\"x\" level=\"whatever\"").level());
        assertEquals(Level.M, inflate("qr-code value=\"x\"").level());
        // Case is not part of the answer: `level="h"` is level H.
        assertEquals(Level.H, inflate("qr-code value=\"x\" level=\"h\"").level());
    }

    @Test
    @DisplayName("a document with no value builds an empty code rather than nothing")
    void aMissingValueIsAnEmptyCode() {
        assertEquals("", inflate("qr-code").payload());
    }

    @Test
    @DisplayName("a code is a figure named by the application, and never by its payload")
    void thePayloadIsNotTheName() {
        var secret = "tg://login?token=this-is-a-credential";
        var named = new QrCode(secret).withAttributes(Attributes.NONE.name("QR code to sign in to Telegram"));

        assertEquals(Role.FIGURE, named.role());
        assertEquals("QR code to sign in to Telegram", named.accessibleName());
        assertNull(new QrCode(secret).accessibleName(), "an unnamed code is unnamed, not named by its token");
    }

    @Test
    @DisplayName("rebuilding with the same payload does not encode again")
    void rebuildingIsFree() {
        var payload = "tg://login?token=AQAAAB8AAAAmaW1wb3J0YW50";

        var first = new QrCode(payload);
        assertEquals(1, QrCache.encodings(), "the first build encodes");

        // Sixty rebuilds is one second of a dialog that rebuilds on every frame.
        for (var i = 0; i < 60; i++) {
            var again = new QrCode(payload);
            assertSame(first.matrix(), again.matrix(), "rebuild " + i + " re-encoded");
        }

        assertEquals(1, QrCache.encodings(), "the encoder ran more than once for one payload");
    }

    @Test
    @DisplayName("rebuilding with a renewed token encodes exactly once more")
    void aNewPayloadEncodesOnce() {
        var first = new QrCode("tg://login?token=one");
        assertEquals(1, QrCache.encodings());

        var second = new QrCode("tg://login?token=two");

        assertEquals(2, QrCache.encodings());
        assertNotSame(first.matrix(), second.matrix());
        // And the first is still there, because a token is renewed about every
        // thirty seconds and the old picture may still be on screen.
        assertEquals(2, QrCache.encodings(), "asking for the first one back should not re-encode it");
        assertSame(first.matrix(), new QrCode("tg://login?token=one").matrix());
    }

    @Test
    @DisplayName("the same payload at another level is another code")
    void theLevelIsPartOfTheKey() {
        var low = new QrCode("same payload", Level.L, 4, Attributes.NONE);
        var high = new QrCode("same payload", Level.H, 4, Attributes.NONE);

        assertNotSame(low.matrix(), high.matrix());
        assertEquals(2, QrCache.encodings());
    }

    @Test
    @DisplayName("more codes than the cache holds still answer, by encoding again")
    void theCacheIsBounded() {
        for (var i = 0; i < 40; i++) {
            var code = new QrCode("payload " + i);
            assertEquals(i + 1, QrCache.encodings());
            assertTrue(code.matrix().size() >= 21);
        }

        // The first is long gone, so asking for it again is a fresh encode --
        // which is the right answer: a bounded cache that never missed would be
        // a leak.
        var revived = new QrCode("payload 0");
        assertEquals(41, QrCache.encodings());
        assertTrue(revived.matrix().size() >= 21);
    }
}
