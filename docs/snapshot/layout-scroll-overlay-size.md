# The overlay scroll bar's size (ADR-0629)

Three pieces. Each says where it goes.

## `book/src/layout/scroll.md`, `### Scrollbars`, first paragraph

Replace the first two sentences' numbers:

> The default bar is an overlay. A 4 px thumb floats on the content, the track is transparent, and nothing costs the content any width. When the pointer is over the viewport the thumb widens to 8 px and the track takes `--gb-surface-2`.

## `book/src/layout/scroll.md`, the token table

| Token | Overlay | Always |
|---|---|---|
| `--gb-scrollbar-size` | `8px` | `12px` |
| `--gb-scroll-thumb-size` | `4px` | `8px` |
| `--gb-scroll-thumb-hover-size` | `8px` | `8px` |

## `book/src/layout/sizing.md`, the `--gb-scrollbar-size` row

| `--gb-scrollbar-size` | `8px`, `12px` with `Scrollbars.ALWAYS` | The bar's thickness |
