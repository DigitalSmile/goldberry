package io.github.digitalsmile.goldberry.natives;

import java.lang.foreign.FunctionDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Where an upcall's signature is declared, so that a native image can be told
/// about it before the stub is ever made (ADR-0339).
///
/// A downcall's descriptor passes through [Downcalls#link] and is recorded
/// there. An upcall's does not pass through anything shared: each of the five
/// classes that makes a stub keeps its own `DESCRIPTOR` and hands it to
/// `Linker.upcallStub` at run time, when the callback is installed. That is too
/// late for an image, which has to know every upcall shape while it is being
/// built — and it is exactly the case a traced run misses, because a stub is
/// made when a tray, a dialog or a measured leaf first exists.
///
/// So the constant is declared through [#describe] instead, which records it at
/// class initialisation and hands it straight back. `ForeignSurface` initialises
/// the owners and reads [#declared()]; a test holds the list of owners to every
/// class that calls `upcallStub`.
public final class Upcalls {

    private static final List<FunctionDescriptor> DECLARED = Collections.synchronizedList(new ArrayList<>());

    private Upcalls() {}

    /// Records `descriptor` as a shape some stub will have, and returns it, so a
    /// `static final` can be declared through this call.
    public static FunctionDescriptor describe(FunctionDescriptor descriptor) {
        synchronized (DECLARED) {
            if (!DECLARED.contains(descriptor)) {
                DECLARED.add(descriptor);
            }
        }
        return descriptor;
    }

    /// The distinct upcall descriptors declared so far, in declaration order.
    /// Complete only once every owner class has been initialised.
    public static List<FunctionDescriptor> declared() {
        synchronized (DECLARED) {
            return List.copyOf(DECLARED);
        }
    }
}
