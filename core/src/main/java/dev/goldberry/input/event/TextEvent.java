package dev.goldberry.input.event;

import java.util.Objects;

import dev.goldberry.widget.Element;

/// Text the platform has finished translating, delivered to whatever has focus.
///
/// ```java
/// @Override public void onTextInput(TextEvent event) {
///     editor.insert(event.text());
///     event.consume();
/// }
/// ```
///
/// The other half of the split between keys and text. By the time this arrives
/// the layout, any dead key, any compose sequence and any IME conversion have all
/// been applied, so a text input appends [#text()] and never reasons about keys
/// at all. A container sees it before its focused child (capture), then the child
/// does, and [#consume()] stops it going further.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#keys-and-text-are-different-events).
public final class TextEvent {

    private final String text;
    private final Element target;
    private boolean consumed;

    public TextEvent(String text, Element target) {
        this.text = Objects.requireNonNull(text, "text");
        this.target = target;
    }

    /// The committed text. Usually one character, but a compose sequence or an
    /// IME conversion can commit several at once.
    public String text() {
        return text;
    }

    public Element target() {
        return target;
    }

    public void consume() {
        consumed = true;
    }

    public boolean isConsumed() {
        return consumed;
    }

    @Override
    public String toString() {
        return "text \"" + text + "\"" + (consumed ? " consumed" : "");
    }
}
