package dev.goldberry.example.ui.lookalike.player;

/// What happens at the end of a track: the repeat button's three states, in the
/// order one press moves through them.
public enum Repeat {
    /// Play to the end of the list and stop.
    OFF,
    /// Go round the list.
    ALL,
    /// Play this track again.
    ONE;

    /// The state after one press.
    public Repeat next() {
        return switch (this) {
            case OFF -> ALL;
            case ALL -> ONE;
            case ONE -> OFF;
        };
    }
}
