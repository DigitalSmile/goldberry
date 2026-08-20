package io.github.digitalsmile.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Checks that every constant in [Downcalls] is what its name says it is.
///
/// A name that has drifted from its layouts is the one mistake this scheme can
/// make and the compiler cannot catch: `invokeExact` checks the handle against
/// the *call site*, so a constant named `INT__PTR_INT` built from
/// `of(INT, PTR, LONG)` would be rejected at every call that reads the name
/// correctly — at run time, on whichever platform got there first. This is the
/// check that happens at build time instead.
///
/// It needs no `libgoldberry`: an unbound handle is linked from a descriptor and
/// names no address, which is the whole point of the class
/// ([ADR-0161](../../../../../../book/src/adr/0161-a-downcall-handle-is-a-constant-or-it-is-not-a-call.md)).
class DowncallsTest {

    /// The words a name is spelled with, and what each one carries.
    private static final Map<String, Class<?>> CARRIERS = Map.of(
            "VOID", void.class,
            "BOOL", boolean.class,
            "SHORT", short.class,
            "INT", int.class,
            "LONG", long.class,
            "FLOAT", float.class,
            "DOUBLE", double.class,
            "PTR", MemorySegment.class);

    private static List<java.lang.reflect.Field> constants() {
        return Arrays.stream(Downcalls.class.getDeclaredFields())
                .filter(field -> field.getType() == MethodHandle.class)
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .toList();
    }

    private static MethodHandle handleOf(java.lang.reflect.Field field) {
        try {
            return (MethodHandle) field.get(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Downcalls." + field.getName() + " is not readable", e);
        }
    }

    /// The [MethodType] a name promises: the target address, then the arguments.
    private static MethodType typeOf(String name) {
        var halves = name.split("__", -1);
        assertEquals(2, halves.length, name + " is not <return>__<arguments>");
        var returnType = CARRIERS.get(halves[0]);
        assertNotNull(returnType, name + " has an unknown return carrier");

        var arguments = new java.util.ArrayList<Class<?>>();
        // The leading MemorySegment is the function to call. Every constant here
        // is unbound, so every one of them takes it.
        arguments.add(MemorySegment.class);
        if (!halves[1].equals("VOID")) {
            for (var word : halves[1].split("_")) {
                var carrier = CARRIERS.get(word);
                assertNotNull(carrier, name + " has an unknown argument carrier " + word);
                assertFalse(carrier == void.class, name + " has a void argument");
                arguments.add(carrier);
            }
        }
        return MethodType.methodType(returnType, arguments);
    }

    @Test
    @DisplayName("every name is <return>__<arguments> and nothing else")
    void namesAreWellFormed() {
        assertAll(constants().stream().map(field -> () -> {
            var name = field.getName();
            assertTrue(name.contains("__"), name + " has no return/argument separator");
            assertFalse(name.startsWith("__"), name + " names no return carrier");
            assertFalse(name.endsWith("_"), name + " ends in a separator");
            typeOf(name);
        }));
    }

    @Test
    @DisplayName("there are constants to check")
    void theClassIsNotEmpty() {
        assertTrue(constants().size() > 40,
                "Downcalls should carry the toolkit's whole signature set, found "
                        + constants().size());
    }

    @Test
    @DisplayName("every constant's descriptor is the signature its name spells")
    void namesDescribeTheirDescriptors() {
        assertAll(constants().stream().map(field -> () -> assertEquals(
                typeOf(field.getName()),
                handleOf(field).type(),
                "Downcalls." + field.getName() + " does not carry the signature its name spells")));
    }

    @Test
    @DisplayName("every constant is unbound — it takes the address to call")
    void everyHandleIsUnbound() {
        assertAll(constants().stream().map(field -> () -> assertEquals(
                MemorySegment.class,
                handleOf(field).type().parameterType(0),
                "Downcalls." + field.getName() + " does not take a target address, so it is bound"
                        + " to one — which is the thing ADR-0161 exists to stop")));
    }

    @Test
    @DisplayName("no two constants carry the same signature")
    void namesAreUnique() {
        var types = constants().stream().map(field -> handleOf(field).type()).toList();
        assertEquals(types.size(), types.stream().distinct().count(),
                "two constants in Downcalls describe the same signature under different names");
    }

    @Test
    @DisplayName("a symbol the library does not export names the export list")
    void aMissingSymbolSaysWhereToAddIt() {
        var failure = assertThrows(UnsatisfiedLinkError.class,
                () -> Downcalls.symbol(name -> java.util.Optional.empty(), "goldberry_no_such_thing"));

        assertTrue(failure.getMessage().contains("goldberry_no_such_thing"), failure.getMessage());
        assertTrue(failure.getMessage().contains("goldberry.symbols"), failure.getMessage());
    }

    @Test
    @DisplayName("an optional symbol that is missing is null, not a failure")
    void aMissingOptionalSymbolIsNull() {
        assertNull(Downcalls.optionalSymbol(name -> java.util.Optional.empty(), "SDL_Nothing"));
    }

    @Test
    @DisplayName("a symbol that is there comes back as its address")
    void aFoundSymbolIsItsAddress() {
        var address = MemorySegment.ofAddress(0x1234);

        assertEquals(address, Downcalls.symbol(name -> java.util.Optional.of(address), "anything"));
        assertEquals(address,
                Downcalls.optionalSymbol(name -> java.util.Optional.of(address), "anything"));
    }
}
