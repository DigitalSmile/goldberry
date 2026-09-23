package io.github.digitalsmile.goldberry.build.format;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts back the imports a formatter removed because the only thing using them was
 * a JDK 23+ {@code ///} Markdown doc comment.
 *
 * <h2>Why this exists</h2>
 *
 * Both unused-import removers Spotless offers -- palantir-java-format's, which its
 * palantir step runs unconditionally before formatting, and google-java-format's,
 * behind {@code removeUnusedImports()} -- look for references in code and in
 * {@code /** ... *}{@code /} Javadoc. Neither reads {@code ///} comments. So an
 * import whose only use is a doc link such as {@code /// See [ActionRegistry].}
 * looks unused, and the formatter deletes it. The file still compiles, and the doc
 * link silently stops resolving -- which in this codebase, where almost every doc
 * comment is Markdown, meant {@code spotlessCheck} failing across five modules
 * with a "fix" that would have broken dozens of links.
 *
 * <p>This does not second-guess the remover in general. An import it dropped
 * comes back only if a {@code ///} comment in the formatted file still refers to
 * it by simple name; an import nothing refers to anywhere stays removed.
 *
 * <h2>What counts as a reference</h2>
 *
 * Inside {@code ///} lines, outside fenced code blocks and inline code spans:
 *
 * <ul>
 *   <li>Markdown reference links: {@code [Foo]}, {@code [Foo#bar(Baz)]},
 *       {@code [text][Foo.Inner]} and {@code [Foo][]} -- but not
 *       {@code [text](url)}, which is a hyperlink.</li>
 *   <li>The inline tags {@code {@link}}, {@code {@linkplain}} and
 *       {@code {@value}}.</li>
 *   <li>The block tags {@code @see}, {@code @throws} and {@code @exception}.</li>
 * </ul>
 *
 * A reference names the type it starts with and every parameter type of a member
 * signature, since javadoc resolves each of them through the imports.
 *
 * <p>The rule errs towards keeping: a {@code [word]} in prose that happens to
 * match a dropped import's simple name keeps that import. The cost of that is one
 * import too many; the cost of the opposite error is a broken link nobody sees.
 */
public final class MarkdownDocImports {

    /** A single-type or static import, capturing {@code static} and the name. */
    private static final Pattern IMPORT =
            Pattern.compile("(?m)^[ \\t]*import[ \\t]+(static[ \\t]+)?([\\w$.]+(?:\\.\\*)?)[ \\t]*;[ \\t]*$");

    private static final Pattern PACKAGE = Pattern.compile("(?m)^[ \\t]*package[ \\t]+[\\w$.]+[ \\t]*;[^\\n]*$");

    /** The text of a {@code ///} line: everything after the three slashes. */
    private static final Pattern DOC_LINE = Pattern.compile("^[ \\t]*///(.*)$");

    /** An opening or closing Markdown code fence. */
    private static final Pattern FENCE = Pattern.compile("^[ \\t]*(```|~~~)");

    /** An inline code span, of any backtick run length. */
    private static final Pattern CODE_SPAN = Pattern.compile("(`+).*?\\1");

    /**
     * {@code [first]} optionally followed by {@code [second]}, and not followed by
     * {@code (} -- which would make it an inline hyperlink.
     */
    private static final Pattern REFERENCE_LINK = Pattern.compile("\\[([^\\[\\]]*)](?:\\[([^\\[\\]]*)])?(?!\\()");

    /** {@code {@link Foo#bar(Baz) label}} and its siblings; group 1 is the reference. */
    private static final Pattern INLINE_TAG =
            Pattern.compile("\\{@(?:link|linkplain|value)[ \\t]+([^\\s}(]+(?:\\([^)]*\\))?)");

    /** {@code @throws FooException ...} and its siblings; group 1 is the reference. */
    private static final Pattern BLOCK_TAG =
            Pattern.compile("(?:^|\\s)@(?:see|throws|exception)[ \\t]+([^\\s(]+(?:\\([^)]*\\))?)");

    /**
     * A program-element reference: an optional dotted type, an optional
     * {@code #member}, and an optional parameter list.
     */
    private static final Pattern REFERENCE =
            Pattern.compile("^([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)?(?:#[\\w$]*(?:\\(([^)]*)\\))?)?$");

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");

    private MarkdownDocImports() {
    }

    /**
     * Restores the imports {@code after} lost from {@code before} that a
     * {@code ///} comment in {@code after} still refers to.
     *
     * <p>Restored imports are appended after the last import that survived, or
     * after the {@code package} declaration if none did. Their order and grouping
     * are deliberately not settled here: the {@code importOrder} step that runs
     * next rewrites the whole block.
     *
     * @param before the source as it was before the formatter ran
     * @param after  the formatter's output
     * @return {@code after}, with the doc-referenced imports put back; the same
     *     instance when there is nothing to restore
     */
    public static String restore(String before, String after) {
        var kept = imports(after);
        var keptSimpleNames = new LinkedHashSet<String>();
        for (var name : kept) {
            keptSimpleNames.add(simpleName(name));
        }

        var referenced = referencedNames(after);
        var restored = new ArrayList<String>();
        for (var name : imports(before)) {
            var simple = simpleName(name);
            // Not a clash with a kept import of the same simple name: that one
            // already answers the reference, and two would not compile.
            if (!kept.contains(name) && referenced.contains(simple) && keptSimpleNames.add(simple)) {
                restored.add(name);
            }
        }
        return restored.isEmpty() ? after : insert(after, restored);
    }

    /**
     * Every simple name a {@code ///} comment in {@code source} refers to.
     *
     * @param source Java source text
     * @return the names, in first-seen order
     */
    static Set<String> referencedNames(String source) {
        var names = new LinkedHashSet<String>();
        var inFence = false;
        for (var line : source.lines().toList()) {
            var doc = DOC_LINE.matcher(line);
            if (!doc.matches()) {
                // A fence cannot outlive its comment.
                inFence = false;
                continue;
            }
            var text = doc.group(1);
            if (FENCE.matcher(text).find()) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                continue;
            }
            text = CODE_SPAN.matcher(text).replaceAll(" ");

            var link = REFERENCE_LINK.matcher(text);
            while (link.find()) {
                var second = link.group(2);
                // `[text][ref]` names `ref`; `[ref]` and `[ref][]` name `ref` too.
                addReference(second == null || second.isBlank() ? link.group(1) : second, names);
            }
            addTagReferences(INLINE_TAG.matcher(text), names);
            addTagReferences(BLOCK_TAG.matcher(text), names);
        }
        return names;
    }

    private static void addTagReferences(Matcher tag, Set<String> names) {
        while (tag.find()) {
            addReference(tag.group(1), names);
        }
    }

    /**
     * Adds the names {@code reference} resolves through imports: its leading type
     * name and the leading name of each parameter type.
     */
    private static void addReference(String reference, Set<String> names) {
        var element = REFERENCE.matcher(reference.strip());
        if (!element.matches()) {
            return;
        }
        var type = element.group(1);
        if (type != null) {
            names.add(firstSegment(type));
        }
        var parameters = element.group(2);
        if (parameters != null) {
            for (var parameter : parameters.split(",")) {
                var identifier = IDENTIFIER.matcher(parameter);
                if (identifier.find()) {
                    names.add(identifier.group());
                }
            }
        }
    }

    /** The non-static, non-wildcard imports in {@code source}, in order. */
    private static List<String> imports(String source) {
        var names = new ArrayList<String>();
        var matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            var name = matcher.group(2);
            // A static import is not how a doc comment reaches a type, and a
            // wildcard has no simple name to be referred to by.
            if (matcher.group(1) == null && !name.endsWith(".*")) {
                names.add(name);
            }
        }
        return names;
    }

    private static String insert(String source, List<String> restored) {
        var block = new StringBuilder();
        for (var name : restored) {
            block.append("import ").append(name).append(";\n");
        }

        var lastImport = -1;
        var anyImport = Pattern.compile("(?m)^[ \\t]*import[ \\t][^\\n]*;[ \\t]*$").matcher(source);
        while (anyImport.find()) {
            lastImport = anyImport.end();
        }
        if (lastImport >= 0) {
            return source.substring(0, lastImport) + "\n" + stripTrailingNewline(block) + source.substring(lastImport);
        }

        var pkg = PACKAGE.matcher(source);
        if (pkg.find()) {
            return source.substring(0, pkg.end()) + "\n\n" + stripTrailingNewline(block) + source.substring(pkg.end());
        }
        return block + "\n" + source;
    }

    private static String stripTrailingNewline(StringBuilder block) {
        return block.substring(0, block.length() - 1);
    }

    private static String simpleName(String qualified) {
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }

    private static String firstSegment(String dotted) {
        var dot = dotted.indexOf('.');
        return dot < 0 ? dotted : dotted.substring(0, dot);
    }
}
