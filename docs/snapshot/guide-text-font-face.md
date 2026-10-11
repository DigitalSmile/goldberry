<!-- Destination: `book/src/guide/text.md`, `### Shipping a face`, after the
     paragraph that starts "`Application.fonts()` is read once". Written
     2026-10-11 with CSS-32 of docs/css-plan.md. -->

A stylesheet may ship a face too, with `@font-face`:

```css
@font-face {
  font-family: "Forum";
  src: url("fonts/Forum-Regular.ttf") format("truetype");
  font-weight: 400;
}
```

A renderer adds the faces its sheets name to the window's book when it is
built, as if `Application.fonts()` had answered them, and `font-family:
Forum` then finds them. In a sheet read with `Stylesheet.resource`, `src` is a
file beside the sheet; otherwise it is a `classpath:` address or a file path.
TrueType and OpenType files are read; `local()` and WOFF are passed over for
the next source in the list. `font-weight` is a number, `normal` or `bold`,
and `font-style` is `normal` or `italic`.
