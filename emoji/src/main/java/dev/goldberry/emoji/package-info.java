/// Noto Color Emoji, the toolkit's emoji face, as a provider of `:core`'s
/// `EmojiFont` service.
///
/// Exported. An application that adds this artifact gets emoji in every paragraph it
/// draws and names no type of it; one that does not carries no emoji face at all
/// (ADR-0384, ADR-0456). The provider's `CREDIT` constant is the line an About box
/// shows.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.emoji;

import org.jspecify.annotations.NullMarked;
