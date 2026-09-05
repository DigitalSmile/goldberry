package io.github.digitalsmile.goldberry.widgets;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.text.Text;

class HueTextProbe {

    private static double lum(int argb) {
        return 0.2126 * ch((argb >> 16) & 0xFF) + 0.7152 * ch((argb >> 8) & 0xFF) + 0.0722 * ch(argb & 0xFF);
    }

    private static double ch(int v) {
        var c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double contrast(int a, int b) {
        var la = lum(a);
        var lb = lum(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static int resolve(Theme theme, String css) {
        var sheets = new java.util.ArrayList<>(Controls.stylesheets(theme));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, css));
        var style = ComputedStyle.of(
                new StyleResolver(sheets).resolve(new ElementTree(new Text("Aa")).root()), CssLength.Context.DEFAULT);
        return style.color();
    }

    private static double[] toHsl(int argb) {
        double r = ((argb >> 16) & 0xFF) / 255.0;
        double g = ((argb >> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;
        var max = Math.max(r, Math.max(g, b));
        var min = Math.min(r, Math.min(g, b));
        var l = (max + min) / 2;
        double h = 0;
        double s = 0;
        if (max != min) {
            var d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == r) {
                h = (g - b) / d + (g < b ? 6 : 0);
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else {
                h = (r - g) / d + 4;
            }
            h /= 6;
        }
        return new double[] {h, s, l};
    }

    private static int fromHsl(double h, double s, double l) {
        double r;
        double g;
        double b;
        if (s == 0) {
            r = l;
            g = l;
            b = l;
        } else {
            var q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            var p = 2 * l - q;
            r = hue(p, q, h + 1.0 / 3);
            g = hue(p, q, h);
            b = hue(p, q, h - 1.0 / 3);
        }
        return 0xFF000000
                | ((int) Math.round(r * 255) << 16)
                | ((int) Math.round(g * 255) << 8)
                | (int) Math.round(b * 255);
    }

    private static double hue(double p, double q, double t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1.0 / 6) {
            return p + (q - p) * 6 * t;
        }
        if (t < 0.5) {
            return q;
        }
        if (t < 2.0 / 3) {
            return p + (q - p) * (2.0 / 3 - t) * 6;
        }
        return p;
    }

    @Test
    void probe() {
        for (var theme : List.of(Theme.NORD_DARK, Theme.NORD_LIGHT)) {
            var dark = theme == Theme.NORD_DARK;
            var surfaces = new int[3];
            var names = List.of("bg", "surface", "surface-2");
            for (var i = 0; i < 3; i++) {
                surfaces[i] = resolve(theme, "text { color: var(--gb-" + names.get(i) + ") }");
            }
            for (var hue : List.of("info", "success", "warning", "danger")) {
                var base = resolve(theme, "text { color: var(--gb-" + hue + ") }");
                var hsl = toHsl(base);
                Integer found = null;
                for (var step = 0; step <= 100; step++) {
                    var l = dark ? hsl[2] + step * 0.005 : hsl[2] - step * 0.005;
                    if (l > 1 || l < 0) {
                        break;
                    }
                    var candidate = fromHsl(hsl[0], hsl[1], l);
                    var worst = Double.MAX_VALUE;
                    for (var s : surfaces) {
                        worst = Math.min(worst, contrast(s, candidate));
                    }
                    if (worst >= 4.55) {
                        found = candidate;
                        System.out.printf(
                                Locale.ROOT,
                                "%-5s %-8s base=#%06x L=%.0f%%  ->  #%06x L=%.0f%%  worst=%.2f:1  (bg %.2f surface %.2f surface-2 %.2f)%n",
                                dark ? "dark" : "light",
                                hue,
                                base & 0xFFFFFF,
                                hsl[2] * 100,
                                candidate & 0xFFFFFF,
                                l * 100,
                                worst,
                                contrast(surfaces[0], candidate),
                                contrast(surfaces[1], candidate),
                                contrast(surfaces[2], candidate));
                        break;
                    }
                }
                if (found == null) {
                    System.out.printf(Locale.ROOT, "%-5s %-8s NO SOLUTION%n", dark ? "dark" : "light", hue);
                }
            }
        }
    }
}
