#!/usr/bin/env python3
"""Annotates the parameter a null was passed to.

NullAway reports "passing @Nullable parameter 'X' where @NonNull is required" at
the *call*, not at the declaration -- so this finds the call, works out which
argument X is, resolves the callee, and annotates that parameter.

Conservative by construction: it acts only when the callee's name resolves to
exactly one declaration with the right arity, in this file or in the module. A
name that resolves to two is left alone and reported, because guessing which one
was meant is how a tool corrupts a codebase quietly.
"""
import io, os, re, sys, collections

CALL = re.compile(r"^(?P<file>\S+\.java):(?P<line>\d+): error: \[NullAway\] passing "
                  r"@Nullable parameter '(?P<arg>.*)' where @NonNull is required")

DECL = re.compile(r'^(?P<head>\s*(?:(?:public|private|protected|static|final|default|abstract|'
                  r'synchronized|native)\s+)*(?:<[^>]+>\s*)?[\w.<>\[\],?\s]*?\b(?P<name>\w+)\s*)'
                  r'\((?P<params>[^;]*)$')


# A declaration is not a call. `return new PointerEvent(...)` matches the shape
# below on its name, and annotating "position 3" of it produced `@Nullable null`
# as an argument.
NOT_A_DECL = re.compile(r'^\s*(?:return|throw|yield|this|super|=|\|\||&&)\b'
                        r'|\bnew\s+\w+\s*\(|;\s*$')


def qualify(param):
    """Puts @Nullable where a TYPE_USE annotation is legal on a parameter."""
    p = param.strip()
    # Varargs: the annotation belongs to the array the ellipsis makes, so it goes
    # between the type and the dots. The dots also look like a qualified name to
    # the pattern below, which turned `Widget... extra` into `Widget...@Nullable
    # extra` -- three dots and no identifier.
    v = re.match(r'^(?P<type>[\w.<>\[\],?]+)\s*\.\.\.(?P<rest>\s.*)$', p)
    if v:
        return v.group('type') + ' @Nullable ...' + v.group('rest')
    m = re.match(r'^(?P<type>[\w.]+)(?P<rest>\s.*)$', p)
    if not m or '.' not in m.group('type'):
        return '@Nullable ' + param.strip()
    parts = m.group('type').split('.')
    return '.'.join(parts[:-1]) + '.@Nullable ' + parts[-1] + m.group('rest')


# Placement is the same problem `annotate.py` solves for declarations; the two
# copies exist because the tools run independently and a shared module would be a
# third file for eleven lines.


def split_args(text):
    """Top-level commas only: `f(a, g(b, c), null)` is three arguments."""
    out, depth, cur = [], 0, ''
    for ch in text:
        if ch in '([<':
            depth += 1
        elif ch in ')]>':
            depth -= 1
        if ch == ',' and depth == 0:
            out.append(cur.strip()); cur = ''
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


def call_at(lines, index, arg):
    """(callee, argument index) for the call on `index` passing `arg`."""
    text = lines[index]
    # A call may be split over lines; join forward until the parens balance.
    for extra in range(1, 4):
        if text.count('(') <= text.count(')'):
            break
        if index + extra < len(lines):
            text += ' ' + lines[index + extra].strip()
    for m in re.finditer(r'(?:new\s+)?(\w+)\s*\(', text):
        name = m.group(1)
        # `this(...)` and `super(...)` delegate to a constructor. The delegation
        # line matches the declaration shape on its own name, so the tool found
        # itself and annotated the *arguments* -- `this(roots, @Nullable selected
        # == null ? ...)`. Which constructor it means needs overload resolution,
        # so it is left to a person.
        if name in ('this', 'super'):
            continue
        start = m.end()
        depth, end = 1, start
        while end < len(text) and depth:
            if text[end] in '(':
                depth += 1
            elif text[end] == ')':
                depth -= 1
            end += 1
        args = split_args(text[start:end - 1])
        for i, a in enumerate(args):
            if a == arg:
                return name, i, len(args)
    return None, None, None


def looks_declared(param):
    """Whether `param` reads as `Type name` rather than as an expression."""
    p = param.strip()
    if not p:
        return False
    if '(' in p or '::' in p or '"' in p or p.startswith('@') and ' ' not in p:
        return False
    p = re.sub(r'^(?:final\s+|@\w+(?:\([^)]*\))?\s+)*', '', p)
    return bool(re.match(r'^[\w.<>\[\],?\s]+\s+\w+(?:\.\.\.)?$', p)) and ' ' in p


def declarations(paths, name, arity):
    """Every declaration of `name` taking `arity` parameters."""
    found = []
    for p in paths:
        lines = io.open(p, encoding='utf-8').read().split('\n')
        for i, line in enumerate(lines):
            m = DECL.match(line)
            if not m or m.group('name') != name or NOT_A_DECL.search(line):
                continue
            text = line[line.index('(', m.end('head') - 1):]
            for extra in range(1, 6):
                if text.count('(') <= text.count(')'):
                    break
                if i + extra < len(lines):
                    text += ' ' + lines[i + extra].strip()
            inner = text[1:text.rfind(')')] if ')' in text else ''
            params = split_args(inner)
            # A declaration's parameters are `Type name`; a call's arguments are
            # expressions. `ChartParts.of(chart.series(), ...)` matched the shape
            # on its name and arity, and the tool annotated an argument.
            if len(params) == arity and all(looks_declared(x) for x in params):
                found.append((p, i, params))
    return found


def annotate_param(path, decl_line, position):
    lines = io.open(path, encoding='utf-8').read().split('\n')
    text, span = lines[decl_line], 1
    while text.count('(') > text.count(')') and decl_line + span < len(lines):
        text += '\n' + lines[decl_line + span]; span += 1
    open_at = text.index('(')
    close_at = text.rindex(')')
    params = split_args(text[open_at + 1:close_at])
    if position >= len(params) or '@Nullable' in params[position]:
        return False
    params[position] = qualify(params[position])
    joined = text[:open_at + 1] + ', '.join(params) + text[close_at:]
    new = joined.split('\n')
    lines[decl_line:decl_line + span] = new
    s = '\n'.join(lines)
    if 'import org.jspecify.annotations.Nullable;' not in s:
        if '\nimport ' in s:
            s = s.replace('\nimport ', '\nimport org.jspecify.annotations.Nullable;\nimport ', 1)
        else:
            # A file with no imports at all -- FocusScope is one -- had nothing
            # for the replacement above to find, so the annotation went in
            # without its import and the compiler said "cannot find symbol".
            s = re.sub(r'^(package [^;]+;\n)', r'\1\nimport org.jspecify.annotations.Nullable;\n',
                       s, count=1, flags=re.M)
    io.open(path, 'w', encoding='utf-8').write(s)
    return True


def main(log, module):
    roots = [os.path.join(dp, f)
             for dp, _, fs in os.walk(f'{module}/src/main/java')
             for f in fs if f.endswith('.java')]
    done, skipped = 0, collections.Counter()
    seen = set()
    for raw in io.open(log, encoding='utf-8'):
        m = CALL.match(raw.strip())
        if not m:
            continue
        path, line, arg = m.group('file'), int(m.group('line')) - 1, m.group('arg')
        if not os.path.exists(path):
            continue
        lines = io.open(path, encoding='utf-8').read().split('\n')
        name, pos, arity = call_at(lines, line, arg)
        if name is None:
            skipped['call not found'] += 1
            continue
        # This file first: a private helper is the common case and a name that is
        # ambiguous across the module is usually unambiguous here.
        cands = declarations([path], name, arity) or declarations(roots, name, arity)
        if len(cands) != 1:
            skipped['%d declarations' % len(cands)] += 1
            continue
        dp, dl, _ = cands[0]
        key = (dp, dl, pos)
        if key in seen:
            continue
        seen.add(key)
        if annotate_param(dp, dl, pos):
            done += 1
    print(f'annotated {done} parameters')
    for k, v in skipped.most_common():
        print(f'  skipped {v}: {k}')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1], sys.argv[2]))
