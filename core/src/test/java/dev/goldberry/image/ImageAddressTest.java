package dev.goldberry.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.render.model.PhysicalRect;

/// An image's address: a path, and the `#xywh=` rectangle of it.
class ImageAddressTest {

    @Test
    @DisplayName("a path with no fragment is the whole picture")
    void whole() {
        var address = ImageAddress.parse("classpath:/ui/kit.png");

        assertEquals("classpath:/ui/kit.png", address.path());
        assertNull(address.region());
    }

    @Test
    @DisplayName("#xywh= is a rectangle in the picture's pixels")
    void region() {
        var address = ImageAddress.parse("classpath:/ui/kit.png#xywh=29,36,718,306");

        assertEquals("classpath:/ui/kit.png", address.path());
        assertEquals(new PhysicalRect(29, 36, 718, 306), address.region());
        assertEquals("classpath:/ui/kit.png#xywh=29,36,718,306", address.toString());
    }

    @Test
    @DisplayName("the pixel: unit may be written")
    void pixelUnit() {
        assertEquals(
                new PhysicalRect(1, 2, 3, 4),
                ImageAddress.parse("kit.png#xywh=pixel:1,2,3,4").region());
    }

    @Test
    @DisplayName("a # that is not a region is part of the path")
    void otherHash() {
        assertEquals("art/#1.png", ImageAddress.parse("art/#1.png").path());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "kit.png#xywh=",
                "kit.png#xywh=1,2,3",
                "kit.png#xywh=1,2,3,4,5",
                "kit.png#xywh=a,2,3,4",
                "kit.png#xywh=1.5,2,3,4",
                "kit.png#xywh=-1,2,3,4",
                "kit.png#xywh=1,2,0,4",
                "kit.png#xywh=percent:1,2,3,4",
                "#xywh=1,2,3,4"
            })
    @DisplayName("a fragment whose numbers cannot be read is refused")
    void malformed(String src) {
        assertThrows(IllegalArgumentException.class, () -> ImageAddress.parse(src));
    }

    @Test
    @DisplayName("the 2x variant sits beside the 1x one, with the region doubled")
    void density() {
        var address = ImageAddress.parse("classpath:/ui/kit.png#xywh=29,36,718,306");

        var doubled = address.atDensity(2);

        assertEquals("classpath:/ui/kit@2x.png", doubled.path());
        assertEquals(new PhysicalRect(58, 72, 1436, 612), doubled.region());
        assertEquals(
                "ui.v2/panel@2x", ImageAddress.parse("ui.v2/panel").atDensity(2).path());
        assertEquals(address, address.atDensity(1));
    }
}
