#!/usr/bin/env python3
"""Unmarks any package that still has a NullAway finding.

Run after `annotate.py` has done what it can: what remains is behavioural -- a
dereference that needs a real null check, a parameter contract to decide -- and
a package with one of those stays unmarked until a person does the deciding.
"""
import os, re, subprocess, sys, collections

def main(module):
    r = subprocess.run(['./gradlew', f':{module}:compileJava', '-Pgoldberry.lenient',
                        '--no-daemon'], capture_output=True, text=True)
    out = r.stdout + r.stderr
    root = os.path.abspath(f'{module}/src/main/java')
    bad = collections.Counter()
    for line in out.split('\n'):
        m = re.match(r'^(\S+\.java):\d+: error: \[NullAway\]', line.strip())
        if m:
            p = os.path.abspath(m.group(1))
            if p.startswith(root):
                bad[os.path.dirname(p)] += 1
    for path in bad:
        pi = os.path.join(path, 'package-info.java')
        if os.path.exists(pi):
            os.remove(pi)
    print(f'{module}: unmarked {len(bad)} more packages')
    return 1 if bad else 0

if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
