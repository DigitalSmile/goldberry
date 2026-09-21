package io.github.digitalsmile.goldberry.example.webpump;

import java.util.Optional;

/// The four numbers [ProbeDocument] sends, read out of the JSON array the engine
/// hands over.
///
/// Deliberately **not** a JSON parser. The toolkit ships no reader and binding
/// one for the sake of a probe would be the dependency [ADR-0448] declined to
/// take on. What this handles is the one shape this document sends — four
/// numbers in an array — and anything else is refused rather than guessed at.
///
/// @param rafFps      `requestAnimationFrame` callbacks per second
/// @param timelineFps how often `document.timeline` advanced per second, which
///        is one tick per rendering update
/// @param timerFps    `setInterval` callbacks per second
/// @param visible     what `document.visibilityState` said
record ProbeArguments(double rafFps, double timelineFps, double timerFps, boolean visible) {

    /// How many numbers the document sends.
    private static final int FIELDS = 4;

    /// Reads `[1.5,63,125,1]`, or empty for anything that is not that shape.
    ///
    /// Empty rather than an exception: this runs on the UI thread inside a
    /// callback the page is awaiting, and a probe that killed the run over a
    /// malformed reading would lose the readings that came before it.
    static Optional<ProbeArguments> parse(String arguments) {
        if (arguments == null) {
            return Optional.empty();
        }
        var text = arguments.strip();
        if (!text.startsWith("[") || !text.endsWith("]")) {
            return Optional.empty();
        }
        var body = text.substring(1, text.length() - 1);
        // -1 so that a trailing empty field is kept and the arity check below
        // rejects it, rather than being dropped into a shape that looks right.
        @SuppressWarnings("StringSplitter")
        var parts = body.split(",", -1);
        if (parts.length != FIELDS) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ProbeArguments(
                    Double.parseDouble(parts[0].strip()),
                    Double.parseDouble(parts[1].strip()),
                    Double.parseDouble(parts[2].strip()),
                    // JavaScript's `1` and `0`, not `true` and `false`: the
                    // document sends a number so that all four values read the
                    // same way, and `0` is the only falsehood.
                    Double.parseDouble(parts[3].strip()) != 0));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
