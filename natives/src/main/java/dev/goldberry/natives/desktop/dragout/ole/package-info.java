/// OLE's half of a drag out on Windows: the `IDataObject`, `IEnumFORMATETC`
/// and `IDropSource` the toolkit hands `DoDragDrop`, written in Java over
/// [ComServer][dev.goldberry.natives.desktop.windows.ComServer], the shell's
/// drag image helper, and the calls around them.
///
/// Not exported. [OleDragSource][dev.goldberry.natives.desktop.dragout.OleDragSource]
/// is what `:core` starts a drag with, and it puts its own words on what crosses.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.desktop.dragout.ole;

import org.jspecify.annotations.NullMarked;
