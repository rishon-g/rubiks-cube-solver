package org.example;

import java.util.Arrays;

/**
 * Facelet-level view used only for parsing and converting to a CubieCube.
 * Internal array f[] is in Kociemba ordering: U(0..8), R(9..17), F(18..26), D(27..35), L(36..44), B(45..53)
 */
final class FaceCube {

    final char[] f = new char[54];

    /**
     *
     *   UUU
     *   UUU
     *   UUU
     * LLLFFF RRRBBB (12 chars per middle row)
     * ...
     *   DDD
     *   DDD
     *   DDD
     *
     *  54 char array with indexes:
     *   0-8   : U
     *   9-17  : L
     *   18-26 : F
     *   27-35 : R
     *   36-44 : B
     *   45-53 : D
     */
    FaceCube(char[] netState) {
        if (netState.length != 54) {
            throw new IllegalArgumentException("netState must have length 54");
        }
        // U stays U
        System.arraycopy(netState, 0, f, 0, 9);
        // R comes from net's R
        System.arraycopy(netState, 27, f, 9, 9);
        // F from net's F
        System.arraycopy(netState, 18, f, 18, 9);
        // D from net's D
        System.arraycopy(netState, 45, f, 27, 9);
        // L from net's L
        System.arraycopy(netState, 9, f, 36, 9);
        // B from net's B
        System.arraycopy(netState, 36, f, 45, 9);
    }

    /**
     * Convert this facelet cube to a CubieCube.
     * Uses standard URFDLB corner/edge definitions and the center colors.
     */
    CubieCube toCubieCube() {
        CubieCube cube = new CubieCube();

        // Map actual chars to  color indices: U,R,F,D,L,B -> 0..5
        int[] colMap = new int[256];
        Arrays.fill(colMap, -1);

        // centers in K-order
        char[] center = {
                f[4],   // U
                f[13],  // R
                f[22],  // F
                f[31],  // D
                f[40],  // L
                f[49]   // B
        };
        for (int c = 0; c < 6; c++) {
            colMap[ center[c] ] = c;
        }

        // crorners
        for (int pos = 0; pos < 8; pos++) {
            boolean found = false;
            for (int cubie = 0; cubie < 8 && !found; cubie++) {
                int[] expected = CORNER_COLORS[cubie];
                // try all three orientations
                for (int ori = 0; ori < 3 && !found; ori++) {
                    int c0 = colMap[ f[ CORNER_FACELET[pos][ori] ] ];
                    int c1 = colMap[ f[ CORNER_FACELET[pos][(ori + 1) % 3] ] ];
                    int c2 = colMap[ f[ CORNER_FACELET[pos][(ori + 2) % 3] ] ];

                    if (c0 == expected[0] && c1 == expected[1] && c2 == expected[2]) {
                        cube.cp[pos] = cubie;
                        cube.co[pos] = ori;
                        found = true;
                    }
                }
            }
            if (!found) {
                throw new IllegalStateException("Invalid corner at position " + pos);
            }
        }

        // edges
        for (int pos = 0; pos < 12; pos++) {
            boolean found = false;
            for (int cubie = 0; cubie < 12 && !found; cubie++) {
                int[] expected = EDGE_COLORS[cubie];

                // two orientations
                for (int ori = 0; ori < 2 && !found; ori++) {
                    int c0 = colMap[ f[ EDGE_FACELET[pos][ori]     ] ];
                    int c1 = colMap[ f[ EDGE_FACELET[pos][1 - ori] ] ];

                    if (c0 == expected[0] && c1 == expected[1]) {
                        cube.ep[pos] = cubie;
                        cube.eo[pos] = ori;
                        found = true;
                    }
                }
            }
            if (!found) {
                throw new IllegalStateException("Invalid edge at position " + pos);
            }
        }

        return cube;
    }


    // Geometry tables (indices into f[])
    // Facelet index mapping:
    // U1..U9:  0..8
    // R1..R9:  9..17
    // F1..F9:  18..26
    // D1..D9:  27..35
    // L1..L9:  36..44
    // B1..B9:  45..53

    // Corner positions: URF, UFL, ULB, UBR, DFR, DLF, DBL, DRB
    private static final int[][] CORNER_FACELET = {
            // URF: U9, R1, F3
            { 8,  9, 20 },
            // UFL: U7, F1, L3
            { 6, 18, 38 },
            // ULB: U1, L1, B3
            { 0, 36, 47 },
            // UBR: U3, B1, R3
            { 2, 45, 11 },
            // DFR: D3, F9, R7
            { 29, 26, 15 },
            // DLF: D1, L9, F7
            { 27, 44, 24 },
            // DBL: D7, B9, L7
            { 33, 53, 42 },
            // DRB: D9, R9, B7
            { 35, 17, 51 }
    };

    // Abstract colors for each corner cubie in its "solved" orientation.
    // Color indices: 0=U,1=R,2=F,3=D,4=L,5=B
    private static final int[][] CORNER_COLORS = {
            {0,1,2}, // URF
            {0,2,4}, // UFL
            {0,4,5}, // ULB
            {0,5,1}, // UBR
            {3,2,1}, // DFR
            {3,4,2}, // DLF
            {3,5,4}, // DBL
            {3,1,5}  // DRB
    };

    // Edge positions: UR,UF,UL,UB,DR,DF,DL,DB,FR,FL,BL,BR
    private static final int[][] EDGE_FACELET = {
            { 5, 10 }, // UR: U6,R2
            { 7, 19 }, // UF: U8,F2
            { 3, 37 }, // UL: U4,L2
            { 1, 46 }, // UB: U2,B2
            { 32, 16 }, // DR: D6,R8
            { 28, 25 }, // DF: D2,F8
            { 30, 43 }, // DL: D4,L8
            { 34, 52 }, // DB: D8,B8
            { 23, 12 }, // FR: F6,R4
            { 21, 41 }, // FL: F4,L6
            { 50, 39 }, // BL: B6,L4
            { 48, 14 }  // BR: B4,R6  <-- FIXED (was 47,14)
    };

    // Abstract colors for each edge cubie in solved state.
    // Color indices as above.
    private static final int[][] EDGE_COLORS = {
            {0,1}, // UR
            {0,2}, // UF
            {0,4}, // UL
            {0,5}, // UB
            {3,1}, // DR
            {3,2}, // DF
            {3,4}, // DL
            {3,5}, // DB
            {2,1}, // FR
            {2,4}, // FL
            {5,4}, // BL
            {5,1}  // BR
    };
}
