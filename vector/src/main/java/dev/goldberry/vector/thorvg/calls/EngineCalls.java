package dev.goldberry.vector.thorvg.calls;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.List;

import dev.goldberry.vector.thorvg.Downcalls;

/// ThorVG's engine: started once for the process and never stopped while a
/// document can still be drawn. Every function answers a `Tvg_Result`.
public record EngineCalls(Init init, Term term) {

    /// The symbols this record binds, in the order of its components.
    public static final List<String> SYMBOLS = List.of("tvg_engine_init", "tvg_engine_term");

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static EngineCalls bind(SymbolLookup lookup) {
        return new EngineCalls(new Init(lookup), new Term(lookup));
    }

    /// `Tvg_Result tvg_engine_init(unsigned threads)`
    ///
    /// `threads` is 0 here: ThorVG is built without its task scheduler.
    public static final class Init {

        private static final MethodHandle FD_tvg_engine_init =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        Init(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_engine_init");
        }

        public int call(int threads) {
            try {
                return (int) FD_tvg_engine_init.invokeExact(address, threads);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_engine_init", t);
            }
        }
    }

    /// `Tvg_Result tvg_engine_term(void)`
    ///
    /// Must pair with an init on the same thread.
    public static final class Term {

        private static final MethodHandle FD_tvg_engine_term = Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        Term(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_engine_term");
        }

        public int call() {
            try {
                return (int) FD_tvg_engine_term.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_engine_term", t);
            }
        }
    }
}
