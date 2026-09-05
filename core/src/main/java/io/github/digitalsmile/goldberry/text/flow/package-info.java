/// How a paragraph sits in the box it is drawn in.
///
/// Three CSS properties and the value that carries them together: `white-space`,
/// which decides whether a paragraph may break a line the author did not;
/// `text-overflow`, which decides what marks a line that was not broken and did
/// not fit; and `text-align`, which decides where a line that fitted easily sits
/// in the room left over. The first two are the too-wide question and the third
/// is the too-narrow one, and all three are answered in the same place — the
/// paint, which is the only code that has both the line's width and the box's.
///
/// Its own package rather than three more types in
/// [io.github.digitalsmile.goldberry.text], for the reason ADR-0172 gives for the
/// rest of the split: `text` is the crossing between HarfBuzz's shaping and
/// Blend2D's rasterizer, and these are neither — they are *style*, read by the
/// paragraph and written by the cascade. Beside the paragraph rather than under
/// `css`, because what they mean is a fact about text layout and the CSS spelling
/// is one way in.
///
/// See [ADR-0255] for why this exists and [ADR-0256] for the third property,
/// and [io.github.digitalsmile.goldberry.text.flow.TextFlow] for why the cascade
/// carries them apart and everything below it sees one value.
@NullMarked
package io.github.digitalsmile.goldberry.text.flow;

import org.jspecify.annotations.NullMarked;
