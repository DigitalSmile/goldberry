package dev.goldberry.text.itemize;

import java.util.Objects;

/// A stretch of one string that one face draws, because that face has its
/// characters.
///
/// [TextRun]'s sibling for the split [Itemizer#byCoverage] makes, which is
/// decided by the faces rather than by the text, so the answer is the face
/// itself rather than a [Slot].
///
/// Read more: [Emoji](https://goldberry.dev/docs/guide/text.html#emoji).
///
/// @param <F>   what a face is to the caller
/// @param start the first character, inclusive
/// @param end   one past the last
/// @param face  the face that draws it
public record FaceRun<F>(int start, int end, F face) {

    public FaceRun {
        Objects.requireNonNull(face, "face");
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("a run cannot end before it starts: " + start + ".." + end);
        }
    }
}
