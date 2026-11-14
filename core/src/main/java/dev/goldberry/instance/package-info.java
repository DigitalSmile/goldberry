/// One running copy of an application: [SingleInstance][dev.goldberry.instance.SingleInstance]
/// holds a lock and a local socket, in plain Java, and hands a second launch's
/// command line to the first. Exported to applications; the launcher uses it
/// for an application that names a single-instance id.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html#the-low-level-path).
@NullMarked
package dev.goldberry.instance;

import org.jspecify.annotations.NullMarked;
