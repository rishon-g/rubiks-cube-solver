"""Sticker-level cube simulator used to generate random scrambles in the solver's input format.

Kept independent of the Java solver, so a scramble produced here and verified by
VerifySolution is an end-to-end check, not the solver grading its own homework.
"""
import random

# Each sticker is (position, normal); x points right, y up, z toward the front face.
FACES = {'U': (0, 1, 0), 'D': (0, -1, 0), 'F': (0, 0, 1), 'B': (0, 0, -1), 'R': (1, 0, 0), 'L': (-1, 0, 0)}
COLORS = {'U': 'W', 'D': 'Y', 'F': 'G', 'B': 'B', 'R': 'R', 'L': 'O'}


def coord(face, r, c):
    """Position of the sticker at row r, column c of a face, as laid out in the net."""
    return {
        'U': (c - 1, 1, r - 1),
        'D': (c - 1, -1, 1 - r),
        'F': (c - 1, 1 - r, 1),
        'B': (1 - c, 1 - r, -1),
        'R': (1, 1 - r, 1 - c),
        'L': (-1, 1 - r, c - 1),
    }[face]


def solved():
    return {(coord(f, r, c), n): COLORS[f] for f, n in FACES.items() for r in range(3) for c in range(3)}


def _rotate(v, n):
    """Rotate v by 90 degrees clockwise, looking at the face whose normal is n."""
    cross = (n[1] * v[2] - n[2] * v[1], n[2] * v[0] - n[0] * v[2], n[0] * v[1] - n[1] * v[0])
    dot = sum(a * b for a, b in zip(n, v))
    return tuple(-cross[i] + n[i] * dot for i in range(3))


def turn(state, face):
    n = FACES[face]
    return {
        ((_rotate(p, n), _rotate(s, n)) if sum(a * b for a, b in zip(p, n)) == 1 else (p, s)): col
        for (p, s), col in state.items()
    }


def to_net(state):
    g = lambda f, r, c: state[(coord(f, r, c), FACES[f])]
    rows = ['   ' + ''.join(g('U', r, c) for c in range(3)) for r in range(3)]
    rows += [''.join(g(f, r, c) for f in 'LFRB' for c in range(3)) for r in range(3)]
    rows += ['   ' + ''.join(g('D', r, c) for c in range(3)) for r in range(3)]
    return '\n'.join(rows) + '\n'


def random_scramble(seed, length=25):
    """Returns (net, moves) for a random scramble with no face turned twice in a row."""
    rnd = random.Random(seed)
    state, moves, last = solved(), [], None
    while len(moves) < length:
        face = rnd.choice('URFDLB')
        if face == last:
            continue
        quarter_turns = rnd.randint(1, 3)
        for _ in range(quarter_turns):
            state = turn(state, face)
        moves.append(face + ['', '2', "'"][quarter_turns - 1])
        last = face
    return to_net(state), moves
