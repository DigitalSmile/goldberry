#!/usr/bin/env python3
"""Marks the packages of a module @NullMarked, and keeps only the ones that pass.

NullAway's findings fall into two kinds. Some are mechanical -- a method already
returns null, so its return type says @Nullable -- and `annotate.py` applies
those from the compiler's own diagnostics. The rest are behavioural: a
dereference that needs a real null check, a parameter contract that has to be
decided. Those need a person, and a package that has one is left unmarked until
somebody does the deciding.

So: mark everything, annotate what is mechanical, then unmark whatever still has
a finding. What is left marked is checked from now on, and what is not is exactly
the work remaining.
"""
import os, io, re, subprocess, sys, collections

NOTE = """/// `@NullMarked`, which puts this package under NullAway.
///
/// Inside a marked package every type is non-null unless it says `@Nullable`,
/// and the build fails on a violation. The annotation is the whole content of
/// this file: there is nothing package-specific to say about nullness, and a
/// paragraph pretending otherwise in every package would be padding.
///
/// Packages are marked one at a time on purpose. NullAway runs in
/// `OnlyNullMarked` mode, so an unmarked package is invisible to it and a marked
/// one is checked from the moment it opts in — which is the only way a codebase
/// this size adopts nullness at all (`docs/testing.md` §2).
@NullMarked
package %s;

import org.jspecify.annotations.NullMarked;
"""


def packages(module):
    root = f'{module}/src/main/java'
    for dirpath, _, files in os.walk(root):
        if any(f.endswith('.java') and f != 'module-info.java' for f in files):
            yield dirpath, os.path.relpath(dirpath, root).replace(os.sep, '.')


def mark_all(module):
    n = 0
    for path, pkg in packages(module):
        pi = os.path.join(path, 'package-info.java')
        if not os.path.exists(pi):
            io.open(pi, 'w', encoding='utf-8').write(NOTE % pkg)
            n += 1
    return n


def compile_module(module):
    r = subprocess.run(['./gradlew', f':{module}:compileJava',
                        '-Pgoldberry.lenient', '--no-daemon'],
                       capture_output=True, text=True)
    return r.stdout + r.stderr


def failing_packages(output, module):
    root = os.path.abspath(f'{module}/src/main/java')
    bad = collections.Counter()
    for line in output.split('\n'):
        m = re.match(r'^(\S+\.java):\d+: error: \[NullAway\]', line.strip())
        if not m:
            continue
        p = os.path.abspath(m.group(1))
        if p.startswith(root):
            bad[os.path.dirname(p)] += 1
    return bad


def main(module, passes=4):
    made = mark_all(module)
    print(f'{module}: marked {made} packages')

    for i in range(passes):
        out = compile_module(module)
        log = f'/tmp/nullaway-{module}.log'
        io.open(log, 'w', encoding='utf-8').write(out)
        if 'error: [NullAway]' not in out:
            break
        before = subprocess.run(['git', 'diff', '--stat', '--', module],
                                capture_output=True, text=True).stdout
        subprocess.run([sys.executable, 'tools/nullness/annotate.py', log],
                       capture_output=True, text=True)
        after = subprocess.run(['git', 'diff', '--stat', '--', module],
                               capture_output=True, text=True).stdout
        if before == after:
            break

    out = compile_module(module)
    bad = failing_packages(out, module)
    for path, count in sorted(bad.items()):
        pi = os.path.join(path, 'package-info.java')
        if os.path.exists(pi):
            os.remove(pi)
    kept = sum(1 for _ in packages(module)) - len(bad)
    print(f'{module}: {kept} packages marked and clean, {len(bad)} left for a person')
    for path, count in sorted(bad.items(), key=lambda kv: -kv[1])[:10]:
        print(f'    {count:3d}  {os.path.relpath(path, module + "/src/main/java")}')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 4))
