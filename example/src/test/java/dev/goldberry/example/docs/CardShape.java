package dev.goldberry.example.docs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.Summaries;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.kdl.KdlValue;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.text.Link;
import dev.goldberry.widgets.text.Text;

/// What a gallery card must have, checked on the widget a screen built and on the
/// node a document wrote: a `row.card-head` holding a `text.card-title` and a
/// `link.card-docs` into the guide, then a `text.card-summary`.
///
/// Each check answers with the problems it found, so a test can list every card
/// that is wrong at once rather than stopping at the first.
public final class CardShape {

    private CardShape() {}

    /// Whether `card` is a gallery card at all.
    public static boolean isGalleryCard(Card card) {
        return card.attributes().classes().contains(ShowcaseCard.CARD);
    }

    /// Whether `node` is a gallery card written in a document.
    public static boolean isGalleryCard(KdlNode node) {
        return node.name().equals("card") && classesOf(node).contains(ShowcaseCard.CARD);
    }

    /// The section of the guide a built card links, if its head has a link.
    public static Optional<DocLink> link(Card card) {
        return head(card)
                .flatMap(CardShape::docs)
                .flatMap(link -> Optional.ofNullable(link.href()))
                .flatMap(DocLink::parse);
    }

    /// What is wrong with a card a screen built.
    public static List<String> problems(Card card) {
        var problems = new ArrayList<String>();
        var name = "card #" + card.attributes().id();
        var head = head(card);
        if (head.isEmpty()) {
            problems.add(name + " does not start with a row.card-head");
        } else {
            var title = head.orElseThrow().children().stream()
                    .filter(child -> child instanceof Text text
                            && text.attributes().classes().contains("card-title"))
                    .map(child -> ((Text) child).content())
                    .findFirst();
            if (title.isEmpty() || title.orElseThrow().isBlank()) {
                problems.add(name + " has no text.card-title in its head");
            }
            var docs = docs(head.orElseThrow());
            if (docs.isEmpty()) {
                problems.add(name + " has no link.card-docs in its head");
            } else {
                problems.addAll(linkProblems(name, docs.orElseThrow().href()));
            }
        }
        var children = card.children();
        if (children.size() < 2
                || !(children.get(1) instanceof Text summary
                        && summary.attributes().classes().contains("card-summary"))) {
            problems.add(name + " has no text.card-summary under its head");
        } else {
            problems.addAll(summaryProblems(name, ((Text) children.get(1)).content()));
        }
        return problems;
    }

    /// What is wrong with a card a document wrote.
    public static List<String> problems(KdlNode card) {
        var problems = new ArrayList<String>();
        var name = "card id=\"" + card.stringProperty("id") + "\" at " + card.position();
        var children = card.children();
        if (children.isEmpty()
                || !children.getFirst().name().equals("row")
                || !classesOf(children.getFirst()).contains("card-head")) {
            problems.add(name + " does not start with row class=\"card-head\"");
        } else {
            var head = children.getFirst().children();
            var title = head.stream()
                    .filter(node ->
                            node.name().equals("text") && classesOf(node).contains("card-title"))
                    .findFirst()
                    .flatMap(CardShape::argument);
            if (title.isEmpty() || title.orElseThrow().isBlank()) {
                problems.add(name + " has no text class=\"card-title\" in its head");
            }
            var docs = head.stream()
                    .filter(node ->
                            node.name().equals("link") && classesOf(node).contains("card-docs"))
                    .findFirst();
            if (docs.isEmpty()) {
                problems.add(name + " has no link class=\"card-docs\" in its head");
            } else {
                problems.addAll(linkProblems(name, docs.orElseThrow().stringProperty("href")));
            }
        }
        if (children.size() < 2
                || !children.get(1).name().equals("text")
                || !classesOf(children.get(1)).contains("card-summary")) {
            problems.add(name + " has no text class=\"card-summary\" under its head");
        } else {
            problems.addAll(summaryProblems(name, argument(children.get(1)).orElse("")));
        }
        return problems;
    }

    private static Optional<Row> head(Card card) {
        return card.children().isEmpty()
                        || !(card.children().getFirst() instanceof Row row
                                && row.attributes().classes().contains("card-head"))
                ? Optional.empty()
                : Optional.of((Row) card.children().getFirst());
    }

    private static Optional<Link> docs(Row head) {
        return head.children().stream()
                .filter(child -> child instanceof Link link
                        && link.attributes().classes().contains("card-docs"))
                .map(Link.class::cast)
                .findFirst();
    }

    private static List<String> linkProblems(String name, String href) {
        if (href == null) {
            return List.of(name + "'s docs link has no href");
        }
        var link = DocLink.parse(href);
        if (link.isEmpty()) {
            return List.of(name + "'s docs link " + href + " is not an address on " + DocLink.SITE);
        }
        if (!BookSections.resolves(link.orElseThrow())) {
            return List.of(name + "'s docs link " + href + " names a chapter or a heading the guide does not have");
        }
        return List.of();
    }

    private static List<String> summaryProblems(String name, String summary) {
        try {
            Summaries.require(name, summary);
            return List.of();
        } catch (IllegalArgumentException refused) {
            return List.of(refused.getMessage());
        }
    }

    private static List<String> classesOf(KdlNode node) {
        var classes = node.stringProperty("class");
        return classes == null ? List.of() : List.of(classes.trim().split("\\s+"));
    }

    private static Optional<String> argument(KdlNode node) {
        return node.argument().map(KdlValue::asString);
    }
}
