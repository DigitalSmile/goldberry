#!/usr/bin/env python3
"""Adds @Nullable where NullAway says a value is already null.

Driven entirely by the compiler's own diagnostics: every edit this makes is a
statement that the code *already* does what NullAway reported, so it documents
behaviour rather than changing it. Nothing is inferred and nothing is guessed --
a diagnostic this does not understand is left alone and printed.

Run through `tools/nullness/sweep.sh`, which re-compiles between passes: the
verification is that the build goes clean and the tests still pass, annotations
being compile-time only.
"""
import io, re, sys, collections

NULLABLE_IMPORT = 'import org.jspecify.annotations.Nullable;'

RETURN = re.compile(r'^(?P<file>\S+\.java):(?P<line>\d+): error: \[NullAway\] '
                    r'returning @Nullable expression from method with @NonNull return type')
FIELD_ASSIGN = re.compile(r'^(?P<file>\S+\.java):(?P<line>\d+): error: \[NullAway\] '
                          r'assigning @Nullable expression to @NonNull field')
INIT = re.compile(r"^(?P<file>\S+\.java):(?P<line>\d+): error: \[NullAway\] initializer method "
                  r"does not guarantee @NonNull fields? (?P<fields>.*?) (?:is|are) initialized")
FIELD_IN_INIT = re.compile(r"'(?P<name>[\w.]+)' \(line (?P<line>\d+)\)")
STATIC_FIELD = re.compile(r"^(?P<file>\S+\.java):(?P<line>\d+): error: \[NullAway\] "
                          r"@NonNull static field '(?P<name>\w+)' not initialized")

# A method declaration: something with a parameter list that is not a call, a
# control-flow keyword, or an annotation.
DECL = re.compile(r'^(?P<indent>\s*)(?P<mods>(?:(?:public|private|protected|static|final|'
                  r'default|abstract|synchronized|native)\s+)*)(?P<rest>[\w.<>\[\],?\s]+?\s+'
                  r'\w+\s*\()')
PRIMITIVE = re.compile(r'^\s*(?:(?:public|private|protected|static|final|transient|volatile|'
                       r'default|abstract|synchronized|native)\s+)*'
                       r'(?:int|long|double|float|boolean|char|byte|short|void)\s')

SKIP = re.compile(r'^\s*(if|for|while|switch|catch|return|throw|new|assert|else|do|yield)\b')


def read(path):
    return io.open(path, encoding='utf-8').read().split('\n')


def write(path, lines):
    io.open(path, 'w', encoding='utf-8').write('\n'.join(lines))


def ensure_import(lines):
    """Adds the JSpecify import if the file has no @Nullable yet."""
    if any(l.strip() == NULLABLE_IMPORT for l in lines):
        return lines
    last = None
    for i, l in enumerate(lines):
        if l.startswith('import '):
            last = i
    if last is None:
        for i, l in enumerate(lines):
            if l.startswith('package '):
                lines.insert(i + 1, '')
                lines.insert(i + 2, NULLABLE_IMPORT)
                return lines
        return lines
    # Keep the file's own import order: jspecify sorts under `org.`.
    at = last + 1
    for i in range(last, -1, -1):
        if lines[i].startswith('import ') and lines[i] > NULLABLE_IMPORT:
            at = i
    lines.insert(at, NULLABLE_IMPORT)
    return lines


def is_continuation(lines, index):
    """Whether `index` continues a declaration begun on an earlier line.

    A wrapped declaration puts the type on one line and the name on the next, and
    annotating the second half produces `Map<...> @Nullable name;`, which does not
    parse. The test is what came before: a line that ends mid-declaration.
    """
    for i in range(index - 1, -1, -1):
        prev = lines[i].strip()
        if not prev or prev.startswith(('//', '///', '*', '/*')):
            continue
        return not prev.endswith((';', '{', '}', ')', ':')) and not prev.startswith('@')
    return False


def annotate_at(lines, index, what):
    """Puts @Nullable on the declaration at `index`, before its type."""
    line = lines[index]
    if '@Nullable' in line:
        return False
    if is_continuation(lines, index):
        return False
    # A primitive cannot be null, so `@Nullable int` is not a weaker contract --
    # it is a sign the backward scan found the wrong declaration. Error Prone's
    # NullablePrimitive says so too, which is how this was caught.
    if PRIMITIVE.search(line):
        return False
    # A declaration names a **type** and then an identifier. `held = null;` names
    # only an identifier, and annotating it produced `@Nullable held = null;`,
    # which does not parse. The test is that something precedes the name.
    stripped = re.sub(r'^(?:(?:public|private|protected|static|final|transient|volatile|'
                      r'default|abstract|synchronized|native)\s+)*', '', line.strip())
    # A type starts with a letter. Without that anchor the `?` of a wrapped
    # ternary matched as one, and `@Nullable ? readableTime(...)` is not an
    # expression.
    if stripped[:1] in '?:.,)+-*/&|=<>!':
        return False
    if not re.match(r'^[A-Za-z_][\w.<>\[\],?]*(?:\s*<[^;=]*>)?(?:\s*\[\])*\s+\w+\s*[=;(]',
                    stripped):
        return False
    m = re.match(r'^(?P<indent>\s*)(?P<mods>(?:(?:public|private|protected|static|final|'
                 r'default|abstract|synchronized|native|transient|volatile)\s+)*)(?P<tail>.*)$',
                 line)
    if not m or not m.group('tail').strip():
        return False
    lines[index] = m.group('indent') + m.group('mods') + qualify(m.group('tail'))
    return True


def qualify(tail):
    """Places @Nullable where a TYPE_USE annotation is legal.

    JSpecify's @Nullable targets TYPE_USE, so on a fully-qualified type it binds
    to the last segment and has to be written there: `java.util.@Nullable Map`,
    never `@Nullable java.util.Map`. javac rejects the second outright with
    "type annotation not expected here", which is how this was found.
    """
    # An array: the annotation goes on the *array*, not on its element type, so
    # it sits between them -- `double @Nullable []`. Written the other way it
    # annotates `double`, which cannot be null and which Error Prone rejects as
    # NullablePrimitiveArray.
    arr = re.match(r'^(?P<elem>[\w.<>,?\s]+?)\s*\[\](?P<rest>\s.*)$', tail)
    if arr:
        return arr.group('elem') + ' @Nullable []' + arr.group('rest')

    m = re.match(r'^(?P<type>[\w.]+(?:\.[A-Z]\w*)*)(?P<rest>[\s<\[].*)$', tail)
    if not m or '.' not in m.group('type'):
        return '@Nullable ' + tail
    parts = m.group('type').split('.')
    # The **last** segment, always. It is the innermost type, and that is what a
    # TYPE_USE annotation binds to: `java.util.@Nullable Map`, and for a nested
    # type `...event.PointerEvent.@Nullable Button`. Annotating the first
    # capitalised segment is right for the first and wrong for the second, which
    # javac rejects in the same words.
    return '.'.join(parts[:-1]) + '.@Nullable ' + parts[-1] + m.group('rest')


def enclosing_declaration(lines, index):
    """The method declaration a statement at `index` belongs to."""
    for i in range(index, -1, -1):
        line = lines[i]
        if SKIP.match(line) or not line.strip() or line.strip().startswith(('//', '///', '*')):
            continue
        stripped = line.strip()
        # A signature ends where its body or its continuation begins -- never in a
        # semicolon, which is what a *call* ends in. `collectFocusable(a, b);`
        # matched the declaration shape until this ruled it out.
        if stripped.endswith(';'):
            continue
        if DECL.match(line) and '=' not in line.split('(')[0]:
            return i
    return None


def field_declaration(lines, name):
    """The declaration line of field `name`."""
    pat = re.compile(r'^\s*(?:(?:public|private|protected|static|final|transient|volatile)\s+)*'
                     r'[\w.<>\[\],?\s]+\s+' + re.escape(name) + r'\s*(?:=|;)')
    for i, l in enumerate(lines):
        if pat.match(l):
            return i
    return None


def main(diagnostics):
    text = io.open(diagnostics, encoding='utf-8').read()
    edits = collections.defaultdict(set)   # file -> {line index}
    unknown = []

    for raw in text.split('\n'):
        line = raw.strip()
        for pattern, kind in ((RETURN, 'return'), (FIELD_ASSIGN, 'assign'),
                              (INIT, 'init'), (STATIC_FIELD, 'static')):
            m = pattern.match(line)
            if not m:
                continue
            path = m.group('file')
            if kind == 'init':
                for f in FIELD_IN_INIT.finditer(m.group('fields')):
                    edits[path].add(('field-line', int(f.group('line')) - 1))
            elif kind == 'static':
                edits[path].add(('field-name', m.group('name')))
            elif kind == 'return':
                edits[path].add(('return', int(m.group('line')) - 1))
            else:
                edits[path].add(('assign', int(m.group('line')) - 1))
            break
        else:
            if '[NullAway]' in line and 'error:' in line:
                unknown.append(line)

    changed = applied = 0
    for path, items in sorted(edits.items()):
        try:
            lines = read(path)
        except OSError:
            continue
        targets = set()
        for kind, value in items:
            if kind == 'field-line':
                targets.add(value)
            elif kind == 'field-name':
                i = field_declaration(lines, value)
                if i is not None:
                    targets.add(i)
            elif kind == 'return':
                i = enclosing_declaration(lines, value)
                if i is not None:
                    targets.add(i)
            elif kind == 'assign':
                m = re.match(r'\s*(?:this\.)?(\w+)\s*=', lines[value])
                if m:
                    i = field_declaration(lines, m.group(1))
                    if i is not None:
                        targets.add(i)
        # Descending, so an insertion never moves a target below it.
        hits = 0
        for i in sorted(targets, reverse=True):
            if annotate_at(lines, i, path):
                hits += 1
        if hits:
            ensure_import(lines)
            write(path, lines)
            changed += 1
            applied += hits

    print(f'annotated {applied} declarations in {changed} files')
    if unknown:
        print(f'{len(unknown)} diagnostics not understood:')
        for u in unknown[:8]:
            print('   ', u[:150])
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
