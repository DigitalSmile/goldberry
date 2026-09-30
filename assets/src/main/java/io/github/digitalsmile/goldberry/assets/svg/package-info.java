/// The slice of SVG the icon compiler needs: the basic shapes converted into path
/// data as the specification defines them, and enough of the path-data grammar to
/// join one shape's run to the next safely.
///
/// It handles what Lucide uses and no more. The icon compiler in `…assets.prepare`
/// is the caller.
package io.github.digitalsmile.goldberry.assets.svg;
