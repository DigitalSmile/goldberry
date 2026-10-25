package dev.goldberry.example;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import dev.goldberry.bind.Observable;
import dev.goldberry.example.model.ShowcaseActions;
import dev.goldberry.example.model.ShowcaseModel;
import dev.goldberry.widgets.markup.Wiring;

/// The showcase's registries as a document sees them: every slice's paths and
/// every slice's actions in one, merged by [Wiring#of] as the window's inflater
/// merges them.
///
/// For a test that names a path without caring which slice publishes it — a
/// list of paths across the window, or a session reading whatever a card bound.
/// A test about one slice asks that slice.
public final class ShowcaseWiring {

    private ShowcaseWiring() {}

    /// What a document inflated against `model` and `actions` resolves names in.
    public static Wiring of(ShowcaseModel model, ShowcaseActions actions) {
        return Wiring.of(Stream.<List<Object>>of(model.slices(), actions.slices())
                .flatMap(List::stream)
                .toArray());
    }

    /// The property `path` names, on whichever slice of `model` publishes it.
    ///
    /// @throws IllegalArgumentException if no slice binds `path`
    @SuppressWarnings("unchecked")
    public static <T> Observable<T> observable(ShowcaseModel model, String path) {
        var bindings = Wiring.of(model.slices().toArray()).bindings();
        return (Observable<T>) Objects.requireNonNull(bindings.resolve(path), "a strict registry answers or throws");
    }
}
