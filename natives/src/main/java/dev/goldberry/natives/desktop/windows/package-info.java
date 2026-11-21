/// What more than one Windows binding needs: [Com][dev.goldberry.natives.desktop.windows.Com],
/// which creates a COM object and calls through its vtable.
///
/// Not exported. The bindings that use it are, and they put their own words on
/// what crosses.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@org.jspecify.annotations.NullMarked
package dev.goldberry.natives.desktop.windows;
