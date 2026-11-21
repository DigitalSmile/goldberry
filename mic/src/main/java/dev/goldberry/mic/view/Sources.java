package dev.goldberry.mic.view;

import java.util.Objects;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.mic.source.SampleSource;
import dev.goldberry.mic.source.Signal;
import dev.goldberry.mic.source.SyntheticSource;
import dev.goldberry.widgets.markup.Wiring;

/// The source a widget's markup names.
final class Sources {

    private Sources() {}

    /// The source `source=` names, or, where nothing is bound to the name, a
    /// source that never delivers: a preview of a document with no
    /// application behind it draws a quiet meter rather than failing.
    static SampleSource named(KdlNode node, Wiring wiring) {
        return Objects.requireNonNullElseGet(
                wiring.handle(node, "source", SampleSource.class), () -> SyntheticSource.of(Signal.silence()));
    }
}
