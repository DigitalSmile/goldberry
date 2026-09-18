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
/// It is also what decides when the memo has to be emptied. What a view *has* changes
/// what a block is — a link with no handler is drawn inert and is not a button at all,
/// and a picture is whatever the view's [ImageSource] answered — so [#signature()] goes
/// into every block's mark, and a view that gains a handler or is given a different
/// image source rebuilds.
///
/// Confined to the UI thread, like everything a build touches.
final class MarkdownWiring {

    private @Nullable Consumer<String> onLink;

    private @Nullable Consumer<String> onWikiLink;

    private @Nullable ImageSource images;

    private @Nullable IntConsumer onTask;

    /// What [#signature()] answers, worked out once a build.
    private int signature;

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
        var present = (link == null ? 0 : 1)
                | (wikiLink == null ? 0 : 2)
                | (imageSource == null ? 0 : 4)
                | (task == null ? 0 : 8);
        this.signature = present * 31 + System.identityHashCode(imageSource);
    }

    /// What the view has, as one number a block's mark can carry.
    ///
    /// **Presence for the three handlers.** One that is a different object saying the
    /// same thing must not rebuild a note, which is the whole reason this class
    /// exists; one that has *appeared* must, because a link with no handler is drawn
    /// inert and is a different widget.
    ///
    /// **Identity for the image source**, because it is not a handler. A block does not
    /// call it when something is pressed — it asks it *what to draw* while it is being
    /// built and then keeps the answer, a `Picture` holding an `Image`, for as long as
    /// the memo keeps the block. [MarkdownWidgets#keep()] covers the block that found
    /// nothing and has to ask again; nothing covered the block that found something and
    /// would now find something else, so an application that swapped its assets kept
    /// the old picture for ever. ADR-0389 §4 argues presence-not-identity for handlers,
    /// and this is the one of the four that is not one.
    ///
    /// It stays cheap because it is not worked out here. This is asked once per block
    /// per build — [MarkdownWidgets] puts it in every mark — and is a field read; the
    /// one `identityHashCode` is paid in [#of], which a build calls once. What it does
    /// **not** cover is a source that answers differently without being replaced, and
    /// that is deliberate: noticing would mean calling it per image per keystroke,
    /// which is the cost ADR-0389 exists to remove.
    int signature() {
        return signature;
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
