package rubikscubesolver;

/**
 * Core cubie-level representation of the cube.
 * Corners (indices 0..7): URF, UFL, ULB, UBR, DFR, DLF, DBL, DRB
 * Edges   (indices 0..11): UR, UF, UL, UB, DR, DF, DL, DB, FR, FL, BL, BR
 * Arrays cp/co/ep/eo are indexed by *position* and store which cubie is there
 * and with what orientation
 */
final class CubieCube {

    // Corner indices
    static final int URF = 0;
    static final int UFL = 1;
    static final int ULB = 2;
    static final int UBR = 3;
    static final int DFR = 4;
    static final int DLF = 5;
    static final int DBL = 6;
    static final int DRB = 7;

    // Edge indices
    static final int UR = 0;
    static final int UF = 1;
    static final int UL = 2;
    static final int UB = 3;
    static final int DR = 4;
    static final int DF = 5;
    static final int DL = 6;
    static final int DB = 7;
    static final int FR = 8;
    static final int FL = 9;
    static final int BL = 10;
    static final int BR = 11;

    static final int N_CORNERS = 8;
    static final int N_EDGES   = 12;


    // Arrays indexed by position -> which cubie + orientation
    final int[] cp = new int[N_CORNERS];
    final int[] co = new int[N_CORNERS];
    final int[] ep = new int[N_EDGES];
    final int[] eo = new int[N_EDGES];

    // Temporary arrays to avoid allocations inside applyFace
    private static final int[] tmpCp = new int[N_CORNERS];
    private static final int[] tmpCo = new int[N_CORNERS];
    private static final int[] tmpEp = new int[N_EDGES];
    private static final int[] tmpEo = new int[N_EDGES];

    /**
     * Corner permutations for a single clockwise face turn:
     * CORNER_MOVE[face][newPos] = oldPos.
     */
    private static final int[][] CORNER_MOVE = {
            // U
            { UBR, URF, UFL, ULB, DFR, DLF, DBL, DRB },
            // R
            { DFR, UFL, ULB, URF, DRB, DLF, DBL, UBR },
            // F
            { UFL, DLF, ULB, UBR, URF, DFR, DBL, DRB },
            // D
            { URF, UFL, ULB, UBR, DLF, DBL, DRB, DFR },
            // L
            { URF, ULB, DBL, UBR, DFR, UFL, DLF, DRB },
            // B
            { URF, UFL, UBR, DRB, DFR, DLF, ULB, DBL }
    };

    /**
     * Corner orientation deltas for a clockwise face turn
     * CORNER_ORI_DELTA[face][newPos] added (mod 3) to the old orientation
     */
    private static final int[][] CORNER_ORI_DELTA = {
            // U
            {0,0,0,0,0,0,0,0},
            // R
            {2,0,0,1,1,0,0,2},
            // F
            {1,2,0,0,2,1,0,0},
            // D
            {0,0,0,0,0,0,0,0},
            // L
            {0,1,2,0,0,2,1,0},
            // B
            {0,0,1,2,0,0,2,1}
    };

    /**
     * Edge permutations for a single clockwise face turn:
     * EDGE_MOVE[face][newPos] = oldPos
     */
    private static final int[][] EDGE_MOVE = {
            // U
            { UB, UR, UF, UL, DR, DF, DL, DB, FR, FL, BL, BR },
            // R
            { FR, UF, UL, UB, BR, DF, DL, DB, DR, FL, BL, UR },
            // F
            { UR, FL, UL, UB, DR, FR, DL, DB, UF, DF, BL, BR },
            // D
            { UR, UF, UL, UB, DF, DL, DB, DR, FR, FL, BL, BR },
            // L
            { UR, UF, BL, UB, DR, DF, FL, DB, FR, UL, DL, BR },
            // B
            { UR, UF, UL, BR, DR, DF, DL, BL, FR, FL, UB, DB }
    };

    /**
     * Edge orientation delta (0 or 1) for a clockwise turn.
     * EDGE_ORI_DELTA[face][newPos] XOR'ed with old orientation.
     */
    private static final int[][] EDGE_ORI_DELTA = {
            // U
            {0,0,0,0,0,0,0,0,0,0,0,0},
            // R
            {0,0,0,0,0,0,0,0,0,0,0,0},
            // F
            {0,1,0,0,0,1,0,0,1,1,0,0},
            // D
            {0,0,0,0,0,0,0,0,0,0,0,0},
            // L
            {0,0,0,0,0,0,0,0,0,0,0,0},
            // B
            {0,0,0,1,0,0,0,1,0,0,1,1}
    };

    // Solved cube constructor
    CubieCube() {
        for (int i = 0; i < N_CORNERS; i++) {
            cp[i] = i;
            co[i] = 0;
        }
        for (int i = 0; i < N_EDGES; i++) {
            ep[i] = i;
            eo[i] = 0;
        }
    }

    // Construct from given arrays (used internally for copies)
    CubieCube(int[] cp, int[] co, int[] ep, int[] eo) {
        if (cp.length != N_CORNERS || co.length != N_CORNERS ||
                ep.length != N_EDGES   || eo.length != N_EDGES) {
            throw new IllegalArgumentException("Wrong array sizes for CubieCube constructor");
        }
        System.arraycopy(cp, 0, this.cp, 0, N_CORNERS);
        System.arraycopy(co, 0, this.co, 0, N_CORNERS);
        System.arraycopy(ep, 0, this.ep, 0, N_EDGES);
        System.arraycopy(eo, 0, this.eo, 0, N_EDGES);
    }

    // Deep copy
    CubieCube copy() {
        return new CubieCube(cp, co, ep, eo);
    }


    /** True if this is exactly the solved cube. */
    boolean isSolved() {
        for (int i = 0; i < N_CORNERS; i++) {
            if (cp[i] != i || co[i] != 0) return false;
        }
        for (int i = 0; i < N_EDGES; i++) {
            if (ep[i] != i || eo[i] != 0) return false;
        }
        return true;
    }

    /**
     * Apply one 90° clockwise face turn:
     * face = 0..5 (U,R,F,D,L,B).
     *
     * SolverPhase wraps this with its own (U, U', U2, etc) move encoding.
     */
    void applyFace(int face) {
        // corners
        for (int i = 0; i < N_CORNERS; i++) {
            int oldPos = CORNER_MOVE[face][i];
            tmpCp[i] = cp[oldPos];

            int ori = co[oldPos] + CORNER_ORI_DELTA[face][i];
            if (ori >= 3) ori -= 3;
            tmpCo[i] = ori;
        }
        System.arraycopy(tmpCp, 0, cp, 0, N_CORNERS);
        System.arraycopy(tmpCo, 0, co, 0, N_CORNERS);

        // edges
        for (int i = 0; i < N_EDGES; i++) {
            int oldPos = EDGE_MOVE[face][i];
            tmpEp[i] = ep[oldPos];

            int ori = eo[oldPos] ^ EDGE_ORI_DELTA[face][i];
            tmpEo[i] = ori;
        }
        System.arraycopy(tmpEp, 0, ep, 0, N_EDGES);
        System.arraycopy(tmpEo, 0, eo, 0, N_EDGES);
    }
}
