/// A private D-Bus connection through libdbus loaded at run time: the
/// iterator API behind a writer and a reader of plain Java values, method
/// calls with their replies and errors, signals both ways, and the serving
/// side a test's fake service needs.
///
/// Not exported. The freedesktop notifier and the Secret Service keychain use
/// it and put their own words on what crosses.
@org.jspecify.annotations.NullMarked
package dev.goldberry.natives.desktop.dbus;
