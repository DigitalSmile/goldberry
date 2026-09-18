package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.controls.button.Floated;
import io.github.digitalsmile.goldberry.widgets.controls.chip.Chip;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.panel.card.Card;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;
import io.github.digitalsmile.goldberry.widgets.text.Link;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Basic** screen: §1's type scale, §2's wrapped paragraph and **every §3
/// control the toolkit has** — `button` in all five variants and both icon forms,
/// `toggle`, `checkbox`, `radio`/`radio-group`, `slider`, `knob`, `select`,
/// `segmented`, `progress`, `spinner` and `badge`.
///
/// The list is the point: §3 names eleven control families and this screen shows
/// all eleven, so "what does the catalogue actually have" is answered by a
/// picture rather than by reading a table. What it does **not** show is the parts
/// of §3 that are specified and unbuilt — a button's `outlined`, `square`,
/// `circle` and `float`, and `select`'s `multiple`, `autocomplete` and `tree`,
/// which the Forms screen carries where they exist.
///
/// ## Why it is a document *and* a class
///
/// Seven of its ten cards are `basic.kdl` and three of them cannot be, and every
/// reason is an interesting one rather than incidental:
///
/// - **The prose card is in the tree only while the checkbox says so.** That is a
///   structural change and not a value one — the paragraph is *absent*, not
///   hidden — and §8's markup has no way to say it.
/// - **The road card's Turn back and Begin again are disabled while the count is
///   zero.** `disabled=#true` is a constant; a document cannot say "disabled when
///   this property is zero" and is not going to grow a way to, because a document
///   that could evaluate `clicks == 0` would be code in a data file with no stack
///   trace when it went wrong ([ADR-0062], [ADR-0110]).
/// - **The dialogs card holds what a dialog answered**, and a dialog answers
///   *later* — a person is inside the call. A document can name an action; it has
///   nowhere to put a result that arrives on a callback ([FileDialogsCard]).
///
/// So this class takes the wall the document built and appends three cards to it —
/// to the *same* masonry, because a masonry places by column height and a second
/// wall underneath would be laid out against different columns
/// (ADR-0222).
///
/// @param cards what `basic.kdl` built, inflated once by [Screen]
/// @param plus  the icon on the primary button — handed in, because a widget is a
///              value that is rebuilt and thrown away and an `Icon` is parsed and
///              scaled once, at the size it is drawn at (ADR-0043, ADR-0277)
public record Basic(ShowcaseModel model, ShowcaseModel.Actions actions, Masonry cards, Icon plus)
        implements Widget.Stateless {

    private static final String NOTE = "§1's type scale, §2's paragraph and every §3 control the toolkit has —"
            + " button, toggle, checkbox, radio, slider, knob, select, segmented,"
            + " progress, spinner and badge. Seven of these cards are basic.kdl and"
            + " three of them cannot be: the verse is in the tree only while the box"
            + " beside it is ticked, Turn back is disabled while the count is zero,"
            + " and a file dialog answers long after the click that opened it.";

    /// The paragraph, which is here to be **re-wrapped** rather than to be read.
    ///
    /// Long enough to break over several lines at any window width, and written
    /// in sentences of very different lengths so that dragging the window's edge
    /// visibly moves where the breaks fall. A block of `Lorem ipsum` would do the
    /// same thing and tell a reader nothing about whether it had done it *well*.
    private static final String PROSE = """
            Yoga proposes a width and this paragraph answers with a height, which is the only \
            thing a flexbox algorithm needs to know about text. The answer comes back through a \
            Java method called from C returning a struct by value — the fiddliest thing the \
            toolkit asks of the Foreign Function & Memory API, and the reason ADR-0017 exists.

            Drag the window's edge and the text re-wraps without being shaped again. Click a \
            button, or press Tab until one has the focus and then Space. Ctrl+T changes the \
            light from the keyboard, and Ctrl+D changes the density — every control gets 4px \
            shorter, and nothing in this file mentions a height.

            The two theme radios are one Tab stop, not two: Tab reaches the group and the arrow \
            keys move inside it. Tab away and back and you land on whichever is selected — \
            including after Ctrl+T has changed it from outside the group, because the selection \
            is the position rather than something remembered beside it.""";

    @Override
    public Widget build(BuildContext context) {
        var extra = new ArrayList<Widget>(4);
        if (model.isProseShown()) {
            extra.add(prose());
        }
        extra.add(road());
        extra.add(tags());
        extra.add(shapes());
        extra.add(links());
        // A value, like every other widget here: the state that remembers what
        // the last dialog answered is the card's own, made once when it is
        // mounted and kept across the rebuilds this screen does for everything
        // else.
        extra.add(new FileDialogsCard());
        return Wall.of("basic", "Basic", NOTE, cards, extra.toArray(Widget[]::new));
    }

    /// §3's four remaining button options ([ADR-0347]): `outlined`, which
    /// composes with the semantic variants; `square`, for buttons that butt
    /// against each other; `circle`, which an icon-only button is without being
    /// told; and `float`, which lifts a button out of this card and into the
    /// window's corner — so the `+` at the bottom right of this screen is
    /// described here and drawn there.
    private Widget shapes() {
        return Notifications.card(
                "shapes-card",
                "Four more buttons",
                List.of(
                        new Row(
                                List.of(
                                        new Button("Outlined", actions::click).styled(Button.OUTLINED),
                                        new Button("Danger", actions::click).styled(Button.OUTLINED, "danger"),
                                        new Button("Square", actions::click).styled(Button.SQUARE),
                                        new Button("", plus, actions::click, false, null).id("circle-button")),
                                Attributes.NONE.id("shapes-row")),
                        new Text(
                                "Outlined is a transparent fill with a border, and it composes with"
                                        + " danger. The disc on the right was not told to be one: an"
                                        + " icon-only button is a circle unless it says square. The"
                                        + " floating + in the window's corner is a button on this card,"
                                        + " lifted out of it.",
                                Attributes.NONE.classes("caption")),
                        new Floated(new Button(
                                "",
                                plus,
                                actions::click,
                                false,
                                Attributes.NONE.id("float-button").classes("primary")))));
    }

    /// §2's `link`: a word that does something, in-app or outside the window
    /// ([ADR-0346]).
    private Widget links() {
        return Notifications.card(
                "links-card",
                "Text that does something",
                List.of(
                        new Link("March a league, as a link", actions::click).id("league-link"),
                        new Link("Somewhere you have been", actions::click).visited(true),
                        Link.external("Goldberry on GitHub", "https://github.com/DigitalSmile/goldberry")
                                .id("github-link"),
                        new Text(
                                "The first two stay in the window; the third goes to the desktop's"
                                        + " browser, carries the icon that says so, and says so to a"
                                        + " screen reader. Visited is the application's word — the"
                                        + " toolkit keeps no history.",
                                Attributes.NONE.classes("caption"))));
    }

    /// The paragraph card. Absent from the returned list rather than emptied,
    /// which is what "structural" means here: the element and every style it had
    /// resolved go with it.
    private Widget prose() {
        return new Card(
                List.of(
                        new Text("A paragraph, and a window edge to drag", Attributes.NONE.classes("card-title")),
                        new Text(PROSE).id("prose")),
                Attributes.NONE.id("prose-card").classes("wall-card"));
    }

    /// The dismissable chips, and the fourth card that cannot be a document.
    ///
    /// A × **removes** a chip, so the row is a different *tree* after a dismiss
    /// rather than the same tree with a different value — and §8's markup has no
    /// way to describe a list that shortens. It is the road card's reason wearing
    /// different clothes, and it is the clearest demonstration of ADR-0063 on this
    /// screen: the chip asks, `dropTag` answers, and the row redraws from what the
    /// model now holds. Delete `dropTag` and the × stops working, which is the
    /// behaviour rather than a bug.
    ///
    /// `Reset` rather than leaving an emptied row, because a card a reader can
    /// only use once is a card most readers never see working.
    private Widget tags() {
        var tags = model.tags();
        var chips = new ArrayList<Widget>(tags.size() + 1);
        for (var tag : tags) {
            // Keyed by the tag, so removing one from the middle reconciles the
            // rest in place rather than shuffling every chip's state along one
            // (ADR-0004).
            chips.add(new Chip(tag)
                    .onDismiss(() -> actions.dropTag(tag))
                    .keyed(tag)
                    .id("tag-" + tag));
        }
        if (tags.isEmpty()) {
            chips.add(new Text("Nothing left to take off.", Attributes.NONE.classes("caption")));
        }
        return new Card(
                List.of(
                        new Text("Tags you can take off", Attributes.NONE.classes("card-title")),
                        new Row(chips, Attributes.NONE.id("chip-tags")),
                        new Text(
                                "Each × asks the model to drop one. The chip removes nothing itself —"
                                        + " which is why this card is Java and the two rows above it are"
                                        + " basic.kdl.",
                                Attributes.NONE.classes("caption")),
                        new Button("Put them back", actions::resetTags).id("reset-tags")),
                Attributes.NONE.id("tag-card").classes("wall-card"));
    }

    /// The counter, and the card that is in Java because two of its buttons
    /// answer a question about a value rather than carry a constant.
    private Widget road() {
        var walked = model.clicks();
        return new Card(
                List.of(
                        new Text("The road, in leagues", Attributes.NONE.classes("card-title")),
                        new Text(
                                        walked == 0
                                                ? "Nobody has left Bag End yet."
                                                : walked + (walked == 1 ? " league" : " leagues") + " walked.")
                                .id("leagues"),
                        new Row(
                                        new Button("March a league", actions::click)
                                                .withIcon(plus)
                                                .id("click")
                                                .styled("primary"),
                                        // The icon-only button, which is here
                                        // rather than in `basic.kdl` because it
                                        // is the one button shape a document
                                        // cannot safely write: with no label to
                                        // fall back on, an icon the registry
                                        // does not answer makes it illegal
                                        // (ADR-0293). `name=` is the accessible
                                        // name §1.6 asks for in a label's place.
                                        new Button(
                                                "",
                                                plus,
                                                actions::click,
                                                false,
                                                Attributes.NONE.id("click-icon").name("March a league")),
                                        new Button("Turn back", actions::undo)
                                                .disabled(!model.hasClicks())
                                                .id("undo"),
                                        new Button("Begin again", actions::reset)
                                                .disabled(!model.hasClicks())
                                                .id("reset")
                                                .styled("danger"))
                                .id("actions"),
                        new Text(
                                "Two of these four are disabled until there is something to undo,"
                                        + " which is the whole reason this card is not in basic.kdl —"
                                        + " and the bare + is the other reason: an icon-only button"
                                        + " has no label to fall back on if a registry does not answer"
                                        + " the name, so it belongs where the icon is an object.",
                                Attributes.NONE.classes("caption"))),
                Attributes.NONE.id("road-card").classes("wall-card"));
    }
}
