package io.github.digitalsmile.goldberry.widgets.core.qrcode;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.qr.Level;
import io.github.digitalsmile.goldberry.qr.QrMatrix;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// A QR code for `payload` — `docs/core-widgets.md` §1's `qr-code`.
///
/// ```kdl
/// qr-code value="tg://login?token=…" level="M" quiet-zone=4 \
///         name="QR code to sign in to Telegram"
/// ```
///
/// ```java
/// new QrCode(link).level(Level.H).withAttributes(Attributes.NONE.name("Scan to sign in"));
/// ```
///
/// The picture is [io.github.digitalsmile.goldberry.qr.QrEncoder]'s and the
/// arithmetic that puts it on a pixel grid is [QrModules]'s. What is left here
/// is a widget: a value that says which code, how much margin, and nothing else.
///
/// ## Modules are whole device pixels
///
/// The one promise this widget makes beyond drawing the right squares. A camera
/// thresholds a photograph and samples the middle of each module, and a module
/// whose edge fell between two device pixels would be drawn half-dark down one
/// side — so the box is divided into a whole number of pixels per module and the
/// remainder becomes margin. What changes between 100%, 150% and 200% is how
/// many pixels a module is, never whether its edges land on one — and never how
/// big the code is on the box, which is [QrModules]'s second and less obvious
/// half.
///
/// A box too small for one logical pixel per module draws nothing, because a
/// 21-module code in sixteen pixels is a grey square rather than a small code.
///
/// ## It is near-black on white in both themes
///
/// `--gb-qr-ink` and `--gb-qr-paper`, and they are the **same two values in the
/// dark theme as in the light one**. Plenty of phone scanners will not read an
/// inverted code — the specification's own module convention is dark on light —
/// so a code that flipped with the theme would be a code that stopped working at
/// night. The quiet zone is painted in the paper colour for the same reason: it
/// is part of the code, and a code drawn straight onto a themed surface has no
/// quiet zone wherever that surface is not white.
///
/// ## Its size is the stylesheet's
///
/// Like `image`, and unlike a chart: a QR code has no natural size in pixels, it
/// has a size in **modules**, and how big a module should be is a question about
/// the screen it is on. The default sheet gives it 160×160 and an application
/// overrides it with `width` and `height` like anything else.
///
/// ## The payload is not read out
///
/// The semantics are an image's — [Role#FIGURE], named by the application's
/// `name=` — and the payload is deliberately not part of the accessible name. A
/// sign-in token is a credential for as long as it is valid, and a screen reader
/// announcing it aloud in an open-plan office is the reason.
///
/// ## Rebuilding does not re-encode
///
/// The dialog this exists for is rebuilt on every keystroke in every field on
/// it. [QrCache] keeps the last few codes and hands the same matrix back, so a
/// rebuild with the same payload costs a map lookup — and a rebuild with a new
/// one, which is what happens when the token is renewed, encodes exactly once.
///
/// @param payload   what a scanner will read, encoded as its UTF-8
/// @param level     how much of the code may be destroyed and still read
/// @param quietZone the light margin around the code, in modules; §6.3 says four
/// @param attributes `id`, `class` and the `name=` a reader is given
@Markup("qr-code")
public record QrCode(String payload, Level level, int quietZone, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Semantics, Attributed<QrCode> {

    /// §6.3's quiet zone: "a region four modules wide which shall be free of all
    /// other markings". The default, and the smallest value anybody should use.
    public static final int STANDARD_QUIET_ZONE = 4;

    /// The ink when no theme says otherwise — near-black rather than black,
    /// which is every other dark value in this toolkit's reasoning: pure black
    /// on pure white is harsher than any scanner needs.
    public static final int DEFAULT_INK = 0xFF1A1A1A;

    /// The paper when no theme says otherwise. White, in both themes.
    public static final int DEFAULT_PAPER = 0xFFFFFFFF;

    /// The token an application recolours the dark modules with.
    private static final String INK_TOKEN = "--gb-qr-ink";

    /// The token for the light modules and the quiet zone.
    private static final String PAPER_TOKEN = "--gb-qr-paper";

    public QrCode {
        Objects.requireNonNull(payload, "payload");
        level = level == null ? Level.M : level;
        attributes = attributes == null ? Attributes.NONE : attributes;
        if (quietZone < 0) {
            throw new IllegalArgumentException("a quiet zone is a number of modules, not " + quietZone);
        }
        // Encoded here, so that a payload no version holds is a refusal where
        // the widget was built rather than an empty square at paint time. It
        // costs nothing to do it twice: the second call is the cache's lookup,
        // and so is every rebuild after it.
        QrCache.matrix(payload, level);
    }

    /// A code for `payload` at level M with §6.3's four-module quiet zone.
    public QrCode(String payload) {
        this(payload, Level.M, STANDARD_QUIET_ZONE, Attributes.NONE);
    }

    /// This code at another error correction level.
    public QrCode level(Level value) {
        return new QrCode(payload, Objects.requireNonNull(value, "value"), quietZone, attributes);
    }

    /// This code with a different light margin, in modules.
    ///
    /// Four is the standard's and the default. Zero is legal here and is for one
    /// case only: a code laid out inside something that already leaves it four
    /// modules of white. A code with no quiet zone on a coloured surface does
    /// not scan.
    public QrCode quietZone(int modules) {
        return new QrCode(payload, level, modules, attributes);
    }

    /// The code itself — the modules, without the quiet zone.
    ///
    /// The same instance for as long as the payload is the same, which is what
    /// makes a rebuild free.
    public QrMatrix matrix() {
        return QrCache.matrix(payload, level);
    }

    @Override
    public String cssType() {
        return "qr-code";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public QrCode withAttributes(Attributes value) {
        return new QrCode(payload, level, quietZone, value);
    }

    @Override
    public Role role() {
        return Role.FIGURE;
    }

    /// What a reader is told this picture is.
    ///
    /// The application's `name=` and never the payload. "QR code to sign in to
    /// Telegram" is what somebody needs to hear; the token is a credential.
    @Override
    public @Nullable String accessibleName() {
        return attributes.name();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // Read here rather than in the painter: the token accessors answer for
        // the node being rendered, and a context held until paint time would
        // answer for whichever node rendered last.
        var ink = context.color(INK_TOKEN, DEFAULT_INK);
        var paper = context.color(PAPER_TOKEN, DEFAULT_PAPER);
        var matrix = matrix();
        return Box.of()
                .style(style)
                .painting((frame, size) -> QrModules.paint(frame, size, matrix, quietZone, ink, paper));
    }

    /// Builds a `qr-code` from markup.
    ///
    /// `value=` is the payload, as it is on every other valued node in §9.
    /// `level=` is one of `L`, `M`, `Q`, `H` and falls back to `M` when it is
    /// something else — a document is reloaded on every keystroke while it is
    /// being written, and a half-typed level should not take the window down.
    /// A payload that no version holds still throws, because that is not a typo
    /// that fixes itself on the next character.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var level = Level.named(node.stringProperty("level"));
        var quietZone = (int) node.numberProperty("quiet-zone", STANDARD_QUIET_ZONE);
        return new QrCode(
                Objects.requireNonNullElse(node.stringProperty("value"), ""),
                level == null ? Level.M : level,
                quietZone,
                Attributes.of(node));
    }
}
