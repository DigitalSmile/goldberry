package io.github.digitalsmile.goldberry.input;

import io.github.digitalsmile.goldberry.widget.Element;
import java.util.Objects;

/// Text the platform has finished translating, delivered to whatever has focus.
///
/// The other half of §7.1's split. By the time this arrives the layout, any dead
/// key, any compose sequence and any IME conversion have all been applied — so a
/// text input appends [#text()] and never reasons about keys at all.
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
