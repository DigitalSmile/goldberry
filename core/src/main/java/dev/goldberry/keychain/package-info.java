/// Secrets in the system's own store: [Keychain][dev.goldberry.keychain.Keychain]
/// keeps one secret per service and account in the Secret Service on Linux,
/// the Keychain on macOS and the Credential Manager on Windows, and
/// [Keychain#inMemory()][dev.goldberry.keychain.Keychain#inMemory()] stands in
/// for them in a test.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html#the-low-level-path).
@NullMarked
package dev.goldberry.keychain;

import org.jspecify.annotations.NullMarked;
