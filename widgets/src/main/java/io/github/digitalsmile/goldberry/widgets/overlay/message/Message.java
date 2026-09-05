package io.github.digitalsmile.goldberry.widgets.overlay.message;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.attr.Bindable;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// An inline banner about the region it sits in — `docs/core-widgets.md` §7's
/// `message`.
///
/// ```kdl
/// message kind="warning" "Your session ends in five minutes." {
///     button class="ghost" "Stay signed in" press="extend"
/// }
/// message kind="danger" dismiss="clear" "Could not save: the port is in use."
/// ```
///
/// ## It is not a `toast`, and that is why both exist
///
/// §7 draws the line and this widget is the half that stays put: a toast is
/// transient, floats over the window and is about something that just happened; a
/// message is **part of the layout**, persists until the condition does, and is
/// about the thing next to it. "Saved" is a toast. A form's error summary is a
/// message — see [#summary(List)], which is the one §4 asked for by name.
///
/// Nothing about that is a style: a toast is on the window's overlay layer
/// ([io.github.digitalsmile.goldberry.Overlay]) and a message is a child in
/// somebody's column, so they could not be one widget with a flag even if the
/// specification wanted them to be.
///
/// ## The kind is a value, where a badge's variant is a class
///
/// [io.github.digitalsmile.goldberry.widgets.controls.badge.Badge] spells its
/// variants as classes and says why: a variant that is only a skin should not be
/// a second vocabulary that only Java can write. A message's [Kind] is not a
/// skin. It picks the **glyph** as well as the hue, because §1.2 forbids colour
/// as the only carrier of meaning, and it is what M5's semantics will read to
/// decide `status` from `alert` — which is the difference between a banner that
/// interrupts a screen reader and one that does not.
///
/// So it is a value the widget reads, `kind=` is what §7 names it, and the class
/// goes on the node anyway — `message.danger` selects without anybody writing it.
///
/// ## The parts
///
/// ```
/// message                  a row: glyph, words, and the way out
/// ├── message-icon         the kind's glyph, 20px per §1.6
/// ├── message-body         a column, and the only part that grows
/// │   ├── (text)           the words, as a child box so they wrap
/// │   └── message-actions  the author's links, absent when there are none
/// └── message-dismiss      a ×, absent unless somebody is listening
/// ```
///
/// The actions are the author's own widgets rather than a node this widget
/// invents: §7 says "optional action links", and `button class="ghost"` already
/// is one. A `message-action` element would have been a second button that had to
/// be kept looking like the first.
///
/// ## A bound banner with nothing to say says nothing
///
/// `bind=` was the last thing this widget was missing, and it was missing for a
/// reason rather than an oversight: a banner bound to an empty string would have
/// been *present and empty* — a bordered box with 12px of padding saying nothing
/// — and §8's subset has no `display`, so no widget could take itself out of a
/// layout. The way round it was to describe the banner away from outside, which
/// is why [#summary(List)] returns an `Optional`.
///
/// The element tree has the word now: a build may answer
/// [Widget#nothing()], and a node that describes nothing has no box and takes no
/// gap ([ADR-0227]). So a bound `message` whose value is null or blank is simply
/// not there, and comes back when the value does — the same element, the same
/// subscription, the same arrival.
///
/// ```kdl
/// message kind="danger" bind="form.error"
/// ```
///
/// The value is read with `toString`, like every other bound text in the
/// catalog. `text` stays as the fallback for a banner that has both, because a
/// document that wrote words *and* a binding meant the words to show until the
/// value arrives.
///
/// @param kind       which of §7's four this is — the glyph and the hue
/// @param text       what it says; hard newlines break lines, which is what makes
///                   [#summary(List)] one banner rather than a column of them
/// @param source     §9's `bind=`, or null — when it resolves to nothing, so does
///                   the banner
/// @param actions    the author's links, in the order they were written
/// @param onDismiss  what to tell when the × is clicked, or null for a banner
///                   with no way out — a message that persists until the
///                   *condition* does is the common case, so this is opt-in
/// @param attributes the `id` and classes, which land on the `message` node
@Markup("message")
public record Message(
        Kind kind,
        String text,
        @Nullable Observable<?> source,
        List<Widget> actions,
        Runnable onDismiss,
        Attributes attributes)
        implements Widget.Stateful, Attributed<Message>, Bindable<Message> {

    /// §7's `kind="info|success|warning|danger"`.
    ///
    /// Each one names a glyph as well as a colour, which is §1.2's rule rather
    /// than a decoration: a banner that said "danger" only in red would say
    /// nothing at all to a reader who cannot see red, and there are two of these
    /// four in every palette that are a hue apart.
    public enum Kind {

        /// Something the reader should know. A `status`, not an alert.
        INFO(Box.Mark.Kind.CIRCLE_INFO),

        /// Something that worked and stays worked — a saved draft, a verified
        /// address. A transient "Saved" is a `toast`.
        SUCCESS(Box.Mark.Kind.CIRCLE_CHECK),

        /// Something that will go wrong if nothing changes.
        WARNING(Box.Mark.Kind.TRIANGLE_ALERT),

        /// Something that has gone wrong. A form's error summary is one of these.
        DANGER(Box.Mark.Kind.CIRCLE_ALERT);

        private final Box.Mark.Kind glyph;

        Kind(Box.Mark.Kind glyph) {
            this.glyph = glyph;
        }

        /// The mark this kind draws — see [Box.Mark.Kind#CIRCLE_INFO] for why
        /// these are marks and not icons.
        public Box.Mark.Kind glyph() {
            return glyph;
        }

        /// The class this kind puts on the node: `message.warning`.
        public String cssClass() {
            return name().toLowerCase(Locale.ROOT);
        }

        /// The kind a document named.
        ///
        /// Absent is [#INFO], because a banner with no kind is still a banner and
        /// refusing to build one would take a window down over a missing word.
        /// A kind that is *misspelt* is refused, because that is a document
        /// saying something it does not mean.
        public static Kind of(@Nullable String text) {
            if (text == null || text.isBlank()) {
                return INFO;
            }
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "a message's kind is \"info\", \"success\", \"warning\" or \"danger\"," + " not \"" + text
                                + "\"",
                        e);
            }
        }
    }

    public Message {
        Objects.requireNonNull(text, "text");
        kind = kind == null ? Kind.INFO : kind;
        actions = List.copyOf(actions == null ? List.of() : actions);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A banner of a kind, saying one thing.
    public Message(Kind kind, String text) {
        this(kind, text, null, List.of(), null, Attributes.NONE);
    }

    /// The five-argument form, kept because every caller written before `bind=`
    /// existed passes exactly these.
    public Message(Kind kind, String text, List<Widget> actions, Runnable onDismiss, Attributes attributes) {
        this(kind, text, null, actions, onDismiss, attributes);
    }

    /// This banner with links after its words.
    public Message actions(Widget... widgets) {
        return new Message(kind, text, source, List.of(widgets), onDismiss, attributes);
    }

    /// This banner with a way out. Null takes the × away again.
    public Message dismiss(Runnable listener) {
        return new Message(kind, text, source, actions, listener, attributes);
    }

    @Override
    public Message bound(Observable<?> value) {
        return new Message(kind, text, value, actions, onDismiss, attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    /// What this banner actually says: the bound value if there is one, and
    /// [#text] otherwise.
    ///
    /// **Blank when the value is null or blank**, which is what makes the banner
    /// disappear rather than stand there empty — see [MessageState].
    /// `text` is not a fallback for a *blank* value, only for no binding at all:
    /// an application whose error property is empty means "there is no error",
    /// and showing the document's placeholder words instead would be a banner
    /// reporting a problem that has gone away.
    public String resolved() {
        if (source == null) {
            return text;
        }
        var value = source.get();
        return value == null ? "" : String.valueOf(value);
    }

    /// §4's error summary: what is wrong with a form, as one `danger` banner.
    ///
    /// The entry `docs/core-widgets.md` §4 asks for by name — "failures register
    /// in the form's error summary" — with
    /// [io.github.digitalsmile.goldberry.widgets.form.form.FormController#errors()]
    /// as the register. It is a **factory rather than something `form` draws**,
    /// because a form does not know where its summary belongs: above the fields
    /// is the convention, below them is what a long form wants, and in a dialog's
    /// header is what a dialog wants. Whoever placed the form places this.
    ///
    /// One banner with a line per failure rather than a banner per failure: the
    /// summary answers "what is stopping this from submitting", and four boxes
    /// stacked up answer it four times.
    ///
    /// @return the banner, or empty when nothing is wrong — a summary of no
    ///         errors is not an empty banner, it is no banner
    public static Optional<Message> summary(List<String> errors) {
        Objects.requireNonNull(errors, "errors");
        var lines = errors.stream()
                .filter(Objects::nonNull)
                .filter(line -> !line.isBlank())
                .toList();
        return lines.isEmpty() ? Optional.empty() : Optional.of(new Message(Kind.DANGER, String.join("\n", lines)));
    }

    /// Whether this banner draws a ×.
    public boolean isDismissable() {
        return onDismiss != null;
    }

    @Override
    public Message withAttributes(Attributes value) {
        return new Message(kind, text, source, actions, onDismiss, value);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    /// The kind's own class is added by [MessageBox], not here — see its note.
    /// This widget styles nothing: it is the description, and what a stylesheet
    /// calls `message` is the node its state builds.
    @Override
    public State<?> createState() {
        return new MessageState();
    }

    /// Builds a `message` from markup.
    ///
    /// The words are the node's argument, the way `text` and `badge` take theirs;
    /// the children are the action links. `dismiss=` names an action, so a
    /// document says who to tell rather than deciding by itself that a banner can
    /// be closed — nothing in a document could remove the banner anyway, because
    /// what put it there is the application's own state.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Message(
                Kind.of(node.stringProperty("kind")),
                Wiring.label(node),
                wiring.bound(node),
                children,
                wiring.action(node, "dismiss"),
                Attributes.of(node));
    }
}
