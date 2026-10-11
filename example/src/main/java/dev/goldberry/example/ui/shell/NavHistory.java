package dev.goldberry.example.ui.shell;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/// The screens a reader has been to, for Back and Forward.
///
/// A browser's two stacks: going somewhere new pushes where you were and
/// forgets what was ahead; going back moves the current screen onto the
/// forward stack. Both are capped, because a reader who has clicked through the
/// gallery for an hour does not need an hour of Back.
///
/// Holds names and nothing else, so it knows nothing about what a screen is —
/// the model asks it, and the model decides what a name means.
///
/// Read more: [Views](https://goldberry.dev/docs/applications.html#views).
public final class NavHistory {

    /// The most screens either stack remembers.
    public static final int LIMIT = 50;

    private final Deque<String> behind = new ArrayDeque<>();
    private final Deque<String> ahead = new ArrayDeque<>();

    /// The reader left `from` for somewhere new.
    ///
    /// Clears the forward stack, as a browser does: a new branch makes the old
    /// one unreachable.
    public void visit(String from) {
        push(behind, from);
        ahead.clear();
    }

    /// Where Back goes from `current`, having remembered `current` for Forward;
    /// empty when there is nowhere to go.
    public Optional<String> back(String current) {
        return move(behind, ahead, current);
    }

    /// Where Forward goes from `current`, having remembered `current` for Back;
    /// empty when there is nowhere to go.
    public Optional<String> forward(String current) {
        return move(ahead, behind, current);
    }

    /// Whether Back would go anywhere.
    public boolean canGoBack() {
        return !behind.isEmpty();
    }

    /// Whether Forward would go anywhere.
    public boolean canGoForward() {
        return !ahead.isEmpty();
    }

    /// The screens Back would visit, nearest first.
    public List<String> behind() {
        return List.copyOf(behind);
    }

    private static Optional<String> move(Deque<String> from, Deque<String> to, String current) {
        var next = from.pollFirst();
        if (next == null) {
            return Optional.empty();
        }
        push(to, current);
        return Optional.of(next);
    }

    private static void push(Deque<String> stack, String name) {
        if (name.equals(stack.peekFirst())) {
            return;
        }
        stack.addFirst(name);
        while (stack.size() > LIMIT) {
            stack.removeLast();
        }
    }
}
