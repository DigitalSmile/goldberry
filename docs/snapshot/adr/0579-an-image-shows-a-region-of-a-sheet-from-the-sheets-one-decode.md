# ADR-0579: An image shows a region of a sheet, from the sheet's one decode

- **Status:** Accepted
- **Date:** 2026-10-08
- **Relates to:** the Gwent clone's issue list (GB-026)

## Context

An `image` drew the whole of its source. `ImageView` had `src`, `srcset`,
`alt`, `decorative`, `fit` and `loader`, and `Image` had `scaled` and no
crop. A sprite sheet with a manifest of rectangles, which is how the
downstream's UI kit and its character portraits are painted, could only be
shown by cutting it into files first, at a decode and a cache entry each.

## Decision

**`Image.cropped(PhysicalRect)`** in `:core`: a copy of the rectangle's rows
into a picture of its own. The rectangle must be non-empty and inside the
image, and is refused otherwise rather than cut down, for the reason
`Frame.drawImage`'s source rectangle is: its numbers came from a manifest
that no longer matches the sheet. The whole image answers itself, as
`scaled` does for its own size.

**`ImageAddress`** in `dev.goldberry.image`: a path and an optional
`#xywh=x,y,width,height` fragment (Media Fragments, with `pixel:` allowed),
parsed once for markup and stylesheets. Only `#xywh=` is a fragment, so a
`#` elsewhere in a file name is part of the path. A fragment that is not four
whole numbers with a positive size is an `IllegalArgumentException`.
`atDensity(2)` gives the `@2x` variant's address with the region doubled,
which the stylesheet uses.

**`ImageSource.region(ImageSource sheet, PhysicalRect rect)`**, a new
`Region` record of the sealed interface. Its key is the sheet's key plus the
fragment. `ImageCache` never reads a region: it loads the sheet under the
sheet's own key and caches the cut beside it, so every region of one sheet
shares one decode. `Region.load()`, which the uncached loaders call, decodes
the sheet and cuts. A region of an image in hand has no key and is cut on the
calling thread.

**Markup**: `ImageSource.parse` reads the fragment, so
`src="classpath:/ui/kit.png#xywh=29,36,718,306"` is a region, and so is each
candidate of a `srcset`. The `srcset` splitter now follows HTML's rule — a
path runs to the first whitespace — because the fragment's commas used to
split candidates. A malformed fragment fails `ImageView.inflate`, so the
document is refused when it is inflated.

## Alternatives considered

- **A `region` attribute on `image`** beside `src`. Two attributes that only
  mean something together, and nothing for `srcset`, where each candidate
  needs its own rectangle at its own density. The fragment travels with the
  path it qualifies.
- **Drawing the crop at the blit** instead of copying it. `ImagePaint` and
  `Fit` would each need to carry a source rectangle through `contain`,
  `cover` and the natural size, for a copy that is paid once per region and
  then cached. The copy keeps a region an ordinary picture everywhere below
  the loader.
- **Caching only the sheet** and cutting on every request. The cut is cheap,
  but a view that asks again on a rebuild would get a new picture each time;
  caching the cut keeps one picture per region, as one per file.

## Consequences

- `ImageCacheTest` checks that two regions of one sheet start one load under
  the sheet's key, that a region off the edge fails and is forgotten, and
  the region's key. `ImageCroppedTest` holds the bounds, `ImageAddressTest`
  the fragment, `ImageViewTest` the markup, and the `image-regions` golden
  two sprites cut from one sheet.
- The sealed `ImageSource` has a sixth record; nothing outside the package
  switched over it.
- A CSS `url()` takes the same fragment, so a sprite sheet serves the
  stylesheet as well as the `image` widget.
