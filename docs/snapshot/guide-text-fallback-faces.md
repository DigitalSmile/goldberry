<!-- Two pieces for book/src/guide/text.md, each says where it goes. -->

<!-- 1. Destination: book/src/guide/text.md, `### The bundled faces`, the last
     paragraph. Replace its last sentence, "A glyph neither family has draws
     `.notdef`: there is no fallback cascade beyond the emoji slot, so
     `font-family: Inter, sans-serif` keeps the first name only.", with: -->

`font-family: Inter, sans-serif` keeps the first name only. A character
neither family has is drawn in a fallback face when the application has one
(below), and as `.notdef` when it does not.

<!-- 2. Destination: book/src/guide/text.md, a new section after
     `### Shipping a face` and before `## Paragraphs`. -->

### Fallback faces

```java
@Override public List<FallbackSource> fallbacks() {
    return List.of(
            FallbackSource.of(face("Noto Sans SC", "fonts/NotoSansSC-Regular.otf"),
                    UnicodeScript.HAN, UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA),
            FallbackSource.of(face("Noto Sans Arabic", "fonts/NotoSansArabic-Regular.ttf"),
                    UnicodeScript.ARABIC),
            FallbackSource.of(face("Noto Sans Math", "fonts/NotoSansMath-Regular.ttf")));
}

private static FontSource face(String family, String file) {
    return FontSource.stream(family, 400, BundledFont.Style.UPRIGHT, () -> MyApp.class.getResourceAsStream(file));
}
```

A name typed in a script the design system's faces lack is drawn in the
first fallback that has its characters, at the size and on the line of the
text around it. Without one it is a row of `.notdef` boxes. A fallback is not
a family a stylesheet names: `font-family: Inter` stays Inter for every
character Inter has, and only the characters it lacks look further.

**The face's own `cmap` decides.** Every face's characters are read once, when
it opens, and a paragraph checks its text against them before shaping. Text
the face covers is shaped exactly as it was, and a paragraph with no
uncovered character never opens a fallback. The scripts after the source are
a hint read before the face is opened, so an Arabic name does not open a Han
face to learn that it has no Arabic. Characters in no script of their own,
such as punctuation, digits and the mathematical letters, are never ruled out
by a hint, and a source with no scripts is opened for anything.

**A run stays whole.** A letter and the marks on it go to one face, and a word
of Arabic or a name in Han is shaped as one run, spaces included, so it joins
and spaces as it should. Right-to-left text is still shaped in logical order
and drawn mirrored, as in any other face.

**The line is the font's.** Line height, ascent and underlines are the
font's that the stylesheet chose. A fallback glyph that is taller may reach
past the line box, as it can in a browser. Carets, selection and clicks work
across a fallback run as across any other.

**Order, weight and style.** Fallbacks are searched in the order given. Several
sources with one family name are one fallback at several weights, and bold
text takes the nearest weight by the rule `font-family` uses. Nothing is
emboldened or slanted by the toolkit: a family shipped only in regular draws
bold text in regular. An artifact can bring fallback faces as a
`dev.goldberry.assets.FallbackFont` provider, searched after the
application's. A font collection (`.ttc`) is not read: ship one face of it.
A book opened by hand takes the list as `Fonts.bundled(shipped, fallbacks)`.
