package io.github.digitalsmile.goldberry.widgets.core.image;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.render.model.PhysicalRect;

/// How an image fills a box whose shape is not its own — §1's
/// `contain | cover | fill | none`, CSS's `object-fit` by another name.
///
/// Each mode answers one question: given an image of some pixels and a natural
/// size, and a box, which pixels are taken and where do they go. The answer is a
/// [Placement], a crop in the image's own pixels and a rectangle in the box's
/// logical units, which is exactly the pair
/// [io.github.digitalsmile.goldberry.paint.Frame] draws. The image is always
/// centred, which is `object-position`'s default and the only position §1 names.
///
/// **Cover crops rather than clips.** The part of the image outside the box is
/// never drawn, so `cover` needs no clip on the box and no `overflow: hidden`
/// from the stylesheet, and cannot paint over a neighbour (ADR-0358).
public enum Fit {

    /// The whole image, as large as fits, letterboxed. The default: it is the one
    /// mode that neither distorts nor loses any of the picture.
    CONTAIN,

    /// The box filled, the image cropped to the box's shape.
    COVER,

    /// The box filled, the image stretched to its shape.
    FILL,

    /// The image at its natural size, centred, cropped if it is larger.
    NONE;

    /// Which pixels, and where.
    ///
    /// @param source the crop, in the image's pixels; never empty
    /// @param x      the left of the drawn rectangle, in the box's logical units
    /// @param y      the top of the drawn rectangle
    /// @param width  its width; positive
    /// @param height its height; positive
    public record Placement(PhysicalRect source, double x, double y, double width, double height) {}

    /// The mode `name` spells, or [#CONTAIN] for null.
    ///
    /// @throws IllegalArgumentException for a word that is not one of the four,
    ///         because a typo in `fit=` that silently meant `contain` is a picture
    ///         that looks almost right
    public static Fit named(@Nullable String name) {
        if (name == null) {
            return CONTAIN;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "contain" -> CONTAIN;
            case "cover" -> COVER;
            case "fill" -> FILL;
            case "none" -> NONE;
            default ->
                throw new IllegalArgumentException(
                        "fit is one of contain, cover, fill or none; \"" + name + "\" is not");
        };
    }

    /// Places an image in a box, or returns null when either has no area.
    ///
    /// @param pixelWidth    the image's width in pixels
    /// @param pixelHeight   its height in pixels
    /// @param naturalWidth  its natural width in logical units — pixels over the
    ///                      variant's scale
    /// @param naturalHeight its natural height in logical units
    /// @param boxWidth      the box's width in logical units
    /// @param boxHeight     the box's height in logical units
    public @Nullable Placement place(
            int pixelWidth,
            int pixelHeight,
            double naturalWidth,
            double naturalHeight,
            double boxWidth,
            double boxHeight) {
        if (pixelWidth <= 0 || pixelHeight <= 0 || !(naturalWidth > 0) || !(naturalHeight > 0)) {
            return null;
        }
        if (!(boxWidth > 0) || !(boxHeight > 0)) {
            return null;
        }
        var whole = new PhysicalRect(0, 0, pixelWidth, pixelHeight);
        return switch (this) {
            case FILL -> new Placement(whole, 0, 0, boxWidth, boxHeight);
            case CONTAIN -> {
                var scale = Math.min(boxWidth / naturalWidth, boxHeight / naturalHeight);
                var width = naturalWidth * scale;
                var height = naturalHeight * scale;
                yield new Placement(whole, (boxWidth - width) / 2, (boxHeight - height) / 2, width, height);
            }
            case COVER -> {
                var scale = Math.max(boxWidth / naturalWidth, boxHeight / naturalHeight);
                var source = centredCrop(
                        pixelWidth, pixelHeight, boxWidth / scale / naturalWidth, boxHeight / scale / naturalHeight);
                yield new Placement(source, 0, 0, boxWidth, boxHeight);
            }
            case NONE -> {
                var width = Math.min(naturalWidth, boxWidth);
                var height = Math.min(naturalHeight, boxHeight);
                var source = centredCrop(pixelWidth, pixelHeight, width / naturalWidth, height / naturalHeight);
                yield new Placement(source, (boxWidth - width) / 2, (boxHeight - height) / 2, width, height);
            }
        };
    }

    /// The centred crop that keeps `fractionX` and `fractionY` of the image, in
    /// whole pixels, at least one of each and never outside it.
    private static PhysicalRect centredCrop(int pixelWidth, int pixelHeight, double fractionX, double fractionY) {
        var width = clamp((int) Math.round(pixelWidth * Math.min(1, fractionX)), pixelWidth);
        var height = clamp((int) Math.round(pixelHeight * Math.min(1, fractionY)), pixelHeight);
        return new PhysicalRect((pixelWidth - width) / 2, (pixelHeight - height) / 2, width, height);
    }

    private static int clamp(int value, int limit) {
        return Math.max(1, Math.min(limit, value));
    }
}
