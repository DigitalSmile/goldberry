# Guide text held back: pictures in stylesheets, and regions of a sheet

Three pieces, for the release commit. Each says where it goes.

## 1. `book/src/guide/styling.md`, `### Backgrounds and gradients`

Add to the first sample:

```css
.panel   { background: url("classpath:/ui/leather.png") repeat, #3b2a1e }
.hero    { background-image: url("classpath:/ui/hero.png"); background-size: cover; background-repeat: no-repeat }
```

Replace the paragraph that begins "Every layer is filled over the whole box"
with:

> A layer may also be a picture, `url("…")`, read as an `image`'s `src` is:
> `classpath:` names a resource on the application's class loader, and
> anything else is a file. In a sheet read with `Stylesheet.resource`, a name
> with no scheme is a resource beside the sheet, and one starting with `/` is
> from the root. On a display above 100% the picture's `@2x` variant is drawn
> when there is one, at the same size. A `#xywh=x,y,width,height` fragment is
> that rectangle of the picture, so one sprite sheet serves every box. Only the
> quoted form is read: `url(ui/leather.png)` drops the declaration. Write a
> file path with forward slashes on every system: inside a CSS string a
> backslash begins an escape, so `C:\Users\…` does not name the file it
> appears to, and Windows reads `C:/Users/…` as the same path.
>
> `background-size` is `auto` (the picture's own size), `cover`, `contain`, or
> one or two lengths or percentages of the box, with `auto` for an axis that
> follows the picture's shape. `background-repeat` is `repeat`, `no-repeat`,
> `repeat-x` or `repeat-y`, or `repeat` and `no-repeat` for the two axes. Both
> are comma lists matched to the layers in order, repeated when shorter. The
> `background` shorthand reads a picture and its repeat, and resets size and
> repeat to `auto` and `repeat`; it does not read a size or a position.
>
> Every layer is clipped to the box's shape: a gradient or a picture on a
> rounded card is rounded. A gradient is filled over the whole box whatever
> `background-size` says. A picture is decoded off the frame, the same decode
> an `image` of that file uses, and drawn from the frame after it arrives;
> until then the colour and the gradients show. There is no
> `background-clip`, `background-origin` or `background-attachment`, and
> `space` and `round` are not read. `background-position` moves every layer by
> the lengths it names. A percentage or a keyword moves nothing, so a picture
> that is not tiled sits at the top left unless a length moves it. It is
> animatable, which is how the stripe above marches. Transition hints (a bare
> position between two stops) and colour interpolation methods are not read,
> and drop the declaration.

## 2. `book/src/guide/styling.md`, at the end of `### Border, outline and shadow`

> A border may also be a picture cut in nine, `border-image`:
>
> ```css
> #menu-panel  { border-image: url("classpath:/ui/panel.png") 48 fill / 48px stretch }
> button       { border-image: url("classpath:/ui/button.png") 12 fill / 12px }
> button:hover { border-image-source: url("classpath:/ui/button-hover.png") }
> ```
>
> `border-image-slice` is one to four numbers or percentages, top first, in the
> picture's pixels: they cut it into four corners, four edges and a middle,
> and `fill` draws the middle too. `border-image-width` says how wide each
> side's pieces are drawn: a number of border widths (`1`, the default), a
> length, a percentage, or `auto` for the slice's own size.
> `border-image-outset` pushes the picture out past the box by lengths or
> border widths. `border-image-repeat` is `stretch` (the default), `repeat`
> or `round` for the edges and the middle, one value or one per axis.
> `border-image-source` is `none` or a `url()`, read as a background picture
> is, `@2x` variant and `#xywh=` fragment included. The `border-image`
> shorthand is `<source> <slice> / <width> / <outset> <repeat>`, in any
> order with the slashes after the slice, and resets what it does not name.
>
> The corners are drawn at the size the width gives, so a 48-pixel corner
> drawn 48 wide is the picture pixel for pixel, at 200% from its `@2x`
> variant. The picture replaces the border's colours, and the border's widths
> still decide the layout. While the picture loads, or when it cannot be read,
> the border is drawn from its colours. A border image is not clipped by
> `border-radius`. Because it is a property like any other, a state's rule
> swaps the sprite, as the `:hover` rule above does.

## 3. `book/src/components/drawing.md`, `## image`

After the paragraph that begins "An `ImageSource` is a `file(path)`", add:

> One picture of a sheet of them is a region: `src="classpath:/ui/kit.png#xywh=29,36,718,306"`
> in markup, `ImageSource.region(sheet, PhysicalRect.of(29, 36, 718, 306))` in
> code. The rectangle is in the sheet's pixels, and every region of one sheet
> shares the sheet's single decode. A fragment whose numbers cannot be read is
> refused when the document is inflated. `Image.cropped(rect)` copies a
> rectangle out of a decoded picture for code that composes them.

In `### Attributes`, the `src` row's text becomes:

> One path. `classpath:` names a resource on the application's class loader,
> and `#xywh=x,y,width,height` shows that rectangle of it. Exactly one of `src`
> and `srcset` is required
