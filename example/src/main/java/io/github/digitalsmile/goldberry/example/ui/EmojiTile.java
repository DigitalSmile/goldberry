package io.github.digitalsmile.goldberry.example.ui;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One cell of the [EmojiScreen]'s sheet: the character, and what Unicode calls
/// it.
///
/// ## Why it is not an [IconTile]
///
/// They look alike and are not the same thing. An icon is a **path** an
/// application holds — `Box.icon(icon, colour)` — and the tile is handed the
/// value. An emoji is **text**: a code point drawn through a face, which means
/// the glyph is a `Box.text` whose `font-family` the stylesheet chooses, and
/// what this tile carries is a `String` rather than an object with a lifetime.
///
/// That difference is the demonstration. `.emoji-glyph { font-family: OpenMoji }`
/// is the whole of how an application reaches the emoji slot in §6.1's font
/// chain — no API, no registry, one declaration — and the face behind it is
/// `goldberry-emoji`, which this application opts into ([ADR-0384]).
///
/// @param character  the emoji itself, as a string because a code point above
///                   the BMP is two chars
/// @param name       what Unicode calls it, which is the caption and what a
///                   search matches on
record EmojiTile(String character, String name, Attributes attributes) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "emoji-tile";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    /// Both halves are children, for [IconTile]'s reason: a box with text is a
    /// measured leaf and Yoga never lays a measured node's children out, so a
    /// tile that drew its own glyph would have nowhere to put the caption.
    @Override
    public List<Widget> children() {
        return List.of(new EmojiGlyph(character), new EmojiName(name));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// The character, drawn through whatever `font-family` the stylesheet picks
    /// for it — which is `OpenMoji`.
    record EmojiGlyph(String character) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "emoji-glyph";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(Box.text(context.paragraph(style, character), style.color()));
        }
    }

    /// The Unicode name under it, `caption`-ranked and muted.
    record EmojiName(String text) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "emoji-tile-name";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(Box.text(context.paragraph(style, text), style.color()));
        }
    }
}
