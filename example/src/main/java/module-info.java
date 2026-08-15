/// The Goldberry showcase.
///
/// A module, like everything else here: an application consuming Goldberry on
/// the module path is the case `--enable-native-access=<module>` is designed for
/// (ADR-0007), and it only works if the toolkit's own descriptors are right.
/// Building this on the classpath instead would leave that untested.
module io.github.digitalsmile.goldberry.example {
    requires io.github.digitalsmile.goldberry.core;
}
