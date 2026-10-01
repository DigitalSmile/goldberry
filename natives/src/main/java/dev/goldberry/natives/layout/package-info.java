/// The check that makes hand-written bindings defensible: a registry of every struct
/// layout and C constant the Java side declares, and the verifier that compares them
/// against the table the compiled library reports for its own target (ADR-0010).
///
/// A layout or constant used in a binding belongs in a registry here, and its C
/// expression in `goldberry_shim.c`; one without the other is a test failure by
/// design. Not exported.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.natives.layout;

import org.jspecify.annotations.NullMarked;
