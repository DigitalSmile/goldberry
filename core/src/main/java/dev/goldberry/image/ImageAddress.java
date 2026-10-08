package dev.goldberry.image;

import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.render.model.PhysicalRect;

/// Where a picture is, as markup and a stylesheet write it: a path, and
/// optionally the rectangle of it to show.
///
/// ```
/// classpath:/ui/kit.png
/// classpath:/ui/kit.png#xywh=29,36,718,306
/// ```
///
/// The rectangle is the Media Fragments `xywh` form, in the picture's own
/// pixels: left, top, width and height, with an optional `pixel:` before them.
/// It is what lets one sheet of sprites stand for many pictures, each read out
/// of the sheet's single decode. Only `#xywh=` is a fragment; a `#` anywhere
/// else is part of the path, as it is in a file name.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#image).
///
/// @param path   the address without its fragment
/// @param region the rectangle to show, or null for the whole picture
public record ImageAddress(String path, @Nullable PhysicalRect region) {

    /// What starts a region fragment.
    private static final String FRAGMENT = "#xywh=";

    public ImageAddress {
        Objects.requireNonNull(path, "path");
        if (path.isEmpty()) {
            throw new IllegalArgumentException("an image address needs a path");
        }
        if (region != null && region.isEmpty()) {
            throw new IllegalArgumentException("a region needs a positive size, and " + region + " has none");
        }
    }

    /// Reads `src`, splitting off a `#xywh=` fragment when it has one.
    ///
    /// @throws IllegalArgumentException for a fragment that is not four
    ///         whole numbers, a negative origin or an empty size: a region whose
    ///         numbers cannot be read is a mistake in the document, and showing
    ///         the whole sheet instead would hide it
    public static ImageAddress parse(String src) {
        Objects.requireNonNull(src, "src");
        var at = src.toLowerCase(Locale.ROOT).lastIndexOf(FRAGMENT);
        if (at < 0) {
            return new ImageAddress(src, null);
        }
        var path = src.substring(0, at);
        var fragment = src.substring(at + FRAGMENT.length());
        if (fragment.toLowerCase(Locale.ROOT).startsWith("pixel:")) {
            fragment = fragment.substring("pixel:".length());
        }
        var parts = fragment.split(",", -1);
        if (parts.length != 4) {
            throw malformed(src);
        }
        var numbers = new int[4];
        for (var i = 0; i < 4; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                throw malformed(src);
            }
        }
        if (numbers[0] < 0 || numbers[1] < 0 || numbers[2] <= 0 || numbers[3] <= 0) {
            throw malformed(src);
        }
        return new ImageAddress(path, new PhysicalRect(numbers[0], numbers[1], numbers[2], numbers[3]));
    }

    /// The same address for a sheet drawn `density` times larger — its `@2x`
    /// variant — with the region scaled to match.
    ///
    /// `ui/kit.png` becomes `ui/kit@2x.png`: the suffix goes before the last
    /// extension, or at the end when there is none. A region is written in the
    /// 1x sheet's pixels, so the 2x sheet's is twice it.
    public ImageAddress atDensity(int density) {
        if (density < 1) {
            throw new IllegalArgumentException("a density is 1 or more, not " + density);
        }
        if (density == 1) {
            return this;
        }
        var slash = path.lastIndexOf('/');
        var dot = path.lastIndexOf('.');
        var suffix = "@" + density + "x";
        var variant = dot > slash + 1 ? path.substring(0, dot) + suffix + path.substring(dot) : path + suffix;
        var scaled = region == null
                ? null
                : new PhysicalRect(
                        region.x() * density,
                        region.y() * density,
                        region.width() * density,
                        region.height() * density);
        return new ImageAddress(variant, scaled);
    }

    /// This address as it would be written, fragment and all.
    @Override
    public String toString() {
        return region == null
                ? path
                : path + FRAGMENT + region.x() + "," + region.y() + "," + region.width() + "," + region.height();
    }

    private static IllegalArgumentException malformed(String src) {
        return new IllegalArgumentException("\"" + src + "\" has a region that is not #xywh=x,y,width,height in whole"
                + " pixels, with a positive width and height");
    }
}
