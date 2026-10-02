/// The structural widgets every layout is built from — `row`, `column`, `stack`
/// and `spacer` — which paint nothing of their own.
///
/// [dev.goldberry.widgets.core.Primitives] lists the structural node names a
/// document may write. The subpackages hold the other structural widgets, one
/// per package: `affix`, `canvas`, `image`, `qr-code`, `scroll` and the embedded
/// `web-view`. Annotated `@NullMarked`: every type here is non-null unless it
/// says `@Nullable`.
///
/// Read more: [How layout works](https://goldberry.dev/docs/layout/index.html#the-layout-widgets)
/// and [The catalogue](https://goldberry.dev/docs/components/index.html#the-catalogue).
@NullMarked
package dev.goldberry.widgets.core;

import org.jspecify.annotations.NullMarked;
