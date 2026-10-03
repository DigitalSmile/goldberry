package dev.goldberry.example.ui.drawing;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.image.qr.Level;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.icon.IconView;
import dev.goldberry.widgets.core.qrcode.QrCode;

/// The Drawing screen's two figures that need no painter: a QR code at three
/// error-correction levels, and a standalone icon at the sizes a stylesheet
/// gives its box.
///
/// Read more: [`qr-code`](https://goldberry.dev/docs/components/drawing.html#qr-code).
final class FigureCards {

    /// What the codes carry: a link, because a sign-in token is a credential and a
    /// screenshot of the gallery is published.
    private static final String SHARE_LINK = "https://goldberry.example/open/showcase";

    private FigureCards() {}

    /// The same link at levels L, M and H.
    static Widget qrCodes() {
        return new ShowcaseCard(
                        "qr-card",
                        "A code for a phone",
                        "The same link at error correction L, M and H: the more of a code may be lost, the denser"
                                + " it is. Each module is whole device pixels at every display scale, so a phone"
                                + " camera reads it. Point one at the screen.",
                        DocLink.to("components/drawing", "qr-code"))
                .of(new Row(
                        List.of(
                                new QrCode(SHARE_LINK, Level.L, 4, Attributes.NONE.id("qr-low")),
                                new QrCode(SHARE_LINK, Level.M, 4, Attributes.NONE.id("qr-medium")),
                                new QrCode(SHARE_LINK, Level.H, 4, Attributes.NONE.id("qr-high"))),
                        Attributes.NONE.id("qr-row")));
    }

    /// One icon at three sizes the stylesheet chose, one coloured and named, and a
    /// name the set does not have.
    static Widget icons() {
        return new ShowcaseCard(
                        "drawing-icon",
                        "One icon, any size",
                        "The icon widget draws a bundled or registered icon at the size of its box, in the box's"
                                + " colour. These are 11, 16 and 24 points by a class each. A name nothing has is"
                                + " an empty box with the class missing, drawn here as a dot.",
                        DocLink.to("components/drawing", "icon"))
                .of(new Row(
                        List.of(
                                new IconView("cloud-upload").styled("small"),
                                new IconView("cloud-upload"),
                                new IconView("cloud-upload").styled("large"),
                                new IconView("circle-alert")
                                        .withAttributes(
                                                Attributes.NONE.classes("warn").name("Not signed in")),
                                new IconView("no-such-icon")),
                        Attributes.NONE.id("drawing-icon-row")));
    }
}
