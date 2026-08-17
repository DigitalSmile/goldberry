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
public record Asset(
        String name,
        String version,
        String url,
        String sha256,
        Map<String, String> extract,
        Map<String, String> licence,
        String licenceUrl,
        String licenceAs) {

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
    }

    /// The filename the archive is cached under.
    public String archiveName() {
        return name + ".zip";
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
    public static final Asset INTER = new Asset(
            "inter",
            "4.1",
            "https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip",
            "9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e",
            Map.of("InterVariable.ttf", "fonts/InterVariable.ttf",
                    "extras/ttf/Inter-SemiBold.ttf", "fonts/Inter-SemiBold.ttf"),
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

    /// OpenMoji — the emoji slot.
    ///
    /// The monochrome build is the default per §6.2, and it is also the small
    /// one: 1.4 MB against 2.5 MB for the COLRv0 colour build and 10 MB for the
    /// SVG-in-OpenType variants. The colour build is opt-in and not bundled
    /// until something can draw layered outlines.
    ///
    /// The font archive carries no licence file — only a README — so the CC BY-SA
    /// text is fetched from the repository at the same tag.
    public static final Asset OPENMOJI = new Asset(
            "openmoji",
            "15.0.0",
            "https://github.com/hfg-gmuend/openmoji/releases/download/15.0.0/openmoji-font.zip",
            "9c157abb27203a3e2f13d5e000c8773015e3e373d3da3c263c1ed917cacbb6de",
            Map.of("OpenMoji-black-glyf/OpenMoji-black-glyf.ttf", "fonts/OpenMoji-black.ttf"),
            Map.of(),
            "https://raw.githubusercontent.com/hfg-gmuend/openmoji/15.0.0/LICENSE.txt",
            "openmoji.txt");

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
        return List.of(INTER, JETBRAINS_MONO, OPENMOJI, LUCIDE);
    }
}
