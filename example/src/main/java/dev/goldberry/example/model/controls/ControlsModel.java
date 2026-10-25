package dev.goldberry.example.model.controls;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.bind.Action;
import dev.goldberry.bind.Bind;
import dev.goldberry.bind.Model;
import dev.goldberry.widgets.controls.checkbox.Checkbox;

/// What the Buttons, Choices and Values screens' controls hold: the road
/// counter, the verse and the half-ticked box, the gain, the chips and the tags.
///
/// Read more: [Values](https://goldberry.dev/docs/applications.html#values).
@Model
// Every field below is read, and none of them is read *here*: a `@Bind` field is
// reached by path through the generated registry, or through a woven call site
// in a native image. The reader is generated.
@SuppressWarnings("UnusedVariable")
public final class ControlsModel {

    /// The five tags the dismissable row starts with, and goes back to.
    private static final List<String> TAGS = List.of("java", "kdl", "blend2d", "harfbuzz", "yoga");

    /// Leagues walked from Bag End. The counter, in the one unit a hobbit
    /// measures anything in.
    @Bind("app.clicks")
    @SuppressWarnings("unused")
    private int clicks;

    @Bind("app.prose")
    @SuppressWarnings("unused")
    private boolean showProse = true;

    @Bind("app.partly")
    @SuppressWarnings("unused")
    private Checkbox.Value partly = Checkbox.Value.MIXED;

    /// A `Number` rather than a `double`, because that is genuinely what it
    /// holds: the initial value is a whole 40 and a slider reports fractions, and
    /// what a `text` widget prints is whichever it was last given. A `double`
    /// here would render the starting value as `40.0`.
    @Bind("app.gain")
    @SuppressWarnings("unused")
    private Number gain = 40;

    /// The three filters the Basic screen's chip row offers.
    ///
    /// Three booleans rather than a `Set<String>`, because a chip's `bind=` reads
    /// **one** value and a binding has no way to say "is this string in that
    /// set". That is the shape of the widget rather than a shortcut: a chip is a
    /// control with a state, exactly like the `checkbox` three cards above it,
    /// and a set would need a second kind of binding for no new behaviour.
    ///
    /// Independent, and that is the argument for chips over `segmented`: none,
    /// some or all of them may be on, and nothing about the row makes that wrong.
    @Bind("app.chip-unread")
    @SuppressWarnings("unused")
    private boolean chipUnread = true;

    @Bind("app.chip-starred")
    @SuppressWarnings("unused")
    private boolean chipStarred;

    @Bind("app.chip-archived")
    @SuppressWarnings("unused")
    private boolean chipArchived;

    /// The tags the Basic screen's dismissable row shows.
    ///
    /// A list because a × **removes** one, which is a structural change: the row
    /// is a different tree afterwards rather than the same tree with a different
    /// value, and the widget cannot make it happen itself — a chip asks and this
    /// list is what answers.
    ///
    /// Assignment is what is observed, which is why it is replaced rather than
    /// edited: a list mutated in place changes nothing anybody can see.
    @Bind("app.tags")
    @SuppressWarnings("unused")
    private List<String> tags = TAGS;

    public boolean isProseShown() {
        return showProse;
    }

    public boolean hasClicks() {
        return clicks > 0;
    }

    public int clicks() {
        return clicks;
    }

    /// The tags the chip row shows, for the card that builds one chip per tag.
    ///
    /// A reader rather than a binding, because what the card needs is the **list**
    /// and a `bind=` reads one value: the row is a different tree per tag, which
    /// is Java's half of the split this screen makes everywhere.
    public List<String> tags() {
        return tags;
    }

    /// Everything that *happens* to the controls' values.
    ///
    /// Read more: [Actions](https://goldberry.dev/docs/applications.html#actions).
    @dev.goldberry.bind.runtime.Actions
    public record Actions(ControlsModel values) {

        // --- the road --------------------------------------------------------

        @Action("app.click")
        @SuppressWarnings("unused")
        public void click() {
            values.clicks++;
        }

        @Action("app.undo")
        @SuppressWarnings("unused")
        public void undo() {
            values.clicks = Math.max(0, values.clicks - 1);
        }

        @Action("app.reset")
        @SuppressWarnings("unused")
        public void reset() {
            values.clicks = 0;
        }

        // --- the choices -----------------------------------------------------

        /// The other half of the loop: the checkbox is handed the read-only
        /// half of `showProse` and cannot write it, so the tick moves only when this
        /// moves it. Delete this method and the control stops working — which is the
        /// behaviour, not a bug.
        @Action("app.toggle-prose")
        @SuppressWarnings("unused")
        public void toggleProse() {
            values.showProse = !values.showProse;
        }

        /// What the *switch* asks for, and the reason it is not [#toggleProse()]. A
        /// drag is a request for a **particular** state rather than for the other one
        /// — dragging right on a switch already on asks for on — so the value comes up
        /// with the event and this sets exactly it.
        @Action("app.set-prose")
        @SuppressWarnings("unused")
        void setProse(boolean value) {
            values.showProse = value;
        }

        /// `Checkbox.Value.toggled()` is what an application applies: from `MIXED`
        /// this goes to `CHECKED`, and a user can never get back to mixed by clicking
        /// — only the application can put it there.
        @Action("app.toggle-partly")
        @SuppressWarnings("unused")
        void togglePartly() {
            values.partly = values.partly.toggled();
        }

        /// Already snapped and clamped by whichever control asked. The application
        /// does no arithmetic at all, which is the point.
        @Action("app.set-gain")
        @SuppressWarnings("unused")
        void setGain(double value) {
            values.gain = value;
        }

        // --- the chips -------------------------------------------------------

        /// One `press` per chip, because a `press=` is a [Runnable] and names no
        /// value.
        ///
        /// A valued `change=` would be the other shape — one action taking the
        /// chip's name — and it is deliberately not what a chip does: a chip
        /// reports **that it was chosen** and not *what* was chosen, because the
        /// value is the widget itself. A row of three that shared one handler
        /// would be a `segmented` with rounder corners.
        @Action("app.toggle-unread")
        @SuppressWarnings("unused")
        public void toggleUnread() {
            values.chipUnread = !values.chipUnread;
        }

        @Action("app.toggle-starred")
        @SuppressWarnings("unused")
        public void toggleStarred() {
            values.chipStarred = !values.chipStarred;
        }

        @Action("app.toggle-archived")
        @SuppressWarnings("unused")
        public void toggleArchived() {
            values.chipArchived = !values.chipArchived;
        }

        /// Takes a tag off the row — what a chip's × asks for.
        ///
        /// The chip removes nothing itself: it asks, and this is the only thing
        /// that can shorten the list. A `dismiss` handler that did nothing would
        /// leave the chip where it was, which is the visible form of "the model
        /// did not change".
        public void dropTag(String tag) {
            var remaining = new ArrayList<>(values.tags);
            remaining.remove(tag);
            values.tags = List.copyOf(remaining);
        }

        /// Puts the five back, so the card can be tried more than once.
        @Action("app.reset-tags")
        @SuppressWarnings("unused")
        public void resetTags() {
            values.tags = TAGS;
        }
    }
}
