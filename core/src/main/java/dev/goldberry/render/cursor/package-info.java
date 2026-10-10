/// The backend SPI's cursor pictures: the pixels an application gave a
/// cursor shape, at each of the sizes it drew.
///
/// `CursorPicture` is one size of one shape, with its hot spot, and
/// `CursorPictures` is every size of a shape with the rule that picks one for
/// a display scale. A backend that can show a picture under the pointer takes
/// them through `Backend.setCursorPictures`; one that cannot shows its own
/// shapes. Exported to every module.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#the-cursor).
@NullMarked
package dev.goldberry.render.cursor;

import org.jspecify.annotations.NullMarked;
