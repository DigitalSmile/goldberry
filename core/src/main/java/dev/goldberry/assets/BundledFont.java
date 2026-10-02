package dev.goldberry.assets;

import java.util.List;

import org.jspecify.annotations.Nullable;

/// The faces that ship inside `goldberry-core`: Inter in two weights and two
/// styles, JetBrains Mono for code, and the slot the emoji face fills.
///
/// ```java
/// BundledFont face = BundledFont.of("Inter", Weight.SEMI_BOLD, Style.ITALIC);
/// ```
///
/// Three families, and that is the whole fallback chain: a primary family and an
/// emoji slot, with a monospace face for code. There is no general fallback
/// cascade: a character in neither slot renders as `.notdef`, deliberately,
/// because a cascade across arbitrary system fonts is what makes text look
/// different on every machine.
///
/// A weight is a face here, not an axis. Inter and JetBrains Mono are variable
/// files, and instancing `wght` at runtime would be the general answer, but the
/// design system ships exactly two weights, so the second one is a second face.
/// A stylesheet may ask for any CSS weight; [Face#match] answers with the
/// nearest face the way a browser does, so 500 is Inter's 400 and 700 is its
/// 600. An application that wants 500 to be a 500 ships that face.
///
/// An italic is a face too. Inter's italic is **drawn**, with different
/// letterforms, so it is a file rather than a transform; shearing the upright
/// glyphs instead would be a *synthetic oblique*, a decision about type design
/// the toolkit does not take for a stylesheet. Two weights × two styles is four
/// Inter faces, and the matrix closes on purpose: a stylesheet that asks for a
/// semibold italic heading gets one, rather than the nearest of three.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#the-bundled-faces).
public enum BundledFont implements Face {

    /// Inter Regular (400) — the UI face, and what every metric in the design
    /// system is authored against.
    UI("fonts/InterVariable.ttf", "Inter", Weight.REGULAR, Style.UPRIGHT),

    /// Inter SemiBold (600) — `body-strong`, and the weight of a button's label.
    UI_STRONG("fonts/Inter-SemiBold.ttf", "Inter", Weight.SEMI_BOLD, Style.UPRIGHT),

    /// Inter Italic (400) — emphasis in running text, and the face a board's
    /// italic button asks for.
    UI_ITALIC("fonts/Inter-Italic.ttf", "Inter", Weight.REGULAR, Style.ITALIC),

    /// Inter SemiBold Italic (600) — the fourth corner of the matrix, so
    /// `font-weight: 600; font-style: italic` is a face rather than a compromise.
    UI_STRONG_ITALIC("fonts/Inter-SemiBoldItalic.ttf", "Inter", Weight.SEMI_BOLD, Style.ITALIC),

    /// JetBrains Mono — the code face. `mono` is specified at 400 only and
    /// upright only, so there is no strong or italic companion until something
    /// asks for one.
    CODE("fonts/JetBrainsMono.ttf", "JetBrains Mono", Weight.REGULAR, Style.UPRIGHT),

    /// Noto Color Emoji — the emoji slot.
    ///
    /// The COLRv1 build, drawn from its paint graphs, and **not in this jar**:
    /// it ships as `goldberry-emoji` and reaches the toolkit through
    /// [EmojiFont], so the resource name here is the one that module writes and
    /// nothing in `:core` reads it. The family is the face's own name-table
    /// family, which is what a stylesheet writes.
    EMOJI("fonts/NotoColorEmoji.ttf", "Noto Color Emoji", Weight.REGULAR, Style.UPRIGHT);

    /// The two weights the design system ships, by name.
    ///
    /// The bundled faces are this pair, because the design system specifies
    /// two. Everything that takes a weight takes a CSS number too; these are
    /// the two numbers worth a name. `font-weight: 700` resolves to the nearer
    /// bundled face, the way CSS's own matching algorithm resolves a weight no
    /// face provides — so a stylesheet that asks for bold gets SemiBold rather
    /// than nothing.
    public enum Weight {

        /// 400. `body`, `caption`, `mono`.
        REGULAR(400),

        /// 600. `display`, `title`, `heading`, `body-strong`.
        SEMI_BOLD(600);

        private final int value;

        Weight(int value) {
            this.value = value;
        }

        /// The CSS number — 400 or 600.
        public int value() {
            return value;
        }

        /// The bundled weight nearest to `css`.
        ///
        /// [Face#match]'s rule over these two: everything at or below 500 is
        /// regular, everything above is semi-bold. `bold` (700) and `black`
        /// (900) both land on SemiBold, which is the honest answer — the
        /// alternative is a heading that silently renders at 400.
        public static Weight nearest(double css) {
            return css > 500 ? SEMI_BOLD : REGULAR;
        }
    }

    /// Upright or italic — CSS's `font-style`, in the two values a shipped face
    /// can honour.
    ///
    /// **`oblique` is absent and it is not an omission.** CSS's `oblique` asks for
    /// a *slant*, which without a slanted face means shearing the upright glyphs;
    /// Inter's italic is a different drawing rather than a sheared one, so
    /// answering `oblique` with it would be answering a different question, and
    /// answering it with a shear would be a type-design decision taken by a
    /// stylesheet. A declaration that writes it is dropped with the usual
    /// warning.
    public enum Style {

        /// Roman — CSS's `normal`, and what every face did before this existed.
        UPRIGHT,

        /// Italic — a drawn face, with its own letterforms.
        ITALIC;

        /// The name as it is written in CSS — `normal`, not `UPRIGHT`.
        public String cssName() {
            return this == UPRIGHT ? "normal" : "italic";
        }
    }

    /// Every face, once: [#of] runs per text node per restyle, and `values()`
    /// copies its array on every call.
    private static final List<BundledFont> ALL = List.of(values());

    private final String resource;
    private final String family;
    private final Weight weight;
    private final Style style;

    BundledFont(String resource, String family, Weight weight, Style style) {
        this.resource = resource;
        this.family = family;
        this.weight = weight;
        this.style = style;
    }

    /// The family name as the font itself declares it.
    @Override
    public String family() {
        return family;
    }

    /// The CSS weight this face is drawn at: 400 or 600.
    @Override
    public int weight() {
        return weight.value();
    }

    /// Whether this face is upright or italic.
    @Override
    public Style style() {
        return style;
    }

    /// The bundled face for a family and a weight, upright.
    public static @Nullable BundledFont of(String family, Weight weight) {
        return of(family, weight, Style.UPRIGHT);
    }

    /// The bundled face for a family, a weight and a style.
    ///
    /// **Style before weight**, which is CSS's own font-matching order (family,
    /// then style, then weight) and the one that reads right: a family that has the
    /// style at another weight uses it, because a slant is what a reader was told
    /// to look for. A family with **no** such style falls back to the weight it was
    /// asked for, upright — `JetBrains Mono` ships one face, so italic code stays
    /// upright code rather than becoming italic Inter, which the family filter has
    /// already ruled out anyway.
    public static @Nullable BundledFont of(String family, Weight weight, Style style) {
        return of(family, weight.value(), style);
    }

    /// The bundled face for a family, a CSS weight and a style: the nearest one,
    /// by [Face#match].
    ///
    /// @param weight 1 to 1000
    /// @throws IllegalArgumentException if the weight is outside 1 to 1000
    public static @Nullable BundledFont of(String family, int weight, Style style) {
        // The rule is [Face#match]'s, shared with the faces an application ships,
        // so a shipped family falls back exactly the way Inter does.
        return Face.match(ALL, family, weight, style);
    }

    String resource() {
        return resource;
    }
}
