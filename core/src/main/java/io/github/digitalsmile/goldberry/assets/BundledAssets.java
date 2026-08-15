package io.github.digitalsmile.goldberry.assets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// The fonts and icons that ship inside `goldberry-core`.
///
/// Embedding them is a deliberate trade (`docs/ARCHITECTURE.md` §6.1): three
/// megabytes of jar in exchange for a toolkit that renders identically on every
/// machine, whatever fonts happen to be installed. The design system's metrics
/// are authored against these faces, so a system font is an opt-in that
/// explicitly gives up pixel-exactness.
///
/// The bytes are prepared at build time by `:assets`, which fetches each
/// upstream release by version **and** SHA-256 (ADR-0033) — so what is in the
/// jar is provably the release that was pinned, not merely something that
/// downloaded successfully.
public final class BundledAssets {

    private static final String ROOT = "/io/github/digitalsmile/goldberry/assets/";

    /// The icon table, parsed on first use and kept.
    ///
    /// 1544 icons and roughly 220 KiB of path data. Parsing it eagerly at class
    /// load would put that on the start-up path this toolkit makes claims about
    /// (ADR-0028); parsing it per icon would re-read the whole table for every
    /// checkbox. Once, lazily, is the middle.
    private static final class Icons {
        private static final Map<String, String> TABLE = loadIcons();
    }

    private BundledAssets() {
    }

    /// Reads a bundled font's bytes.
    ///
    /// A fresh array each call: the caller hands it to HarfBuzz and Blend2D,
    /// both of which copy it, and a shared mutable array of font bytes is a
    /// footgun for the sake of a megabyte.
    ///
    /// @throws UncheckedIOException if the resource is missing, which means the
    ///         jar was assembled without the asset step
    public static byte[] font(BundledFont font) {
        Objects.requireNonNull(font, "font");
        return read(ROOT + font.resource());
    }

    /// The path data for one Lucide icon, in a 24×24 box.
    ///
    /// Empty when the set has no such icon — a caller asking for a name a
    /// designer invented should get a clear absence rather than an exception
    /// from deep inside a paint pass.
    public static Optional<String> icon(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(Icons.TABLE.get(name));
    }

    /// Every icon name in the bundled set.
    public static Set<String> iconNames() {
        return Icons.TABLE.keySet();
    }

    /// The box every bundled icon is drawn in. Lucide is uniform 24×24, which is
    /// what lets the table carry no per-icon metadata at all.
    public static final double ICON_SIZE = 24.0;

    /// The stroke width the icons are designed at, in the same 24×24 box.
    public static final double ICON_STROKE_WIDTH = 2.0;

    private static Map<String, String> loadIcons() {
        var table = new LinkedHashMap<String, String>(2048);
        var text = new String(read(ROOT + "icons/lucide.txt"), StandardCharsets.UTF_8);
        text.lines().forEach(line -> {
            if (line.isEmpty() || line.charAt(0) == '#') {
                return;
            }
            var tab = line.indexOf('\t');
            if (tab > 0) {
                table.put(line.substring(0, tab), line.substring(tab + 1));
            }
        });
        return Map.copyOf(table);
    }

    private static byte[] read(String resource) {
        try (var in = BundledAssets.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException(
                        "bundled asset " + resource + " is missing from the jar."
                                + " It is produced by :assets — a build that skipped"
                                + " prepareAssets produces a goldberry-core that cannot"
                                + " render text."));
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read bundled asset " + resource, e);
        }
    }
}
