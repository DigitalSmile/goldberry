/// The platform calls behind [io.github.digitalsmile.goldberry.natives.desktop.DesktopMotion],
/// and the crossing they share.
///
/// **Not exported**, which is ADR-0173's rule for every holder in this module:
/// a `call` here takes and returns raw addresses, so exporting one would put the
/// foreign boundary in an application's reach. What leaves this module is an
/// enum with three constants in it.
@org.jspecify.annotations.NullMarked
package io.github.digitalsmile.goldberry.natives.desktop.calls;
