package io.github.digitalsmile.goldberry.html.model;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// A tag, its attributes and what is inside it.
///
/// ```java
/// for (var link : document.find("a")) {
///     check(link.attribute("href"), link.text());
/// }
/// ```
///
/// **The tag is a lower-case string and not an enum**, which is the one modelling
/// decision here worth an argument. An enum would make a fold exhaustive and would
/// also make `<my-widget>` unrepresentable — and HTML's whole story for the last
/// decade is that authors invent elements. So the *node kinds* are sealed and the
/// *tags* are open: a renderer that meets a tag it has no rule for lays it out as
/// whatever [Tags] says it is, block or inline, and styles it by name. Nothing is
/// dropped for being unknown (ADR-0298).
///
/// @param tag the tag name, lower-cased by the parser so that `<DIV>` and `<div>`
///        are one element to match on
/// @param attributes what was written in the tag, in order, with names lower-cased
/// @param children what is between the tags. Empty for a void element such as `br`,
///        and empty for a tag the author closed straight away
public record Element(String tag, HtmlAttributes attributes, List<HtmlNode> children) implements HtmlNode {

    public Element {
        Objects.requireNonNull(tag, "tag");
        if (tag.isBlank()) {
            throw new IllegalArgumentException("an element with no tag name is not something a document can hold");
        }
        attributes = attributes == null ? HtmlAttributes.NONE : attributes;
        children = List.copyOf(Objects.requireNonNull(children, "children"));
    }

    /// An empty element with no attributes — `<hr>`.
    public Element(String tag) {
        this(tag, HtmlAttributes.NONE, List.of());
    }

    /// An element holding `children`, with no attributes.
    public Element(String tag, List<HtmlNode> children) {
        this(tag, HtmlAttributes.NONE, children);
    }

    /// What `name` was set to, or null when the tag did not set it.
    ///
    /// Null rather than empty, because `<a href>` and `<a>` are different things to a
    /// link checker: one is a link with nowhere to go and the other is an anchor.
    public @Nullable String attribute(String name) {
        return attributes.value(name);
    }

    /// The `class` attribute, split on whitespace. Empty when there is none.
    public List<String> classes() {
        return attributes.classes();
    }

    /// The `id` attribute, or null.
    public @Nullable String id() {
        return attributes.value("id");
    }

    /// Every word inside this element, in order, with one space where a tag was.
    ///
    /// What an anchor's label is, what a heading contributes to a table of contents,
    /// and what a `pre` holds — for that last one see [#rawText()], because this
    /// collapses nothing but also inserts nothing except at tag boundaries.
    public String text() {
        return Walk.text(this);
    }

    /// The text of this element with **no** space inserted at tag boundaries.
    ///
    /// What a `pre` or a `<pre><code>` pair holds: the author's own spacing, which is
    /// the whole point of those two elements, and which [#text()] would have added a
    /// space to at every `<span>` a highlighter left behind.
    public String rawText() {
        return Walk.raw(this);
    }

    /// Every descendant element with this tag, in document order.
    ///
    /// The generic walk that makes this model worth exporting: a table of contents is
    /// `find("h2")`, a link check is `find("a")`, and neither needs a visitor.
    public List<Element> find(String tag) {
        return Walk.find(this, tag);
    }
}
