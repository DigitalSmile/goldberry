/// `docs/core-widgets.md` §1's `image` — a picture from a file, a resource,
/// bytes or an application's own loader, drawn without blocking a frame
/// (ADR-0358).
///
/// [io.github.digitalsmile.goldberry.widgets.core.image.ImageView] is the widget,
/// [io.github.digitalsmile.goldberry.widgets.core.image.ImageSource] says where
/// its pixels come from, [io.github.digitalsmile.goldberry.widgets.core.image.Fit]
/// how they fill a box, and
/// [io.github.digitalsmile.goldberry.widgets.core.image.ImageLoader] when they
/// arrive. Everything else here is a part.
@NullMarked
package io.github.digitalsmile.goldberry.widgets.core.image;

import org.jspecify.annotations.NullMarked;
