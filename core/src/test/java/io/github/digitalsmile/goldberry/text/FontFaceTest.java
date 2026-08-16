package io.github.digitalsmile.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FontFaceTest {

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("several sizes share one face")
    void sizesShareAFace() {
        try (var face = FontFace.bundled(BundledFont.UI);
                var small = Font.on(face, 12);
                var medium = Font.on(face, 16);
                var large = Font.on(face, 32)) {

            assertSame(face, small.face());
            assertSame(face, medium.face());
            assertSame(face, large.face());

            // The design grid is a property of the face, so all three agree on
            // it without any of them having parsed the file.
            assertEquals(face.unitsPerEm(), small.unitsPerEm());
            assertEquals(face.unitsPerEm(), large.unitsPerEm());
        }
    }

    @Test
    @DisplayName("closing one size leaves the others working")
    void closingOneSizeLeavesTheFaceAlone() {
        // The failure this guards against is the one the old code could not
        // have: a Font that owned the face would take the shaper down with it,
        // and the surviving Font would shape against freed memory.
        try (var face = FontFace.bundled(BundledFont.UI)) {
            var survivor = Font.on(face, 16);
            try {
                var doomed = Font.on(face, 24);
                doomed.close();

                assertTrue(doomed.isClosed());
                assertTrue(!face.isClosed(), "the shared face was closed with one of its fonts");

                // And the survivor still shapes -- against the same face.
                var run = survivor.shape("Goldberry");
                assertTrue(run.length() > 0, "the surviving font shaped nothing");
            } finally {
                survivor.close();
            }
        }
    }

    @Test
    @DisplayName("two sizes over one face measure differently but shape the same")
    void sharedFaceStillGivesDifferentMetrics() {
        try (var face = FontFace.bundled(BundledFont.UI);
                var small = Font.on(face, 12);
                var large = Font.on(face, 36)) {

            // The size is on Blend2D's font alone (ADR-0034), so the shaping
            // result -- design units -- is identical, and only the metrics move.
            assertEquals(small.shape("Wg").length(), large.shape("Wg").length());
            assertTrue(large.lineHeight() > small.lineHeight(),
                    () -> large.lineHeight() + " should exceed " + small.lineHeight());
            assertNotEquals(small.widthOf("Goldberry"), large.widthOf("Goldberry"));
        }
    }

    @Test
    @DisplayName("a font that parsed its own face still closes it")
    void ownedFaceIsClosedWithTheFont() {
        var font = Font.bundled(BundledFont.UI, 16);
        var face = font.face();
        font.close();

        assertTrue(face.isClosed(), "a font that made its own face must close it");
    }

    @Test
    @DisplayName("a closed face refuses to hand out anything")
    void closedFaceIsRefused() {
        var face = FontFace.bundled(BundledFont.UI);
        face.close();

        assertThrows(IllegalStateException.class, face::unitsPerEm);
        assertThrows(IllegalStateException.class, () -> Font.on(face, 16));
    }

    @Test
    @DisplayName("closing a face twice is harmless")
    void closingTwiceIsIdempotent() {
        var face = FontFace.bundled(BundledFont.CODE);
        face.close();
        face.close();

        assertTrue(face.isClosed());
    }

    @Test
    @DisplayName("the face names the family it came from")
    void faceCarriesItsName() {
        try (var face = FontFace.bundled(BundledFont.UI)) {
            assertEquals(BundledFont.UI.family(), face.name());
            assertTrue(face.toString().contains(BundledFont.UI.family()), face::toString);
        }
    }
}
