package io.github.digitalsmile.goldberry.assets;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/// Turns an SVG icon set into one table of path data.
///
/// Shipping 1544 SVGs would put an XML parser on the path that draws a checkbox.
/// Instead every icon is reduced, once at build time, to a single run of SVG path
/// data — the one thing Blend2D can consume directly.
///
/// The output is a text table, `name<TAB>path` per line. `docs/ARCHITECTURE.md`
/// §6.3 anticipates a "compact binary path table"; this is the same idea in the
/// form that can be read, diffed and grepped. Turning it binary is worth doing
/// when something measures the parse, and today nothing draws an icon at all.
///
/// What makes this a transcription rather than an SVG renderer is Lucide's
/// uniformity: every icon is a 24×24 viewBox of 2px round strokes with no
/// transforms, fills, gradients or groups. Anything outside that is refused
/// rather than approximated — see [#compile].
public final class IconCompiler {

    /// Every SVG element this compiler understands. Anything else is a refusal.
    private static final List<String> SHAPES =
            List.of("path", "line", "polyline", "polygon", "circle", "ellipse", "rect");

    private final DocumentBuilderFactory factory;

    public IconCompiler() {
        this.factory = DocumentBuilderFactory.newInstance();
        // The icons are trusted input from a pinned, checksummed archive, but a
        // parser that resolves external entities is a liability regardless of
        // what it is pointed at.
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("the XML parser refused to be locked down", e);
        }
    }

    /// Compiles one icon's SVG into a single run of path data.
    ///
    /// Every shape in the document becomes a subpath and they are concatenated
    /// in document order. That is safe because each one begins with a moveto —
    /// concatenating path data is only ever wrong when a fragment continues from
    /// wherever the previous one ended.
    ///
    /// @throws IllegalArgumentException if the icon uses an element this cannot
    ///         convert, which would otherwise produce an icon that is subtly
    ///         incomplete rather than obviously missing
    public String compile(String name, InputStream svg) throws IOException {
        Element root;
        try {
            root = factory.newDocumentBuilder().parse(svg).getDocumentElement();
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalArgumentException(name + ": not parseable as SVG", e);
        }

        var parts = new ArrayList<String>();
        var children = root.getChildNodes();
        for (var i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            var element = (Element) child;
            var tag = element.getTagName();
            if (!SHAPES.contains(tag)) {
                throw new IllegalArgumentException(
                        name + ": <" + tag + "> needs a real SVG renderer, not a shape conversion."
                                + " Emitting the rest would give an icon that is silently"
                                + " incomplete.");
            }
            var path = convert(element);
            if (!path.isEmpty()) {
                parts.add(path);
            }
        }

        if (parts.isEmpty()) {
            throw new IllegalArgumentException(
                    name + ": produced no path data at all, so it would render as nothing");
        }
        return String.join(" ", parts);
    }

    /// Compiles a whole set, keyed by icon name and sorted for a stable output.
    ///
    /// Sorted because the table is a build output that lands in a jar: an
    /// unstable order would make every rebuild produce a different artifact for
    /// the same inputs.
    public Map<String, String> compileAll(Map<String, byte[]> svgsByName) throws IOException {
        var table = new TreeMap<String, String>();
        for (var entry : svgsByName.entrySet()) {
            try (var in = new java.io.ByteArrayInputStream(entry.getValue())) {
                table.put(entry.getKey(), compile(entry.getKey(), in));
            }
        }
        return table;
    }

    private static String convert(Element element) {
        return switch (element.getTagName()) {
            case "path" -> attribute(element, "d", "").trim();
            case "line" -> SvgShapes.line(
                    number(element, "x1", 0), number(element, "y1", 0),
                    number(element, "x2", 0), number(element, "y2", 0));
            case "polyline" -> SvgShapes.polyline(attribute(element, "points", ""));
            case "polygon" -> SvgShapes.polygon(attribute(element, "points", ""));
            case "circle" -> SvgShapes.circle(
                    number(element, "cx", 0), number(element, "cy", 0), number(element, "r", 0));
            case "ellipse" -> SvgShapes.ellipse(
                    number(element, "cx", 0), number(element, "cy", 0),
                    number(element, "rx", 0), number(element, "ry", 0));
            // -1 rather than 0 for the radii: SVG distinguishes "not given",
            // which mirrors the other axis, from an explicit zero, which does
            // not. Collapsing the two would square off every rounded corner.
            case "rect" -> SvgShapes.rect(
                    number(element, "x", 0), number(element, "y", 0),
                    number(element, "width", 0), number(element, "height", 0),
                    number(element, "rx", -1), number(element, "ry", -1));
            default -> throw new IllegalStateException("unreachable: " + element.getTagName());
        };
    }

    private static String attribute(Element element, String name, String fallback) {
        var value = element.getAttribute(name);
        return value.isEmpty() ? fallback : value;
    }

    private static double number(Element element, String name, double fallback) {
        var value = element.getAttribute(name);
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "attribute " + name + "=\"" + value + "\" is not a number", e);
        }
    }
}
