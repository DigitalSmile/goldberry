package io.github.digitalsmile.goldberry.content;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.image.Image;

/// Where a document's images come from — the application's answer, not the
/// toolkit's.
///
/// ```java
/// ImageSource assets = src -> cache.computeIfAbsent(src, this::load);
///
/// var view = HtmlView.of(page).images(assets);
/// ```
///
/// ```kdl
/// html-view bind="doc.source" images="doc.assets"
/// ```
///
/// ## Why this is an interface and not a resolver the module ships
///
/// A `src` in a document is a **string**, and what it means is the application's:
/// a path relative to something only it knows, a key in a content-addressed store,
/// a URL nothing here may fetch. ADR-0190 put fetching outside the toolkit, ADR-0291
/// put URL handling outside it, and this is the same line drawn at the same place
/// (ADR-0300). Nothing in `:html` opens a file or a socket.
///
/// It is also what makes the [Image] **lifetime** clear: the application owns the
/// image and decides what is cached. An `Image` is a value that owns no native handle
/// (ADR-0283), so it can be held in a map for as long as the application likes and
/// handed to a widget that is rebuilt every frame.
///
/// ## Answering "no" is normal
///
/// A source returns null for anything it does not have, and the view draws the alt
/// text — which is what a document with a broken link should show and what every
/// view did before this existed. There is no exception and no placeholder: a
/// renderer that drew a broken-image icon would be inventing content.
@FunctionalInterface
public interface ImageSource {

    /// The image `src` names, or null when this source does not have one.
    ///
    /// Called during a **build**, so it must not block: a source that fetches over a
    /// network returns null the first time, starts its own work, and sets a property
    /// the view is bound to — which makes the image arrive on a later frame the same
    /// way every other value does (ADR-0062).
    ///
    /// @param src the `src` or the link destination, exactly as the document wrote it
    @Nullable
    Image image(String src);

    /// A source with nothing in it, for a view nobody has wired.
    static ImageSource none() {
        return src -> null;
    }
}
