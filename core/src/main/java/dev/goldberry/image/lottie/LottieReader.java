package dev.goldberry.image.lottie;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.jspecify.annotations.Nullable;

import dev.goldberry.image.ImageDecodeException;
import dev.goldberry.image.lottie.Json.Arr;
import dev.goldberry.image.lottie.Json.Bool;
import dev.goldberry.image.lottie.Json.Num;
import dev.goldberry.image.lottie.Json.Obj;
import dev.goldberry.image.lottie.Json.Str;
import dev.goldberry.image.lottie.Json.Value;
import dev.goldberry.paint.stroke.Cap;
import dev.goldberry.paint.stroke.Join;

/// Reads a Lottie document, gzipped (`tgs`) or plain, into a [Composition].
///
/// ## What is refused, and what is passed over
///
/// Two things are **refused**, with an [ImageDecodeException] that names
/// them: an **expression**, which is JavaScript the document expects its player
/// to run, and an **image layer**, which is a picture rather than a
/// description. Telegram forbids both in a sticker, and drawing the document
/// without them would draw something its author never made.
///
/// Everything else this reader does not know is **passed over** and the rest
/// is drawn: a text layer, a layer effect, a merge, a repeater, rounded
/// corners, a shape type that did not exist when this was written. A picture
/// with one ornament missing is still the picture, and a reader that refused
/// whatever it had not met would refuse next year's exporter.
///
/// Bytes that are not JSON, or JSON that is not a Lottie document, are an
/// [ImageDecodeException] too.
final class LottieReader {

    /// How large a gzipped document may grow when it is inflated. A sticker is at
    /// most 64 KB packed and a few hundred unpacked; this is room for any
    /// honest file and a stop for a compression bomb.
    static final int MAX_INFLATED = 16 * 1024 * 1024;

    private LottieReader() {}

    /// Reads `bytes`, from its position to its limit, which are left as they
    /// were.
    ///
    /// @throws ImageDecodeException when the bytes are not a Lottie document
    ///         this can draw
    static Composition read(ByteBuffer bytes) {
        var copy = bytes.duplicate();
        var raw = new byte[copy.remaining()];
        copy.get(raw);
        var text = text(isGzip(raw) ? inflate(raw) : raw);
        Value root;
        try {
            root = Json.parse(text);
        } catch (Json.JsonException e) {
            throw new ImageDecodeException("a Lottie document is JSON, and this is " + e.getMessage(), e);
        }
        if (!(root instanceof Obj document)) {
            throw new ImageDecodeException("a Lottie document is a JSON object, and this is not one");
        }
        try {
            return composition(document);
        } catch (IllegalArgumentException | ClassCastException | IndexOutOfBoundsException e) {
            throw new ImageDecodeException("this Lottie document could not be read: " + e.getMessage(), e);
        }
    }

    /// Whether `bytes` start with gzip's magic number.
    static boolean isGzip(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B;
    }

    private static byte[] inflate(byte[] packed) {
        try (InputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(packed))) {
            var out = new ByteArrayOutputStream(packed.length * 8);
            var chunk = new byte[16 * 1024];
            int read;
            while ((read = in.read(chunk)) > 0) {
                if (out.size() + read > MAX_INFLATED) {
                    throw new ImageDecodeException(
                            "a tgs that inflates past " + MAX_INFLATED / (1024 * 1024) + " MB is not a sticker");
                }
                out.write(chunk, 0, read);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new ImageDecodeException("these bytes start like gzip and do not inflate: " + e.getMessage(), e);
        }
    }

    private static String text(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new ImageDecodeException("a Lottie document is UTF-8 text, and these bytes are not", e);
        }
    }

    private static Composition composition(Obj document) {
        var width = number(document, "w", -1);
        var height = number(document, "h", -1);
        if (!(width > 0) || !(height > 0)) {
            throw new ImageDecodeException("a Lottie document has a positive w and h, and this one does not");
        }
        var frameRate = number(document, "fr", -1);
        if (!(frameRate > 0)) {
            throw new ImageDecodeException("a Lottie document has a positive frame rate, and this one does not");
        }
        var inPoint = number(document, "ip", 0);
        var outPoint = number(document, "op", -1);
        if (!(outPoint > inPoint)) {
            throw new ImageDecodeException(
                    "a Lottie document ends after it begins, and this one runs " + inPoint + " to " + outPoint);
        }
        var precomps = new HashMap<String, List<Layer>>();
        if (document.get("assets") instanceof Arr assets) {
            for (var asset : assets.items()) {
                if (asset instanceof Obj object
                        && object.get("id") instanceof Str(var id)
                        && object.get("layers") instanceof Arr layers) {
                    precomps.put(id, layers(layers));
                }
            }
        }
        if (!(document.get("layers") instanceof Arr layers)) {
            throw new ImageDecodeException("a Lottie document has layers, and this one has none");
        }
        return new Composition(width, height, frameRate, inPoint, outPoint, layers(layers), precomps);
    }

    private static List<Layer> layers(Arr array) {
        var layers = new ArrayList<Layer>();
        for (var item : array.items()) {
            if (item instanceof Obj object) {
                layers.add(layer(object));
            }
        }
        return layers;
    }

    private static Layer layer(Obj object) {
        var type = (int) number(object, "ty", -1);
        if (type == 2) {
            throw new ImageDecodeException("this Lottie document draws a picture (an image layer), and a vector"
                    + " animation is drawn from its own shapes; Telegram refuses such a sticker too");
        }
        // A hidden layer draws nothing and is kept: a child may still ride on
        // its transform, and a hidden matte is still a matte.
        var hidden = bool(object, "hd") && !bool(object, "td");
        Layer.Content content = hidden
                ? new Layer.Nothing()
                : switch (type) {
                    case 0 -> {
                        if (!(object.get("refId") instanceof Str(var reference))) {
                            throw new IllegalArgumentException("a precomp layer names its composition");
                        }
                        var remap = object.get("tm") instanceof Obj tm ? property(tm) : null;
                        yield new Layer.Precomp(reference, number(object, "w", 0), number(object, "h", 0), remap);
                    }
                    case 1 ->
                        new Layer.Solid(
                                solidColour(object.get("sc")), number(object, "sw", 0), number(object, "sh", 0));
                    case 4 -> new Layer.Shapes(object.get("shapes") instanceof Arr shapes ? shapes(shapes) : List.of());
                    // A null layer, and the kinds passed over: text, audio,
                    // cameras, data. Kept as layers so a child can still ride on
                    // their transform.
                    default -> new Layer.Nothing();
                };
        var transform = object.get("ks") instanceof Obj ks ? transform(ks) : Transform.NONE;
        var parent = object.get("parent") instanceof Num(var p) ? (Integer) (int) p : null;
        var matteIndex = object.get("tp") instanceof Num(var tp) ? (Integer) (int) tp : null;
        var matte = switch ((int) number(object, "tt", 0)) {
            case 1 -> Layer.Matte.ALPHA;
            case 2 -> Layer.Matte.ALPHA_INVERTED;
            case 3 -> Layer.Matte.LUMA;
            case 4 -> Layer.Matte.LUMA_INVERTED;
            default -> Layer.Matte.NONE;
        };
        var masks = new ArrayList<Layer.Mask>();
        if (object.get("masksProperties") instanceof Arr list) {
            for (var item : list.items()) {
                if (item instanceof Obj mask && mask.get("pt") instanceof Obj pt) {
                    masks.add(new Layer.Mask(
                            maskMode(mask.get("mode")),
                            bool(mask, "inv"),
                            shapeProperty(pt),
                            mask.get("o") instanceof Obj o ? property(o) : Property.of(100)));
                }
            }
        }
        var stretch = number(object, "sr", 1);
        return new Layer(
                (int) number(object, "ind", Integer.MIN_VALUE),
                parent,
                content,
                transform,
                number(object, "ip", 0),
                number(object, "op", Double.MAX_VALUE),
                number(object, "st", 0),
                stretch > 0 ? stretch : 1,
                matte,
                matteIndex,
                bool(object, "td"),
                masks);
    }

    private static Layer.Mask.Mode maskMode(@Nullable Value value) {
        if (!(value instanceof Str(var mode))) {
            return Layer.Mask.Mode.ADD;
        }
        return switch (mode) {
            case "n" -> Layer.Mask.Mode.NONE;
            case "s" -> Layer.Mask.Mode.SUBTRACT;
            case "i" -> Layer.Mask.Mode.INTERSECT;
            case "l" -> Layer.Mask.Mode.LIGHTEN;
            case "d" -> Layer.Mask.Mode.DARKEN;
            case "f" -> Layer.Mask.Mode.DIFFERENCE;
            default -> Layer.Mask.Mode.ADD;
        };
    }

    /// A solid layer's `#rrggbb`, opaque.
    private static int solidColour(@Nullable Value value) {
        if (value instanceof Str(var hex) && hex.startsWith("#") && hex.length() == 7) {
            try {
                return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("a solid's colour is #rrggbb, and \"" + hex + "\" is not", e);
            }
        }
        return 0xFF000000;
    }

    private static List<Shape> shapes(Arr array) {
        var shapes = new ArrayList<Shape>();
        for (var item : array.items()) {
            if (item instanceof Obj object && !bool(object, "hd")) {
                var shape = shape(object);
                if (shape != null) {
                    shapes.add(shape);
                }
            }
        }
        return shapes;
    }

    private static @Nullable Shape shape(Obj object) {
        if (!(object.get("ty") instanceof Str(var type))) {
            return null;
        }
        var reversed = number(object, "d", 1) == 3;
        return switch (type) {
            case "gr" -> group(object);
            case "rc" ->
                new Shape.Rect(prop(object, "p", 0, 0), prop(object, "s", 0, 0), prop(object, "r", 0), reversed);
            case "el" -> new Shape.Ellipse(prop(object, "p", 0, 0), prop(object, "s", 0, 0), reversed);
            case "sr" ->
                new Shape.Star(
                        number(object, "sy", 1) != 2,
                        prop(object, "p", 0, 0),
                        prop(object, "pt", 5),
                        prop(object, "r", 0),
                        prop(object, "ir", 0),
                        prop(object, "or", 0),
                        prop(object, "is", 0),
                        prop(object, "os", 0),
                        reversed);
            case "sh" -> object.get("ks") instanceof Obj ks ? new Shape.PathShape(shapeProperty(ks), reversed) : null;
            case "fl" ->
                new Shape.Fill(prop(object, "c", 0, 0, 0, 1), prop(object, "o", 100), number(object, "r", 1) == 2);
            case "st" -> new Shape.Stroke(prop(object, "c", 0, 0, 0, 1), prop(object, "o", 100), line(object));
            case "gf" -> new Shape.GradientFill(ramp(object), prop(object, "o", 100), number(object, "r", 1) == 2);
            case "gs" -> new Shape.GradientStroke(ramp(object), prop(object, "o", 100), line(object));
            case "tm" ->
                new Shape.Trim(
                        prop(object, "s", 0),
                        prop(object, "e", 100),
                        prop(object, "o", 0),
                        number(object, "m", 1) == 2);
            // A transform outside a group, which no exporter writes, and every
            // type this reader passes over.
            default -> null;
        };
    }

    private static Shape.Group group(Obj object) {
        var items = new ArrayList<Shape>();
        var transform = Transform.NONE;
        if (object.get("it") instanceof Arr list) {
            for (var item : list.items()) {
                if (item instanceof Obj child && !bool(child, "hd")) {
                    if (child.get("ty") instanceof Str(var type) && type.equals("tr")) {
                        transform = transform(child);
                        continue;
                    }
                    var shape = shape(child);
                    if (shape != null) {
                        items.add(shape);
                    }
                }
            }
        }
        return new Shape.Group(items, transform);
    }

    private static Shape.Line line(Obj object) {
        var cap = switch ((int) number(object, "lc", 2)) {
            case 1 -> Cap.BUTT;
            case 3 -> Cap.SQUARE;
            default -> Cap.ROUND;
        };
        var join = switch ((int) number(object, "lj", 2)) {
            case 1 -> Join.MITER;
            case 3 -> Join.BEVEL;
            default -> Join.ROUND;
        };
        var miter = number(object, "ml", 4);
        if (object.get("ml2") instanceof Obj ml2) {
            miter = property(ml2).scalar(0);
        }
        var dash = new ArrayList<Property>();
        Property offset = null;
        if (object.get("d") instanceof Arr parts) {
            for (var part : parts.items()) {
                if (part instanceof Obj entry && entry.get("v") instanceof Obj v) {
                    if (entry.get("n") instanceof Str(var name) && name.equals("o")) {
                        offset = property(v);
                    } else {
                        dash.add(property(v));
                    }
                }
            }
        }
        return new Shape.Line(prop(object, "w", 1), cap, join, miter > 0 ? miter : 4, dash, offset);
    }

    private static Shape.Ramp ramp(Obj object) {
        if (!(object.get("g") instanceof Obj g) || !(g.get("k") instanceof Obj k)) {
            throw new IllegalArgumentException("a gradient has stops");
        }
        var count = (int) number(g, "p", 0);
        return new Shape.Ramp(
                number(object, "t", 1) == 2, prop(object, "s", 0, 0), prop(object, "e", 0, 0), property(k), count);
    }

    private static Transform transform(Obj object) {
        Property position = null;
        Property x = null;
        Property y = null;
        if (object.get("p") instanceof Obj p) {
            if (bool(p, "s")) {
                x = p.get("x") instanceof Obj px ? property(px) : Property.of(0);
                y = p.get("y") instanceof Obj py ? property(py) : Property.of(0);
            } else {
                position = property(p);
            }
        } else {
            position = Property.of(0, 0);
        }
        var rotation = object.get("r") instanceof Obj r
                ? property(r)
                : object.get("rz") instanceof Obj rz ? property(rz) : Property.of(0);
        return new Transform(
                prop(object, "a", 0, 0),
                position,
                x,
                y,
                prop(object, "s", 100, 100),
                rotation,
                prop(object, "o", 100),
                prop(object, "sk", 0),
                prop(object, "sa", 0));
    }

    /// The property called `name`, or one fixed at `fallback`.
    private static Property prop(Obj object, String name, double... fallback) {
        return object.get(name) instanceof Obj value ? property(value) : Property.of(fallback);
    }

    private static Property property(Obj object) {
        refuseExpression(object);
        var k = object.get("k");
        if (k == null) {
            throw new IllegalArgumentException("an animatable property has a k");
        }
        if (!isKeyframes(k)) {
            return Property.of(numbers(k));
        }
        var frames = ((Arr) k).items();
        var keys = new ArrayList<Property.Key>();
        for (var index = 0; index < frames.size(); index++) {
            var frame = (Obj) frames.get(index);
            var time = number(frame, "t", 0);
            var start = frame.get("s") != null ? numbers(frame.get("s")) : null;
            if (start == null) {
                // The old shape's last keyframe: only a time, and the value is
                // where the one before it was going.
                start = keys.isEmpty() ? new double[0] : keys.getLast().end();
            }
            double[] end;
            if (frame.get("e") != null) {
                end = numbers(frame.get("e"));
            } else if (index + 1 < frames.size()
                    && frames.get(index + 1) instanceof Obj next
                    && next.get("s") != null) {
                end = numbers(next.get("s"));
            } else {
                end = start;
            }
            Property.Spatial spatial = null;
            if (frame.get("to") instanceof Arr to && frame.get("ti") instanceof Arr ti) {
                var out = numbers(to);
                var in = numbers(ti);
                if (start.length >= 2 && end.length >= 2 && Property.Spatial.curves(out, in)) {
                    spatial = new Property.Spatial(start, end, out, in);
                }
            }
            keys.add(new Property.Key(time, start, end, number(frame, "h", 0) == 1, easings(frame), spatial));
        }
        return new Property.Keyed(keys);
    }

    /// Whether `k` is a list of keyframes rather than a value.
    private static boolean isKeyframes(Value k) {
        return k instanceof Arr array
                && !array.items().isEmpty()
                && array.items().getFirst() instanceof Obj first
                && first.get("t") != null;
    }

    /// The keyframe's curves, one per component when the document gives several.
    private static List<Easing> easings(Obj frame) {
        if (!(frame.get("o") instanceof Obj out) || !(frame.get("i") instanceof Obj in)) {
            return List.of();
        }
        var ox = numbers(out.get("x"));
        var oy = numbers(out.get("y"));
        var ix = numbers(in.get("x"));
        var iy = numbers(in.get("y"));
        var count = Math.min(Math.min(ox.length, oy.length), Math.min(ix.length, iy.length));
        var curves = new ArrayList<Easing>(count);
        for (var i = 0; i < count; i++) {
            curves.add(new Easing(ox[i], oy[i], ix[i], iy[i]));
        }
        return curves;
    }

    private static ShapeProperty shapeProperty(Obj object) {
        refuseExpression(object);
        var k = object.get("k");
        if (k instanceof Obj path) {
            return new ShapeProperty.Fixed(bezier(path));
        }
        if (!(k instanceof Arr frames) || frames.items().isEmpty()) {
            throw new IllegalArgumentException("a path property has a path or keyframes");
        }
        var items = frames.items();
        var keys = new ArrayList<ShapeProperty.Key>();
        for (var index = 0; index < items.size(); index++) {
            var frame = (Obj) items.get(index);
            var start = frame.get("s") != null ? firstPath(frame.get("s")) : null;
            if (start == null) {
                start = keys.isEmpty() ? Bezier.EMPTY : keys.getLast().end();
            }
            Bezier end;
            if (frame.get("e") != null) {
                end = firstPath(frame.get("e"));
            } else if (index + 1 < items.size() && items.get(index + 1) instanceof Obj next && next.get("s") != null) {
                end = firstPath(next.get("s"));
            } else {
                end = start;
            }
            var easings = easings(frame);
            keys.add(new ShapeProperty.Key(
                    number(frame, "t", 0),
                    start,
                    end,
                    number(frame, "h", 0) == 1,
                    easings.isEmpty() ? Easing.LINEAR : easings.getFirst()));
        }
        return new ShapeProperty.Keyed(keys);
    }

    /// A keyframe's path, which the document wraps in a list of one.
    private static Bezier firstPath(@Nullable Value value) {
        if (value instanceof Arr list && !list.items().isEmpty() && list.items().getFirst() instanceof Obj path) {
            return bezier(path);
        }
        if (value instanceof Obj path) {
            return bezier(path);
        }
        throw new IllegalArgumentException("a path keyframe holds a path");
    }

    private static Bezier bezier(Obj path) {
        var vertices = points(path.get("v"));
        var count = vertices.length;
        var in = path.get("i") != null ? points(path.get("i")) : new double[count];
        var out = path.get("o") != null ? points(path.get("o")) : new double[count];
        if (in.length != count || out.length != count) {
            throw new IllegalArgumentException("a path has as many tangents as vertices");
        }
        return new Bezier(vertices, in, out, bool(path, "c"));
    }

    /// `[[x, y], ...]`, flattened.
    private static double[] points(@Nullable Value value) {
        if (!(value instanceof Arr list)) {
            throw new IllegalArgumentException("a path's points are a list");
        }
        var out = new double[list.items().size() * 2];
        for (var i = 0; i < list.items().size(); i++) {
            var point = numbers(list.items().get(i));
            out[i * 2] = point.length > 0 ? point[0] : 0;
            out[i * 2 + 1] = point.length > 1 ? point[1] : 0;
        }
        return out;
    }

    private static void refuseExpression(Obj object) {
        if (object.get("x") instanceof Str(var code) && !code.isBlank()) {
            throw new ImageDecodeException("this Lottie document animates a property with an expression, which is"
                    + " code for a player to run rather than a description to draw; Telegram refuses such a"
                    + " sticker too");
        }
    }

    /// A number or a list of numbers, as an array.
    private static double[] numbers(@Nullable Value value) {
        return switch (value) {
            case Num(var n) -> new double[] {n};
            case Arr array -> {
                var out = new double[array.items().size()];
                for (var i = 0; i < out.length; i++) {
                    out[i] = array.items().get(i) instanceof Num(var n) ? n : 0;
                }
                yield out;
            }
            case null, default -> new double[0];
        };
    }

    private static double number(Obj object, String name, double fallback) {
        return switch (object.get(name)) {
            case Num(var n) -> n;
            case Bool(var b) -> b ? 1 : 0;
            case null, default -> fallback;
        };
    }

    private static boolean bool(Obj object, String name) {
        return number(object, name, 0) != 0;
    }
}
