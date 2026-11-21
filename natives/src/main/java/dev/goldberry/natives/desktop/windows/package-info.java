/// What more than one Windows binding needs: [Com][dev.goldberry.natives.desktop.windows.Com],
/// which creates a COM object and calls through its vtable;
/// [ComServer][dev.goldberry.natives.desktop.windows.ComServer], a COM object
/// written in Java; and [GlobalMemory][dev.goldberry.natives.desktop.windows.GlobalMemory],
/// the `HGLOBAL` such an object hands its bytes over in.
///
/// Not exported. The bindings that use it are, and they put their own words on
/// what crosses.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@org.jspecify.annotations.NullMarked
package dev.goldberry.natives.desktop.windows;
