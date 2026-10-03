package dev.goldberry.example.ui.guide;

import java.util.List;

/// The chapters of the guide the Guide screen links, in the book's parts and
/// order.
///
/// Each summary is the chapter's opening, shortened. The layout part is not
/// here: the Layout screen's header links it.
///
/// Read more: [Goldberry](https://goldberry.dev/docs/introduction.html).
public final class GuideParts {

    /// One chapter: a card's id, its title, a sentence or two, and the page.
    ///
    /// @param id      the card's id
    /// @param title   the chapter's title
    /// @param summary what the chapter is about
    /// @param page    the chapter, as the book writes it, without `.md`
    public record Chapter(String id, String title, String summary, String page) {}

    /// One part of the book.
    ///
    /// @param title    the part's caption
    /// @param chapters its chapters, in order
    public record Part(String title, List<Chapter> chapters) {

        public Part {
            chapters = List.copyOf(chapters);
        }
    }

    /// Every part, in the book's order.
    public static final List<Part> PARTS = List.of(
            new Part(
                    "Overview",
                    List.of(
                            new Chapter(
                                    "guide-introduction",
                                    "Goldberry",
                                    "A declarative desktop UI toolkit in pure Java 25: widgets are records or KDL,"
                                            + " styled with a CSS subset, laid out by flexbox, and shipped as a jar"
                                            + " or one native binary.",
                                    "introduction"),
                            new Chapter(
                                    "guide-concept",
                                    "Concept",
                                    "Goldberry draws every pixel itself, describes a screen as data, and keeps the"
                                            + " native code behind one boundary. The idea in five parts.",
                                    "overview/concept"),
                            new Chapter(
                                    "guide-architecture",
                                    "Architecture",
                                    "Five layers, three trees and one native boundary: the map an application"
                                            + " developer needs.",
                                    "overview/architecture"),
                            new Chapter(
                                    "guide-limitations",
                                    "Limitations",
                                    "What Goldberry does not do today, in one place, and whether each gap is"
                                            + " deliberate, deferred or in progress.",
                                    "overview/limitations"),
                            new Chapter(
                                    "guide-catalogue",
                                    "The catalogue",
                                    "Every widget is a Java record, a KDL node and a CSS type. What they all share,"
                                            + " and which chapter holds the one you want.",
                                    "components/index"))),
            new Part(
                    "Getting started",
                    List.of(
                            new Chapter(
                                    "guide-requirements",
                                    "Requirements",
                                    "A JDK 25 and a desktop. Everything else is inside the jars.",
                                    "getting-started/requirements"),
                            new Chapter(
                                    "guide-installing",
                                    "Installing",
                                    "Add the BOM for the version, the umbrella artifact, and one natives jar per"
                                            + " platform you run on.",
                                    "getting-started/installing"),
                            new Chapter(
                                    "guide-first-java",
                                    "Your first Java application",
                                    "A counter: a model with one value and one action, a document that names them,"
                                            + " a stylesheet and the class that opens the window.",
                                    "getting-started/first-java-application"),
                            new Chapter(
                                    "guide-first-native",
                                    "Your first native application",
                                    "The same counter as one executable: no JDK on the target machine, the native"
                                            + " library inside the file, a window up in a tenth of a second.",
                                    "getting-started/first-native-application"))),
            new Part(
                    "Performance",
                    List.of(
                            new Chapter(
                                    "guide-frame-cost",
                                    "What a frame costs",
                                    "The numbers to expect from a window and where each was measured: start-up in a"
                                            + " tenth of a second, a settled frame in microseconds.",
                                    "performance/index"),
                            new Chapter(
                                    "guide-startup",
                                    "Starting fast",
                                    "What each way of launching costs, how to train an AOT cache, and what to keep"
                                            + " off the path to the first frame.",
                                    "performance/startup"),
                            new Chapter(
                                    "guide-frames",
                                    "Keeping frames cheap",
                                    "What the toolkit already removes from a frame, and the habits that keep an"
                                            + " application from undoing it.",
                                    "performance/frames"),
                            new Chapter(
                                    "guide-measuring",
                                    "Measuring",
                                    "A trace line per frame, a resize run that reports its cost, and benchmarks:"
                                            + " how to measure your own application rather than trust the figures.",
                                    "performance/measuring"))),
            new Part(
                    "Developer guide",
                    List.of(
                            new Chapter(
                                    "guide-testing",
                                    "Testing an application",
                                    "Render a tree to pixels with no display, step a virtual clock, click through the"
                                            + " real input router, and compare pictures with a tolerance.",
                                    "guide/testing"),
                            new Chapter(
                                    "guide-writing-a-widget",
                                    "Writing a widget",
                                    "A widget is an immutable record that describes a box. Implement the interfaces"
                                            + " it needs and annotate it with its node name.",
                                    "guide/writing-a-widget"),
                            new Chapter(
                                    "guide-weaving",
                                    "Model weaving",
                                    "A model is plain Java, and the build makes assignments to it observable. A jar"
                                            + " needs no build step; a native image is built from a woven model.",
                                    "weaving"),
                            new Chapter(
                                    "guide-native",
                                    "Native image",
                                    "An application as a GraalVM native image: no class loading, no reflection on"
                                            + " the binding path, and start-up measured against the process.",
                                    "native"))),
            new Part(
                    "Contributing",
                    List.of(
                            new Chapter(
                                    "guide-contributing",
                                    "Contributing",
                                    "Apache 2.0 with no contributor agreement. Every change is a pull request that"
                                            + " passes the same gates.",
                                    "contributing/index"),
                            new Chapter(
                                    "guide-building",
                                    "Building from source",
                                    "One command builds and tests every Java module and the native library for this"
                                            + " machine; one property leaves the native library out.",
                                    "contributing/building"),
                            new Chapter(
                                    "guide-repository",
                                    "Repository layout",
                                    "The Gradle modules, which of them are published, and a package rule that lets"
                                            + " you read the pipeline off the package list.",
                                    "contributing/repository"),
                            new Chapter(
                                    "guide-gates",
                                    "Tests and gates",
                                    "Pixels are the assertion for painters, every suite runs in both binding modes,"
                                            + " and a cost is guarded by a count rather than a clock.",
                                    "contributing/testing"),
                            new Chapter(
                                    "guide-releasing",
                                    "Releasing",
                                    "A version is a year and a count, every push publishes a snapshot, and a release"
                                            + " is a tag on the commit that declares it.",
                                    "contributing/releasing"),
                            new Chapter(
                                    "guide-decisions",
                                    "Recording a decision",
                                    "A choice with alternatives and costs is written down once, numbered next in"
                                            + " line, and never edited; the build checks that it is there.",
                                    "contributing/decisions"),
                            new Chapter(
                                    "guide-doc-comments",
                                    "Writing a doc comment",
                                    "What the object is, how to use it and how it works, then a link to the chapter"
                                            + " of the guide that says more.",
                                    "contributing/doc-comments"))),
            new Part(
                    "Reference",
                    List.of(
                            new Chapter(
                                    "guide-status",
                                    "Status",
                                    "What is built, milestone by milestone, and what it cost to find out.",
                                    "status"),
                            new Chapter(
                                    "guide-todo",
                                    "Not done yet",
                                    "What is deferred, known to be broken, or specified and unbuilt, with what each"
                                            + " gap is waiting on.",
                                    "TODO"))));

    private GuideParts() {}
}
