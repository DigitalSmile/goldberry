package io.github.digitalsmile.goldberry.widgets.core.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// §1's "DPI-aware (picks raster scale by physical pixels)" ([ADR-0358]).
class VariantTest {

    private static final ClassLoader LOADER = VariantTest.class.getClassLoader();

    private static Variant at(double scale) {
        return new Variant(scale, ImageSource.file(Path.of("logo@" + scale + "x.png")));
    }

    private final List<Variant> three = List.of(at(2), at(1), at(3));

    @Test
    @DisplayName("the smallest raster at least as dense as the display, so no pixel is invented")
    void smallestSufficient() {
        assertEquals(at(1), Variant.pick(three, 1));
        assertEquals(at(2), Variant.pick(three, 1.25));
        assertEquals(at(2), Variant.pick(three, 2));
        assertEquals(at(3), Variant.pick(three, 2.5));
    }

    @Test
    @DisplayName("the densest there is, on a display denser than all of them")
    void densestWhenNoneSuffice() {
        assertEquals(at(3), Variant.pick(three, 4));
    }

    @Test
    @DisplayName("a srcset reads as HTML's, a bare path being 1x")
    void srcset() {
        var variants = Variant.parse("logo.png, classpath:logo@2x.png 2x ,  logo@1.5x.png 1.5x", LOADER);

        assertEquals(
                List.of(1.0, 2.0, 1.5), variants.stream().map(Variant::scale).toList());
        assertInstanceOf(ImageSource.File.class, variants.get(0).source());
        var resource =
                assertInstanceOf(ImageSource.Resource.class, variants.get(1).source());
        assertEquals("logo@2x.png", resource.name());
    }

    @Test
    @DisplayName("a srcset with a width descriptor, or two images for one scale, is refused")
    void refusals() {
        assertThrows(IllegalArgumentException.class, () -> Variant.parse("a.png 300w", LOADER));
        assertThrows(IllegalArgumentException.class, () -> Variant.parse("a.png 2x, b.png 2x", LOADER));
        assertThrows(IllegalArgumentException.class, () -> Variant.parse("a.png twox", LOADER));
        assertThrows(IllegalArgumentException.class, () -> new Variant(0, ImageSource.file(Path.of("a.png"))));
    }
}
