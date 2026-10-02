package dev.goldberry.natives.harfbuzz;

/// Which HarfBuzz is linked into `libgoldberry`.
///
/// A build fact, not a runtime one: HarfBuzz is statically linked, so there is
/// no system copy to disagree with.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public record HarfBuzzVersion(int major, int minor, int micro) {

    @Override
    public String toString() {
        return major + "." + minor + "." + micro;
    }
}
