package dev.goldberry.example.ui.input;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// The **Input** screen: a card for every section of the guide's input chapter.
/// Most of them are pads that report what the router tells them.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html).
///
/// @param context what the screen is built from
public record InputChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("guide/input");

    /// The cursors the cursor card shows, by their CSS names.
    static final List<String> CURSORS =
            List.of("pointer", "text", "crosshair", "move", "ew-resize", "ns-resize", "wait", "not-allowed");

    @Override
    public Widget build(BuildContext buildContext) {
        return Wall.of(
                "input",
                "Input",
                "Pointer, wheel and keyboard events go through one router that remembers who is hovered, pressed"
                        + " and focused. Each pad here reports what it is told.",
                CHAPTER,
                List.of(
                        new EventTravelCard(),
                        new PointerKindsCard(),
                        new CaptureCard(),
                        new DragCard(),
                        new WheelCard(),
                        new KeysCard(),
                        new AcceleratorsCard(),
                        new FocusCard(),
                        new CompositeCard(),
                        cursors(),
                        new DropCard(),
                        contextMenu(),
                        customWidget()));
    }

    /// A box per cursor, each set by one CSS rule.
    static Widget cursors() {
        var boxes = CURSORS.stream()
                .<Widget>map(name -> new Panel(
                        List.of(new Text(name)),
                        Attributes.NONE.id("cursor-" + name).classes("cursor-box")))
                .toList();
        return new ShowcaseCard(
                        "input-cursor",
                        "The cursor",
                        "The cursor is a property of the painted box, set from CSS or from code, and it covers"
                                + " everything drawn inside the box. Move the pointer across the boxes.",
                        DocLink.to("guide/input", "the-cursor"))
                .of(new Row(boxes, Attributes.NONE.id("cursor-boxes").classes("cursor-boxes")));
    }

    /// A panel whose `context-menu` names the window's menu.
    static Widget contextMenu() {
        return new ShowcaseCard(
                        "input-context",
                        "Context menus",
                        "context-menu=\"name\" on any widget names a menu. A right-click, the Menu key or Shift+F10"
                                + " finds the name by walking up from where it happened, and the application says"
                                + " what the name opens.",
                        DocLink.to("guide/input", "context-menus"))
                .of(new Panel(
                                List.of(
                                        new Text("Right-click anywhere in this panel."),
                                        new Button("Or focus me and press Shift+F10").id("context-focus")),
                                Attributes.NONE.id("context-panel").classes("context-panel"))
                        .contextMenu("content"));
    }

    /// The widget contract, which is the guide's table.
    static Widget customWidget() {
        return new ShowcaseCard(
                        "input-custom",
                        "What a custom widget implements",
                        "A widget takes part in input by implementing Handles, where every method has a default:"
                                + " pointer and key handlers in two phases, text, focus, the Tab scope and the"
                                + " caret. A canvas takes the same through its own Input.",
                        DocLink.to("guide/input", "what-a-custom-widget-implements"))
                .reference();
    }
}
