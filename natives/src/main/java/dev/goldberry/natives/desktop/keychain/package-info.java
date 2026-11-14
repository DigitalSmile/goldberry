/// The system's secret store, one platform at a time: the freedesktop Secret
/// Service over D-Bus on Linux (gnome-keyring, KWallet, KeePassXC), the
/// Keychain through `Security.framework` on macOS, and the Credential Manager
/// through `advapi32` on Windows.
///
/// Each is a binding against a library the process already has, loaded at run
/// time, so nothing here is in the native build, and each says why when it
/// cannot store: no service, the user refused, or the store failed.
///
/// Exported to `:core` alone, which puts the toolkit's own words on it.
@org.jspecify.annotations.NullMarked
package dev.goldberry.natives.desktop.keychain;
