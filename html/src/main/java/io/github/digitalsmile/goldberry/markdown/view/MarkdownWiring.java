package io.github.digitalsmile.goldberry.markdown.view;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.content.ImageSource;
import io.github.digitalsmile.goldberry.image.Image;

/// What a rendered document presses back through, held for as long as the view is
/// mounted.
///
/// **The indirection a memo needs.** A link is a `button` whose press handler was
/// built at some earlier keystroke, and an application that writes
/// `onLink(this::open)` inside its own `build` hands the view a *new* object every
/// frame. A button that had captured one would keep calling the first one it ever
/// saw. It calls through this instead: one object for the life of the view, given
/// the current handlers at the start of every build
/// ([ADR-0389]).
///
/// It is also what decides when the memo has to be emptied. Which handlers a view
/// *has* changes what a block is — a link with no handler is drawn inert and is not
/// a button at all — so [#signature()] goes into every block's mark and a view that
/// gains or loses one rebuilds.
///
/// Confined to the UI thread, like everything a build touches.
final class MarkdownWiring {

    private @Nullable Consumer<String> onLink;

    private @Nullable Consumer<String> onWikiLink;

    private @Nullable ImageSource images;

    private @Nullable IntConsumer onTask;

    /// Takes this build's handlers.
    void of(
            @Nullable Consumer<String> link,
            @Nullable Consumer<String> wikiLink,
            @Nullable ImageSource imageSource,
            @Nullable IntConsumer task) {
        this.onLink = link;
        this.onWikiLink = wikiLink;
        this.images = imageSource;
        this.onTask = task;
    }

    /// Which of the four the view has, as four bits.
    ///
    /// Presence and not identity: a handler that is a different object saying the
    /// same thing must not rebuild a note, which is the whole reason this class
    /// exists. A handler that has *appeared* must.
    int signature() {
        return (onLink == null ? 0 : 1)
                | (onWikiLink == null ? 0 : 2)
                | (images == null ? 0 : 4)
                | (onTask == null ? 0 : 8);
    }

    /// What a link presses, or null when the view has no handler and a link is drawn
    /// inert.
    ///
    /// A forwarder rather than the application's own consumer, which is the point:
    /// the widget that captures this calls whatever the view is holding **now**.
    @Nullable
    Consumer<String> links() {
        return onLink == null ? null : this::link;
    }

    /// The same for a `[[wiki link]]`.
    @Nullable
    Consumer<String> wikiLinks() {
        return onWikiLink == null ? null : this::wikiLink;
    }

    /// The same for a task box, which reports its ordinal.
    @Nullable
    IntConsumer tasks() {
        return onTask == null ? null : this::task;
    }

    private void link(String href) {
        if (onLink != null) {
            onLink.accept(href);
        }
    }

    private void wikiLink(String target) {
        if (onWikiLink != null) {
            onWikiLink.accept(target);
        }
    }

    private void task(int index) {
        if (onTask != null) {
            onTask.accept(index);
        }
    }

    /// The image `src` names, or null — including when no source is wired.
    @Nullable
    Image image(String src) {
        return images == null ? null : images.image(src);
    }
}
