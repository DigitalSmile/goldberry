package io.github.digitalsmile.goldberry.assets;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/// One upstream archive Goldberry bundles from, pinned by version **and**
/// checksum.
///
/// Both, because they answer different questions. The version says which release
/// was chosen; the checksum says that what arrived is that release. A git tag can
/// be moved and a release asset can be replaced — GitHub allows both — and an
/// asset that changed underneath us would change how every application using the
/// toolkit renders, silently, with no version number moving.
///
/// This is the same discipline ADR-0030 applies to the native upstreams. It lives
/// in Java rather than in `gradle/libs.versions.toml` because a version catalog
/// holds versions and has nowhere to put a checksum, an archive layout, or the
/// list of entries worth extracting — and splitting those across two files is how
/// they drift apart.
///
/// @param name       short id, used for the cached archive's filename
/// @param version    the upstream release
/// @param url        where to fetch the archive
/// @param sha256     the archive's expected SHA-256, lowercase hex
/// @param extract    archive entry -> path under the generated resource root
/// @param licence    archive entry -> filename under `licenses/`, when the
///                   archive carries its own licence text
/// @param licenceUrl where to fetch the licence text when the archive does not
///                   carry one; null otherwise
/// @param licenceAs  filename under `licenses/` for [#licenceUrl]
/// @param packaging  whether [#url] is an archive to extract from or the one
///                   file the asset is
public record Asset(
        String name,
        String version,
        String url,
        String sha256,
        Map<String, String> extract,
        Map<String, String> licence,
        String licenceUrl,
        String licenceAs,
        Packaging packaging) {

    /// What the pinned URL downloads.
    ///
    /// Most upstreams publish a release archive and the manifest names the
    /// entries worth taking out of it. Some publish **no** archive holding only
    /// the font: Noto's emoji release has no assets at all, and its source zip
    /// is the whole repository — hundreds of megabytes of PNGs for one 5 MB
    /// file. So the asset pins the file itself, by the tag in its URL and by its
    /// checksum, which is the same promise an archive's checksum makes.
    public enum Packaging {

        /// A zip archive; [Asset#extract] maps entries in it to resources.
        ZIP,

        /// The download **is** the resource. [Asset#extract] holds at most one
        /// entry, keyed by the upstream file name for the reader's benefit — or
        /// none, for a file that is compiled rather than shipped, as Lucide's
        /// archive is — and [Asset#licence] is empty because there is no archive
        /// to take a licence out of.
        FILE
    }

    /// An asset fetched as a zip archive — every upstream but one.
    public Asset(
            String name,
            String version,
            String url,
            String sha256,
            Map<String, String> extract,
            Map<String, String> licence,
            String licenceUrl,
            String licenceAs) {
        this(name, version, url, sha256, extract, licence, licenceUrl, licenceAs, Packaging.ZIP);
    }

    public Asset {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(sha256, "sha256");
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    name + ": a SHA-256 is 64 lowercase hex characters, and \"" + sha256
                            + "\" is not one");
        }
        extract = Map.copyOf(extract == null ? Map.of() : extract);
        licence = Map.copyOf(licence == null ? Map.of() : licence);
        if ((licenceUrl == null) != (licenceAs == null)) {
            throw new IllegalArgumentException(
                    name + ": a licence URL and its destination filename go together");
        }
        Objects.requireNonNull(packaging, "packaging");
        if (packaging == Packaging.FILE && (extract.size() > 1 || !licence.isEmpty())) {
            // A single file is at most one resource, and there is no archive to
            // find a licence entry in. Refused here, where the manifest is
            // written, rather than discovered by a build that extracted nothing.
            throw new IllegalArgumentException(
                    name + ": a single-file asset is at most one resource and carries no licence entry");
        }
    }

    /// The filename the download is cached under.
    ///
    /// A single file keeps its own extension, so the cache directory says what
    /// each entry is: `noto-emoji.ttf` beside `inter.zip`.
    public String archiveName() {
        return switch (packaging) {
            case ZIP -> name + ".zip";
            case FILE -> {
                var file = url.substring(url.lastIndexOf('/') + 1);
                var dot = file.lastIndexOf('.');
                yield dot < 0 ? name : name + file.substring(dot);
            }
        };
    }

    // ------------------------------------------------------------------------
    // The manifest.
    //
    // Fonts per docs/ARCHITECTURE.md §6.1 and §6.2, icons per §6.3. Every
    // checksum here was computed from the archive that was actually downloaded
    // and used — not copied from a release page, which would only prove the
    // release page and the download agree with each other.
    // ------------------------------------------------------------------------

    /// Inter — the embedded UI face.
    ///
    /// The variable file **and** the SemiBold static instance, which is two
    /// weights rather than one axis.
    ///
    /// Instancing `wght` at runtime would be the smaller download and the more
    /// general answer, and it needs symbols bound in *both* libraries —
    /// HarfBuzz's `hb_font_set_variations` and Blend2D's variation settings —
    /// which means a new struct layout and three export branches, the ELF version
    /// script, the MSVC `.def` and the Mach-O list. That machinery has caught the
    /// same class of local-symbol bug three times and is only ever answered by a
    /// CI run across four targets.
    ///
    /// `docs/design-system.md` §1.4 ships **exactly two weights**, 400 and 600,
    /// and Principle 3 says a screen that needs a third extends the system rather
    /// than improvising one. Two static instances therefore cover the whole
    /// shipped scale for 400 KB and no native change, and the axis stays a real
    /// optimisation for the day an intermediate weight is actually specified.
    ///
    /// ## And the italics, which are two more files for the same reason
    ///
    /// An italic is a **face** and not a transform: Inter's italic is drawn, with
    /// different letterforms — a single-storey `a`, a cursive `f` — and the
    /// alternative available without a file is shearing the upright glyphs, which
    /// is a *synthetic oblique* and a decision about type design rather than a
    /// workaround (`docs/gaps.md` G27, ADR-0323).
    ///
    /// Two of them rather than one, because the matrix has to close: a stylesheet
    /// that writes `font-weight: 600; font-style: italic` on a heading must get
    /// something that is both, and "the nearest of the three we shipped" is how a
    /// design system acquires a weight nobody chose. Two weights × two styles is
    /// four files and 830 KB more, which is the same trade ADR-0066 took.
    public static final Asset INTER = new Asset(
            "inter",
            "4.1",
            "https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip",
            "9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e",
            Map.of("InterVariable.ttf", "fonts/InterVariable.ttf",
                    "extras/ttf/Inter-SemiBold.ttf", "fonts/Inter-SemiBold.ttf",
                    "extras/ttf/Inter-Italic.ttf", "fonts/Inter-Italic.ttf",
                    "extras/ttf/Inter-SemiBoldItalic.ttf", "fonts/Inter-SemiBoldItalic.ttf"),
            Map.of("LICENSE.txt", "inter.txt"),
            null,
            null);

    /// JetBrains Mono — the embedded code face.
    public static final Asset JETBRAINS_MONO = new Asset(
            "jetbrains-mono",
            "2.304",
            "https://github.com/JetBrains/JetBrainsMono/releases/download/v2.304/JetBrainsMono-2.304.zip",
            "6f6376c6ed2960ea8a963cd7387ec9d76e3f629125bc33d1fdcd7eb7012f7bbf",
            Map.of("fonts/variable/JetBrainsMono[wght].ttf", "fonts/JetBrainsMono.ttf"),
            Map.of("OFL.txt", "jetbrains-mono.txt"),
            null,
            null);

    /// Noto Color Emoji — the emoji slot.
    ///
    /// **The COLRv1 build**, `Noto-COLRv1.ttf`. Google publishes the same
    /// pictures two ways and the choice between them is the one ADR-0393 made
    /// for OpenMoji: the `CBDT` build is 10.7 MB of 136-pixel bitmap strikes that
    /// blur at any size above that, and the COLRv1 build is 5 MB of outlines,
    /// gradients and transforms that are crisp at every scale. The toolkit draws
    /// the paint graph itself (`text.font.sfnt.ColorPaints`, ADR-0456).
    ///
    /// **A single file, not an archive.** The release has no assets and its
    /// source zip is the whole repository, so the font is pinned by the tag in
    /// its URL and by its checksum ([Packaging#FILE]).
    ///
    /// SIL OFL 1.1 — the font's own `LICENSE`, fetched from the same tag.
    public static final Asset NOTO_EMOJI = new Asset(
            "noto-emoji",
            "2.051",
            "https://raw.githubusercontent.com/googlefonts/noto-emoji/v2.051/fonts/Noto-COLRv1.ttf",
            "0ae57fe58645638523ba35f388d93739d292539a9acb84df5700c81b1e1a28d2",
            Map.of("Noto-COLRv1.ttf", "fonts/NotoColorEmoji.ttf"),
            Map.of(),
            "https://raw.githubusercontent.com/googlefonts/noto-emoji/v2.051/fonts/LICENSE",
            "noto-emoji.txt",
            Packaging.FILE);

    /// Unicode's `emoji-test.txt` — which **group** each emoji is in.
    ///
    /// Not a font and not shipped as itself: the showcase's Emoji screen sorts
    /// its sheet into Unicode's ten groups ("Smileys & Emotion", "Flags", …),
    /// and the JDK carries every emoji property but that one. So the file is
    /// compiled by [CatalogCompiler] into a table of code point and group, and
    /// nothing is extracted.
    ///
    /// Version 17.0, the Unicode version Noto's pinned release draws; the JDK's
    /// own tables may be a version behind, which costs nothing — a code point
    /// the JDK does not call an emoji is never looked up.
    public static final Asset UNICODE_EMOJI = new Asset(
            "unicode-emoji",
            "17.0",
            "https://unicode.org/Public/17.0.0/emoji/emoji-test.txt",
            "1d8a944f88d7952f7ef7c5167fef3c67995bcae24543949710231b03a201acda",
            Map.of(),
            Map.of(),
            "https://www.unicode.org/license.txt",
            "unicode.txt",
            Packaging.FILE);

    /// Lucide — the icon set.
    ///
    /// Nothing is extracted file by file: the 1544 SVGs are compiled into one
    /// path table by [IconCompiler].
    public static final Asset LUCIDE = new Asset(
            "lucide",
            "0.469.0",
            "https://github.com/lucide-icons/lucide/releases/download/0.469.0/lucide-icons-0.469.0.zip",
            "a1f58d08afa0f7c12a9e6eb92814b74c6fb763eeb92bf62ead5b38bb7770de7f",
            Map.of(),
            Map.of(),
            "https://raw.githubusercontent.com/lucide-icons/lucide/0.469.0/LICENSE",
            "lucide.txt");

    /// Everything Goldberry bundles.
    public static List<Asset> all() {
        return List.of(INTER, JETBRAINS_MONO, NOTO_EMOJI, LUCIDE, UNICODE_EMOJI);
    }
}
