"""Solves N random scrambles, verifies each solution, and reports timing and move counts.

Usage (from the repository root, after `mvn clean compile`):
    python3 bench/benchmark.py [count] [scramble_length]
"""
import itertools
import os
import statistics
import subprocess
import sys
import tempfile
import time

sys.path.insert(0, os.path.dirname(__file__))
from cube import random_scramble

CLASSES = 'target/classes'


def face_turns(solution):
    """Counts face turns: "UUU" is one move (U'), "UU" is one move (U2)."""
    return sum(1 for _, run in itertools.groupby(solution) if len(list(run)) % 4)


def main():
    count = int(sys.argv[1]) if len(sys.argv) > 1 else 30
    length = int(sys.argv[2]) if len(sys.argv) > 2 else 25
    times, lengths, failures = [], [], 0
    with tempfile.TemporaryDirectory() as tmp:
        for seed in range(1, count + 1):
            scramble, out = os.path.join(tmp, 'in.txt'), os.path.join(tmp, 'out.txt')
            with open(scramble, 'w') as f:
                f.write(random_scramble(seed, length)[0])
            start = time.perf_counter()
            subprocess.run(['java', '-cp', CLASSES, 'rubikscubesolver.Solver', scramble, out], check=True)
            times.append(time.perf_counter() - start)
            verdict = subprocess.run(['java', '-cp', CLASSES, 'rubikscubesolver.VerifySolution', scramble, out],
                                     capture_output=True, text=True).stdout
            solution = open(out).read().strip()
            ok = 'Solution works' in verdict
            failures += not ok
            lengths.append(face_turns(solution))
            print(f'#{seed:<3} {times[-1]:.2f}s  {lengths[-1]:>2} moves  {"verified" if ok else "FAILED"}')

    print(f'\nSolved and verified: {count - failures}/{count}')
    print(f'Wall time per solve (JVM start + table build + search): '
          f'median {statistics.median(times):.2f}s, max {max(times):.2f}s')
    print(f'Solution length: max {max(lengths)} face turns')


if __name__ == '__main__':
    main()
