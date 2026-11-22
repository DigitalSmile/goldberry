package dev.goldberry.example.ui.forms;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.spelling.ShowcaseDictionary;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.form.textarea.TextArea;
import dev.goldberry.widgets.form.textinput.TextInput;

/// The spelling card: a note and a title, both checked against the showcase's
/// English dictionary, with the misspelled words drawn as `::spelling-error`
/// draws them.
///
/// `spellcheck=true` is said once, on the card, and both fields inside take
/// it. Right-click a word with a wavy line for its suggestions.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
public record SpellingCard() implements Widget.Stateless {

    /// The card's id.
    static final String ID = "spelling-card";

    /// The note's words, two of them misspelled on purpose.
    static final String NOTE = "The hobbits recieved a letter from Gandalf\nand left the Shire the folowing morning.";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "Spelling",
            "A field that says spellcheck draws a wavy line under a word its dictionary does not know. "
                    + "Right-click one for suggestions, or add it to the dictionary.",
            DocLink.to("components/forms", "text-input"));

    @Override
    public Widget build(BuildContext context) {
        // Installed the first time the card is built, in the background in a
        // window; the fields find it through the registry when it arrives, as
        // an application's would.
        ShowcaseDictionary.install();
        // Said once, on the card, and both fields inside take it.
        return CARD.of(
                        new TextInput("A jorney there and back", value -> {}).id("spelling-title"),
                        new TextArea(NOTE, value -> {}).rows(3).id("spelling-note"))
                .spellcheck(true);
    }
}
