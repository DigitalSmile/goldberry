/// The check that makes hand-written bindings defensible: a registry of every struct
/// layout and C constant the Java side declares, and the verifier that compares them
/// against the table the compiled library reports for its own target.
///
/// A layout or constant used in a binding belongs in a registry here, and its C
/// expression in `goldberry_shim.c`; one without the other is a test failure by
/// design. Not exported.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more:
/// [Repository layout](https://goldberry.dev/docs/contributing/repository.html#the-export-list-and-the-layout-probe).
@NullMarked
package dev.goldberry.natives.layout;

import org.jspecify.annotations.NullMarked;
