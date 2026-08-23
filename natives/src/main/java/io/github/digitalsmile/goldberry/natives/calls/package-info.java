/// One holder per bound C function: its handle, its address, and its `call`.
///
/// A holder pairs the one thing that is known while the image is being built —
/// the signature — with the one thing that cannot be — the address, which exists
/// only once `libgoldberry` has been `dlopen`ed. The signature is a
/// `private static final MethodHandle FD_<symbol>`; the address is a
/// `private final MemorySegment`; and `call` is an ordinary Java method with
/// ordinary Java argument types, so the `invokeExact`, the cast of its result and
/// the `try`/`catch` that names the function in the failure all live in one place
/// instead of at each of the two hundred and eighty call sites.
///
/// ## Why this is a package and not a nested class of the binding
///
/// Because of the flag. ADR-0161 measured that a downcall handle is 450× slower
/// in a native image unless it is a **compile-time constant**, which it only is
/// if its class was initialised while the image was being built. `:natives`
/// therefore ships `--initialize-at-build-time`, and what that flag can name is
/// the constraint:
///
/// | flag                                | holder                          | ns/call |
/// |-------------------------------------|---------------------------------|---------|
/// | `--initialize-at-build-time=Outer`  | `static final` on `Outer`       | 10.55   |
/// | `--initialize-at-build-time=Outer`  | `static final` on `Outer$Nested`| 4537.82 |
/// | `--initialize-at-build-time=Outer,Outer$Nested` | same nested class    | 11.25   |
/// | `--initialize-at-build-time=<package>` | nested, anywhere in it       | 8.07    |
/// | any                                 | instance field of a record      | 4539.53 |
///
/// Measured on GraalVM CE 25.2.4 by the probe in ADR-0173, twenty million calls
/// to `goldberry_abi_version`. **Naming the enclosing class is not enough** — the
/// second row is the trap, and it is silent: the image builds, runs and paints
/// correctly, at a fortieth of the speed.
///
/// Naming a hundred and thirty-four nested classes in a build flag is not a
/// maintainable list, and naming the *binding* packages instead would build-time
/// initialise `Sdl` and `Blend2D`, whose holder idiom `dlopen`s the library — in
/// the builder, which is the wrong process. So the holders get packages that
/// contain nothing else, and the flag names those. It is safe by construction: a
/// holder added tomorrow is covered by the package that already exists.
///
/// The last row is why the handle is not simply a field of the pair, which is the
/// design this one replaced before it was measured.
///
/// @see io.github.digitalsmile.goldberry.natives.Downcalls
package io.github.digitalsmile.goldberry.natives.calls;
