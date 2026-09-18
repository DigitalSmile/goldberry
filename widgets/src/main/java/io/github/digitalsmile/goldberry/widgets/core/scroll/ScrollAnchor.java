package io.github.digitalsmile.goldberry.widgets.core.scroll;

import java.util.Locale;

/// Where a [Scroll] sits when it is first laid out, and where it stays as its
/// content changes — `docs/gaps.md` G48.
///
/// Two values because there are two kinds of document. A page is read from the
/// top and grows at the bottom; a log, a console and a chat are read from the
/// bottom and the newest line is the one that matters. Everything else about the
/// two is identical, which is why this is an attribute on `scroll` rather than a
/// second widget (ADR-0392).
public enum ScrollAnchor {

    /// Offset zero, and nothing keeps it there. Today's behaviour, and the
    /// default: the overwhelming majority of viewports hold a document.
    START,

    /// Opens at the end, and **sticks** there while it is already at the end.
    ///
    /// A message arriving while the reader is at the bottom scrolls; one
    /// arriving while they are reading history does not move them. See
    /// [ScrollStick] for what "at the end" is worth to the nearest pixel.
    END;

    /// Whether a viewport with this anchor preserves its offset when content is
    /// inserted above it, unless the caller said otherwise.
    ///
    /// On for [#END] and off for [#START], which is what makes
    /// [Scroll#preserveOnPrepend(boolean)] a **tri-state**: the answer depends on
    /// something else, so "unset" cannot be spelled as either boolean.
    public boolean preservesOnPrepend() {
        return this == END;
    }

    /// The anchor `name` spells, for KDL's `anchor=` — defaulting to [#START]
    /// rather than throwing, for [ScrollAxis#parse]'s reason: a document that
    /// misspells an attribute should still show its content.
    public static ScrollAnchor parse(String name) {
        if (name == null) {
            return START;
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "end", "bottom" -> END;
            default -> START;
        };
    }
}
