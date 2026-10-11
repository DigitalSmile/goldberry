package dev.goldberry.example.ui.search;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

/// What the reader picked from the palette lately, most recent first, so an
/// empty query offers them again.
///
/// Keys rather than entries ([SearchEntry#key()]), because the palette's list
/// is rebuilt each time it opens and a command's `Runnable` is a new object
/// every time.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public final class RecentPicks {

    /// The most picks remembered.
    public static final int LIMIT = 6;

    private final SequencedSet<String> keys = new LinkedHashSet<>();

    /// `key` was picked: it moves to the front, and the oldest falls off the end.
    public void remember(String key) {
        keys.remove(key);
        keys.addFirst(key);
        while (keys.size() > LIMIT) {
            keys.removeLast();
        }
    }

    /// The keys, most recent first.
    public List<String> keys() {
        return List.copyOf(keys);
    }
}
