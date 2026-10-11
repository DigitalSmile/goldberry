package dev.goldberry.css.value.colour;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/// The named colours of CSS Color 4, as `0xAARRGGBB`.
///
/// Every name a browser knows, `rebeccapurple` and both spellings of grey
/// included, so that a sheet written for the web reads the same here.
/// `transparent` is not one of them: it is a keyword of its own, read beside
/// these.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#colour).
public final class NamedColours {

    private NamedColours() {}

    /// The colour `name` names, in any case, or null.
    public static @Nullable Integer get(String name) {
        var rgb = RGB.get(name.toLowerCase(Locale.ROOT));
        return rgb == null ? null : 0xFF000000 | rgb;
    }

    /// How many names there are: 148.
    public static int count() {
        return RGB.size();
    }

    // Puts rather than `Map.ofEntries`: 148 entries as varargs is a call javac
    // and the IDE both slow down on.
    private static final Map<String, Integer> RGB = table();

    private static Map<String, Integer> table() {
        var table = new HashMap<String, Integer>(256);
        table.put("aliceblue", 0xF0F8FF);
        table.put("antiquewhite", 0xFAEBD7);
        table.put("aqua", 0x00FFFF);
        table.put("aquamarine", 0x7FFFD4);
        table.put("azure", 0xF0FFFF);
        table.put("beige", 0xF5F5DC);
        table.put("bisque", 0xFFE4C4);
        table.put("black", 0x000000);
        table.put("blanchedalmond", 0xFFEBCD);
        table.put("blue", 0x0000FF);
        table.put("blueviolet", 0x8A2BE2);
        table.put("brown", 0xA52A2A);
        table.put("burlywood", 0xDEB887);
        table.put("cadetblue", 0x5F9EA0);
        table.put("chartreuse", 0x7FFF00);
        table.put("chocolate", 0xD2691E);
        table.put("coral", 0xFF7F50);
        table.put("cornflowerblue", 0x6495ED);
        table.put("cornsilk", 0xFFF8DC);
        table.put("crimson", 0xDC143C);
        table.put("cyan", 0x00FFFF);
        table.put("darkblue", 0x00008B);
        table.put("darkcyan", 0x008B8B);
        table.put("darkgoldenrod", 0xB8860B);
        table.put("darkgray", 0xA9A9A9);
        table.put("darkgreen", 0x006400);
        table.put("darkgrey", 0xA9A9A9);
        table.put("darkkhaki", 0xBDB76B);
        table.put("darkmagenta", 0x8B008B);
        table.put("darkolivegreen", 0x556B2F);
        table.put("darkorange", 0xFF8C00);
        table.put("darkorchid", 0x9932CC);
        table.put("darkred", 0x8B0000);
        table.put("darksalmon", 0xE9967A);
        table.put("darkseagreen", 0x8FBC8F);
        table.put("darkslateblue", 0x483D8B);
        table.put("darkslategray", 0x2F4F4F);
        table.put("darkslategrey", 0x2F4F4F);
        table.put("darkturquoise", 0x00CED1);
        table.put("darkviolet", 0x9400D3);
        table.put("deeppink", 0xFF1493);
        table.put("deepskyblue", 0x00BFFF);
        table.put("dimgray", 0x696969);
        table.put("dimgrey", 0x696969);
        table.put("dodgerblue", 0x1E90FF);
        table.put("firebrick", 0xB22222);
        table.put("floralwhite", 0xFFFAF0);
        table.put("forestgreen", 0x228B22);
        table.put("fuchsia", 0xFF00FF);
        table.put("gainsboro", 0xDCDCDC);
        table.put("ghostwhite", 0xF8F8FF);
        table.put("gold", 0xFFD700);
        table.put("goldenrod", 0xDAA520);
        table.put("gray", 0x808080);
        table.put("green", 0x008000);
        table.put("greenyellow", 0xADFF2F);
        table.put("grey", 0x808080);
        table.put("honeydew", 0xF0FFF0);
        table.put("hotpink", 0xFF69B4);
        table.put("indianred", 0xCD5C5C);
        table.put("indigo", 0x4B0082);
        table.put("ivory", 0xFFFFF0);
        table.put("khaki", 0xF0E68C);
        table.put("lavender", 0xE6E6FA);
        table.put("lavenderblush", 0xFFF0F5);
        table.put("lawngreen", 0x7CFC00);
        table.put("lemonchiffon", 0xFFFACD);
        table.put("lightblue", 0xADD8E6);
        table.put("lightcoral", 0xF08080);
        table.put("lightcyan", 0xE0FFFF);
        table.put("lightgoldenrodyellow", 0xFAFAD2);
        table.put("lightgray", 0xD3D3D3);
        table.put("lightgreen", 0x90EE90);
        table.put("lightgrey", 0xD3D3D3);
        table.put("lightpink", 0xFFB6C1);
        table.put("lightsalmon", 0xFFA07A);
        table.put("lightseagreen", 0x20B2AA);
        table.put("lightskyblue", 0x87CEFA);
        table.put("lightslategray", 0x778899);
        table.put("lightslategrey", 0x778899);
        table.put("lightsteelblue", 0xB0C4DE);
        table.put("lightyellow", 0xFFFFE0);
        table.put("lime", 0x00FF00);
        table.put("limegreen", 0x32CD32);
        table.put("linen", 0xFAF0E6);
        table.put("magenta", 0xFF00FF);
        table.put("maroon", 0x800000);
        table.put("mediumaquamarine", 0x66CDAA);
        table.put("mediumblue", 0x0000CD);
        table.put("mediumorchid", 0xBA55D3);
        table.put("mediumpurple", 0x9370DB);
        table.put("mediumseagreen", 0x3CB371);
        table.put("mediumslateblue", 0x7B68EE);
        table.put("mediumspringgreen", 0x00FA9A);
        table.put("mediumturquoise", 0x48D1CC);
        table.put("mediumvioletred", 0xC71585);
        table.put("midnightblue", 0x191970);
        table.put("mintcream", 0xF5FFFA);
        table.put("mistyrose", 0xFFE4E1);
        table.put("moccasin", 0xFFE4B5);
        table.put("navajowhite", 0xFFDEAD);
        table.put("navy", 0x000080);
        table.put("oldlace", 0xFDF5E6);
        table.put("olive", 0x808000);
        table.put("olivedrab", 0x6B8E23);
        table.put("orange", 0xFFA500);
        table.put("orangered", 0xFF4500);
        table.put("orchid", 0xDA70D6);
        table.put("palegoldenrod", 0xEEE8AA);
        table.put("palegreen", 0x98FB98);
        table.put("paleturquoise", 0xAFEEEE);
        table.put("palevioletred", 0xDB7093);
        table.put("papayawhip", 0xFFEFD5);
        table.put("peachpuff", 0xFFDAB9);
        table.put("peru", 0xCD853F);
        table.put("pink", 0xFFC0CB);
        table.put("plum", 0xDDA0DD);
        table.put("powderblue", 0xB0E0E6);
        table.put("purple", 0x800080);
        table.put("rebeccapurple", 0x663399);
        table.put("red", 0xFF0000);
        table.put("rosybrown", 0xBC8F8F);
        table.put("royalblue", 0x4169E1);
        table.put("saddlebrown", 0x8B4513);
        table.put("salmon", 0xFA8072);
        table.put("sandybrown", 0xF4A460);
        table.put("seagreen", 0x2E8B57);
        table.put("seashell", 0xFFF5EE);
        table.put("sienna", 0xA0522D);
        table.put("silver", 0xC0C0C0);
        table.put("skyblue", 0x87CEEB);
        table.put("slateblue", 0x6A5ACD);
        table.put("slategray", 0x708090);
        table.put("slategrey", 0x708090);
        table.put("snow", 0xFFFAFA);
        table.put("springgreen", 0x00FF7F);
        table.put("steelblue", 0x4682B4);
        table.put("tan", 0xD2B48C);
        table.put("teal", 0x008080);
        table.put("thistle", 0xD8BFD8);
        table.put("tomato", 0xFF6347);
        table.put("turquoise", 0x40E0D0);
        table.put("violet", 0xEE82EE);
        table.put("wheat", 0xF5DEB3);
        table.put("white", 0xFFFFFF);
        table.put("whitesmoke", 0xF5F5F5);
        table.put("yellow", 0xFFFF00);
        table.put("yellowgreen", 0x9ACD32);
        return Map.copyOf(table);
    }
}
