package io.github.digitalsmile.goldberry.build.ci;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A throwable as an annotation's body: its cause chain, one line each, and the
 * top of the deepest stack (ADR-0338).
 *
 * <p>Gradle wraps what actually went wrong several levels deep -- "Execution failed
 * for task", then "There were failing tests", or a process exit code, and only
 * then the message someone can act on. The whole chain is shown for that reason,
 * and the frames are the deepest cause's, since the outer ones are Gradle's.
 */
public final class FailureText {

    /** Frames kept from the deepest cause. Enough to find the line, no more. */
    static final int FRAMES = 8;

    private FailureText() {
    }

    /**
     * @param failure what went wrong
     * @return the cause chain and the top frames of the root cause
     */
    public static String of(Throwable failure) {
        var chain = chain(failure);
        var lines = chain.stream()
                .map(FailureText::describe)
                .collect(Collectors.toCollection(ArrayList::new));
        var root = chain.getLast();
        var frames = root.getStackTrace();
        for (var index = 0; index < Math.min(FRAMES, frames.length); index++) {
            lines.add("    at " + frames[index]);
        }
        return String.join("\n", lines);
    }

    /** The throwable and its causes, outermost first, without looping on a cycle. */
    static List<Throwable> chain(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var chain = new ArrayList<Throwable>();
        for (var current = failure; current != null && seen.add(current); current = current.getCause()) {
            chain.add(current);
        }
        return chain;
    }

    private static String describe(Throwable throwable) {
        var message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getName()
                : throwable.getClass().getSimpleName() + ": " + message.strip();
    }
}
