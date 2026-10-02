/// The platform calls behind [dev.goldberry.natives.desktop.DesktopMotion],
/// and the crossing they share.
///
/// **Not exported**, like every `…calls` package in this module: a `call` here
/// takes and returns raw addresses, so exporting one would put the foreign
/// boundary in an application's reach. What leaves this module is an enum with
/// three constants in it.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@org.jspecify.annotations.NullMarked
package dev.goldberry.natives.desktop.calls;
