package org.example;

import java.io.*;
import java.util.*;

/**
 * Two-phase Rubik's Cube solver.
 *
 * Phase 1 goal: All corner orientations solved, all edge orientations solved.
 * the 4 slice edges {FR, FL, BL, BR} are in the middle layer
 *
 * Phase 2 goal: Reach the fully solved state while preserving Phase 1 constraints
 */
public class Solver {

    // Move encoding
    // 0:U, 1:U', 2:U2,
    // 3:R, 4:R', 5:R2,
    // 6:F, 7:F', 8:F2,
    // 9:D,10:D',11:D2,
    // 12:L,13:L',14:L2,
    // 15:B,16:B',17:B2
    private static final String[] MOVE_NAMES = {
            "U","U'","U2",
            "R","R'","R2",
            "F","F'","F2",
            "D","D'","D2",
            "L","L'","L2",
            "B","B'","B2"
    };

    // face index for each move (0=U,1=R,2=F,3=D,4=L,5=B)
    private static final int[] MOVE_FACE = {
            0,0,0,
            1,1,1,
            2,2,2,
            3,3,3,
            4,4,4,
            5,5,5
    };

    // axis index (same as face, used for pruning same-axis sequences)
    private static final int[] MOVE_AXIS = MOVE_FACE;
    private static final int N_MOVES_PHASE1 = 18;

    // Phase-2 allowed moves (indices into 0..17):
    // U, U', U2, D, D', D2, R2, L2, F2, B2
    private static final int[] PHASE2_MOVES = {
            0,1,2,    // U, U', U2
            9,10,11,  // D, D', D2
            5,        // R2
            14,       // L2
            8,        // F2
            17        // B2
    };
    private static final int N_MOVES_PHASE2 = PHASE2_MOVES.length;

    // Limits
    private static final int MAX_TOTAL_DEPTH = 30;              // safety cap
    private static final long TIME_LIMIT_NS = 20_000_000_000L; // 20 seconds

    private static long deadlineNs;
    private static boolean timedOut;

    // Combinations C(n,k) for slice indexing
    private static final int[][] C = new int[13][5];
    static {
        for (int n = 0; n <= 12; n++) {
            C[n][0] = 1;
            for (int k = 1; k <= Math.min(4, n); k++) {
                C[n][k] = C[n - 1][k - 1] + C[n - 1][k];
            }
        }
    }

    // Factorials for permutation indices
    private static final int[] FACT = {1,1,2,6,24,120,720,5040}; // 0!..7!
    private static final int[] FACT4 = {1,1,2,6,24};              // 0!..4!

    // Slice edges & UD edges (use CubieCube edge indices)
    private static final int E_FR = CubieCube.FR;
    private static final int E_FL = CubieCube.FL;
    private static final int E_BL = CubieCube.BL;
    private static final int E_BR = CubieCube.BR;

    private static final int[] SLICE_EDGES = { E_FR, E_FL, E_BL, E_BR };
    private static final int[] NON_SLICE_EDGES = {
            CubieCube.UR, CubieCube.UF, CubieCube.UL, CubieCube.UB,
            CubieCube.DR, CubieCube.DF, CubieCube.DL, CubieCube.DB
    };

    // Phase 1 tables: orientations + slice membership
    private static final int N_CO = 2187; // 3^7
    private static final int N_EO = 2048; // 2^11
    private static final int N_SLICE = 495;  // C(12,4)

    private static int[][] coMove = new int[N_CO][N_MOVES_PHASE1];
    private static int[][] eoMove = new int[N_EO][N_MOVES_PHASE1];
    private static int[][] sliceMove = new int[N_SLICE][N_MOVES_PHASE1];

    private static byte[] pdbP1CoSlice = new byte[N_CO * N_SLICE];
    private static byte[] pdbP1EoSlice = new byte[N_EO * N_SLICE];

    // Phase 2 tables: permutations + slice permutation
    private static final int N_CP = 40320; // 8!
    private static final int N_UD = 40320; // 8!
    private static final int N_SP = 24;    // 4!

    private static int[][] cpMove2 = new int[N_CP][N_MOVES_PHASE2];
    private static int[][] udEdgeMove2 = new int[N_UD][N_MOVES_PHASE2];
    private static int[][] slicePermMove2 = new int[N_SP][N_MOVES_PHASE2];

    private static byte[] pdbP2Corners = new byte[N_CP * N_SP];
    private static byte[] pdbP2Edges = new byte[N_UD * N_SP];

    private static boolean tablesInitialized = false;

    // MAIN
    public static void main(String[] args) {
        /* if (args.length < 2) {
            System.out.println("Usage: java rubikscube.Solver2Phase <input_file> <output_file>");
            return;
        } */

        String inputFile  = args[0];
        String outputFile = args[1];

        long wallStart = System.currentTimeMillis();

        try {
            // System.out.println("Reading input from: " + inputFile);
            char[] net = parseInputNet(inputFile);

            FaceCube fc = new FaceCube(net);
            CubieCube start = fc.toCubieCube();

            // System.out.println("Building move tables and pattern databases");
            initTables();

            // System.out.println("\nSolving...");
            deadlineNs = System.nanoTime() + TIME_LIMIT_NS;
            timedOut = false;

            List<Integer> solutionMoves = solvePhase(start);

            long duration = System.currentTimeMillis() - wallStart;
            double seconds = duration / 1000.0;

            List<String> finalOutput = convertSolutionToOutput(solutionMoves);
            writeOutput(outputFile, finalOutput);

            if (solutionMoves != null) {
                /* System.out.println("\n");
                System.out.println("Solution found!");
                System.out.println("Internal moves: " + solutionMoves.size());
                System.out.println("Output letters: " + finalOutput.size());
                System.out.printf("Time taken: %.3f seconds%n", seconds);
                System.out.println("\n"); */
            } else {
                if (timedOut) {
                    System.out.println("No solution within time limit.");
                } else {
                    System.out.println("No solution within depth/search limits.");
                }
                // System.out.printf("Time elapsed: %.3f seconds%n", seconds);
            }

        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        } catch (RuntimeException re) {
            System.err.println("Runtime error: " + re.getMessage());
            re.printStackTrace();
        }
    }

    // Two-phase search
    private static List<Integer> solvePhase(CubieCube start) {
        // Phase-1 iterative deepening over depth1 = 0..MAX_TOTAL_DEPTH
        for (int depth1Limit = 0; depth1Limit <= MAX_TOTAL_DEPTH; depth1Limit++) {
            // System.out.println("Phase 1 depth limit = " + depth1Limit);

            List<Integer> path1 = new ArrayList<>();
            CubieCube work = start.copy();
            List<Integer> fullSolution = new ArrayList<>();

            if (searchPhase1(work, 0, depth1Limit, -1, -1, path1, fullSolution)) {
                return fullSolution;
            }

            if (timedOut) return null;
        }
        return null;
    }

    // Phase-1 DFS with heuristic pruning.
    private static boolean searchPhase1(CubieCube cube,
                                        int depth,
                                        int depthLimit,
                                        int lastMove,
                                        int lastAxis,
                                        List<Integer> path1,
                                        List<Integer> fullSolution) {

        if (System.nanoTime() > deadlineNs) {
            timedOut = true;
            return false;
        }

        int h1 = heuristicPhase1(cube);
        if (depth + h1 > depthLimit || depth + h1 > MAX_TOTAL_DEPTH) {
            return false;
        }

        if (isPhase1Goal(cube)) {
            int remainingDepth = MAX_TOTAL_DEPTH - depth;

            List<Integer> path2 = new ArrayList<>();
            CubieCube phase2Start = cube.copy();
            if (searchPhase2(phase2Start, 0, remainingDepth, -1, -1, path2)) {
                fullSolution.addAll(path1);
                fullSolution.addAll(path2);
                return true;
            }
        }

        if (depth == depthLimit) {
            return false;
        }

        for (int move = 0; move < N_MOVES_PHASE1; move++) {
            int axis = MOVE_AXIS[move];

            if (lastMove != -1) {
                // no back-to-back same-axis moves (e.g., U then U2)
                if (axis == lastAxis) continue;
            }

            applyMove18(cube, move);
            path1.add(move);

            if (searchPhase1(cube, depth + 1, depthLimit, move, axis, path1, fullSolution)) {
                return true;
            }

            path1.remove(path1.size() - 1);
            applyMove18(cube, inverseMove(move));
        }

        return false;
    }


    // Phase-2 DFS restricted to PHASE2_MOVES.
    private static boolean searchPhase2(CubieCube cube,
                                        int depth,
                                        int depthLimit,
                                        int lastMove,
                                        int lastAxis,
                                        List<Integer> path2) {

        if (System.nanoTime() > deadlineNs) {
            timedOut = true;
            return false;
        }

        int h2 = heuristicPhase2(cube);
        if (depth + h2 > depthLimit) {
            return false;
        }

        if (cube.isSolved()) {
            return true;
        }

        if (depth == depthLimit) {
            return false;
        }

        for (int i = 0; i < N_MOVES_PHASE2; i++) {
            int move = PHASE2_MOVES[i];
            int axis = MOVE_AXIS[move];

            if (lastMove != -1) {
                if (axis == lastAxis) continue;
            }

            applyMove18(cube, move);
            path2.add(move);

            if (searchPhase2(cube, depth + 1, depthLimit, move, axis, path2)) {
                return true;
            }

            path2.remove(path2.size() - 1);
            applyMove18(cube, inverseMove(move));
        }

        return false;
    }


    // Phase 1 goal + heuristic
    private static boolean isPhase1Goal(CubieCube c) {
        // orientations solved
        for (int i = 0; i < CubieCube.N_CORNERS; i++) {
            if (c.co[i] != 0) return false;
        }
        for (int i = 0; i < CubieCube.N_EDGES; i++) {
            if (c.eo[i] != 0) return false;
        }

        // slice edges FR, FL, BL, BR must be in positions 8..11
        for (int pos = 0; pos < 12; pos++) {
            int e = c.ep[pos];
            boolean isSlice = (e == E_FR || e == E_FL || e == E_BL || e == E_BR);
            if (pos < 8) {
                if (isSlice) return false;
            } else {
                if (!isSlice) return false;
            }
        }
        return true;
    }

    private static int heuristicPhase1(CubieCube c) {
        int co = cornerOriIndex(c);
        int eo = edgeOriIndex(c);
        int sl = sliceIndex(c);

        int d1 = pdbP1CoSlice[co * N_SLICE + sl] & 0xFF;
        int d2 = pdbP1EoSlice[eo * N_SLICE + sl] & 0xFF;
        return Math.max(d1, d2);
    }

    // Phase 2 heuristic
    private static int heuristicPhase2(CubieCube c) {
        int cp = cornerPermIndex(c);
        int ud = udEdgePermIndex(c);
        int sp = slicePermIndex(c);

        int dC = pdbP2Corners[cp * N_SP + sp] & 0xFF;
        int dE = pdbP2Edges[ud * N_SP + sp] & 0xFF;
        return Math.max(dC, dE);
    }

    // Move application for 18-move metric
    private static void applyMove18(CubieCube c, int move) {
        int face = MOVE_FACE[move];   // 0..5
        int type = move % 3;    // 0: cw, 1: ccw, 2: half

        if (type == 0) {
            c.applyFace(face);     // 90° cw
        } else if (type == 1) {
            c.applyFace(face);
            c.applyFace(face);
            c.applyFace(face);     // 270° cw = 90° ccw
        } else {
            c.applyFace(face);
            c.applyFace(face);        // 180°
        }
    }

    private static int inverseMove(int move) {
        int face = MOVE_FACE[move];
        int type = move % 3;
        int invType;
        if (type == 0)      invType = 1; // U -> U'
        else if (type == 1) invType = 0; // U' -> U
        else                invType = 2; // U2 -> U2
        return face * 3 + invType;
    }

    // Coordinates for orientations + slice pattern
    private static int cornerOriIndex(CubieCube c) {
        int idx = 0;
        for (int i = 0; i < 7; i++) {
            idx = 3 * idx + c.co[i];
        }
        return idx;
    }

    private static void setCornerOriFromIndex(CubieCube c, int idx) {
        for (int i = 0; i < CubieCube.N_CORNERS; i++) {
            c.cp[i] = i;
            c.co[i] = 0;
        }
        for (int i = 0; i < CubieCube.N_EDGES; i++) {
            c.ep[i] = i;
            c.eo[i] = 0;
        }

        int sum = 0;
        for (int i = 6; i >= 0; i--) {
            int v = idx % 3;
            idx /= 3;
            c.co[i] = v;
            sum += v;
        }
        c.co[7] = (3 - (sum % 3)) % 3;
    }

    private static int edgeOriIndex(CubieCube c) {
        int idx = 0;
        for (int i = 0; i < 11; i++) {
            idx = (idx << 1) | (c.eo[i] & 1);
        }
        return idx;
    }

    private static void setEdgeOriFromIndex(CubieCube c, int idx) {
        for (int i = 0; i < CubieCube.N_CORNERS; i++) {
            c.cp[i] = i;
            c.co[i] = 0;
        }
        for (int i = 0; i < CubieCube.N_EDGES; i++) {
            c.ep[i] = i;
            c.eo[i] = 0;
        }

        int sum = 0;
        for (int i = 10; i >= 0; i--) {
            int v = idx & 1;
            idx >>= 1;
            c.eo[i] = v;
            sum += v;
        }
        c.eo[11] = (2 - (sum % 2)) % 2;
    }

    private static boolean isSliceEdge(int e) {
        return (e == E_FR || e == E_FL || e == E_BL || e == E_BR);
    }

    private static int sliceIndex(CubieCube c) {
        int idx = 0;
        int r = 4;
        for (int pos = 0; pos < 12; pos++) {
            if (isSliceEdge(c.ep[pos])) {
                r--;
            } else {
                if (r > 0) {
                    idx += C[12 - pos - 1][r - 1];
                }
            }
        }
        return idx;
    }

    private static void setSliceFromIndex(CubieCube c, int idx) {
        for (int i = 0; i < CubieCube.N_CORNERS; i++) {
            c.cp[i] = i;
            c.co[i] = 0;
        }
        for (int i = 0; i < CubieCube.N_EDGES; i++) {
            c.eo[i] = 0;
        }

        boolean[] isSlicePos = new boolean[12];
        int r = 4;
        for (int pos = 0; pos < 12; pos++) {
            if (r == 0) break;
            int comb = C[12 - pos - 1][r - 1];
            if (idx >= comb) {
                idx -= comb;
            } else {
                isSlicePos[pos] = true;
                r--;
            }
        }

        int slicePtr = 0;
        int nonSlicePtr = 0;
        for (int pos = 0; pos < 12; pos++) {
            if (isSlicePos[pos]) {
                c.ep[pos] = SLICE_EDGES[slicePtr++];
            } else {
                c.ep[pos] = NON_SLICE_EDGES[nonSlicePtr++];
            }
        }
    }


    // Phase 2 coordinates: permutations
    private static int cornerPermIndex(CubieCube c) {
        int idx = 0;
        for (int i = 0; i < 8; i++) {
            int smaller = 0;
            for (int j = i + 1; j < 8; j++) {
                if (c.cp[j] < c.cp[i]) smaller++;
            }
            idx += smaller * FACT[7 - i];
        }
        return idx;
    }

    private static void setCornerPermFromIndex(CubieCube c, int idx) {
        for (int i = 0; i < CubieCube.N_CORNERS; i++) {
            c.co[i] = 0;
        }
        for (int i = 0; i < CubieCube.N_EDGES; i++) {
            c.ep[i] = i;
            c.eo[i] = 0;
        }

        List<Integer> avail = new ArrayList<>();
        for (int i = 0; i < 8; i++) avail.add(i);

        for (int i = 0; i < 8; i++) {
            int fact = FACT[7 - i];
            int pos = idx / fact;
            idx %= fact;
            c.cp[i] = avail.remove(pos).intValue();
        }
    }

    // Permutation index of UD edges (UR,UF,UL,UB,DR,DF,DL,DB) in positions 0..7.
    private static int udEdgePermIndex(CubieCube c) {
        int[] perm = new int[8];
        for (int pos = 0; pos < 8; pos++) {
            perm[pos] = c.ep[pos];
        }

        int idx = 0;
        for (int i = 0; i < 8; i++) {
            int smaller = 0;
            for (int j = i + 1; j < 8; j++) {
                if (perm[j] < perm[i]) smaller++;
            }
            idx += smaller * FACT[7 - i];
        }
        return idx;
    }

    private static void setUDEdgesFromIndex(CubieCube c, int idx) {
        for (int i = 0; i < CubieCube.N_CORNERS; i++) {
            c.cp[i] = i;
            c.co[i] = 0;
        }
        for (int i = 0; i < 12; i++) c.eo[i] = 0;

        List<Integer> avail = new ArrayList<>();
        for (int i = 0; i < 8; i++) avail.add(i);

        int[] perm = new int[8];
        for (int i = 0; i < 8; i++) {
            int fact = FACT[7 - i];
            int pos = idx / fact;
            idx %= fact;
            perm[i] = avail.remove(pos).intValue();
        }

        for (int pos = 0; pos < 8; pos++) {
            c.ep[pos] = perm[pos];
        }
        c.ep[8]  = E_FR;
        c.ep[9]  = E_FL;
        c.ep[10] = E_BL;
        c.ep[11] = E_BR;
    }

    // Permutation index of the 4 slice edges in positions 8..11.
    private static int slicePermIndex(CubieCube c) {
        int[] perm = new int[4];
        for (int i = 0; i < 4; i++) {
            int e = c.ep[8 + i];
            int v;
            if (e == E_FR)      v = 0;
            else if (e == E_FL) v = 1;
            else if (e == E_BL) v = 2;
            else                v = 3; // E_BR
            perm[i] = v;
        }

        int idx = 0;
        for (int i = 0; i < 4; i++) {
            int smaller = 0;
            for (int j = i + 1; j < 4; j++) {
                if (perm[j] < perm[i]) smaller++;
            }
            idx += smaller * FACT4[3 - i];
        }
        return idx;
    }

    private static void setSlicePermFromIndex(CubieCube c, int idx) {
        for (int i = 0; i < 8; i++) c.ep[i] = i; // UR..DB
        for (int i = 0; i < 12; i++) c.eo[i] = 0;
        for (int i = 0; i < CubieCube.N_CORNERS; i++) {
            c.cp[i] = i;
            c.co[i] = 0;
        }

        List<Integer> avail = new ArrayList<>();
        for (int i = 0; i < 4; i++) avail.add(i);

        int[] perm = new int[4];
        for (int i = 0; i < 4; i++) {
            int fact = FACT4[3 - i];
            int pos = idx / fact;
            idx %= fact;
            perm[i] = avail.remove(pos).intValue();
        }

        int[] edges = {E_FR, E_FL, E_BL, E_BR};
        for (int i = 0; i < 4; i++) {
            c.ep[8 + i] = edges[perm[i]];
        }
    }

    // Build all tables and PDBs
    private static void initTables() {
        if (tablesInitialized) return;

        // System.out.println("Building Phase 1 move tables"); debugging
        buildPhase1MoveTables();

        // System.out.println("Building Phase 1 PDBs");
        buildPhase1PDBs();

        // System.out.println("Building Phase 2 move tables");
        buildPhase2MoveTables();

        // System.out.println("Building Phase 2 PDBs");
        buildPhase2PDBs();

        tablesInitialized = true;
    }

    private static void buildPhase1MoveTables() {
        CubieCube tmp = new CubieCube();

        // coMove
        for (int idx = 0; idx < N_CO; idx++) {
            setCornerOriFromIndex(tmp, idx);
            for (int m = 0; m < N_MOVES_PHASE1; m++) {
                CubieCube c2 = tmp.copy();
                applyMove18(c2, m);
                coMove[idx][m] = cornerOriIndex(c2);
            }
        }

        // eoMove
        for (int idx = 0; idx < N_EO; idx++) {
            setEdgeOriFromIndex(tmp, idx);
            for (int m = 0; m < N_MOVES_PHASE1; m++) {
                CubieCube c2 = tmp.copy();
                applyMove18(c2, m);
                eoMove[idx][m] = edgeOriIndex(c2);
            }
        }

        // sliceMove
        for (int idx = 0; idx < N_SLICE; idx++) {
            setSliceFromIndex(tmp, idx);
            for (int m = 0; m < N_MOVES_PHASE1; m++) {
                CubieCube c2 = tmp.copy();
                applyMove18(c2, m);
                sliceMove[idx][m] = sliceIndex(c2);
            }
        }
    }

    private static void buildPhase1PDBs() {
        Arrays.fill(pdbP1CoSlice, (byte) -1);
        Arrays.fill(pdbP1EoSlice, (byte) -1);

        CubieCube solved = new CubieCube();
        int co0 = cornerOriIndex(solved);
        int eo0 = edgeOriIndex(solved);
        int sl0 = sliceIndex(solved);

        // Co + Slice
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int idx0 = co0 * N_SLICE + sl0;
        pdbP1CoSlice[idx0] = 0;
        q.add(new int[]{co0, sl0});

        while (!q.isEmpty()) {
            int[] s = q.poll();
            int co = s[0];
            int sl = s[1];
            int d  = pdbP1CoSlice[co * N_SLICE + sl] & 0xFF;

            for (int m = 0; m < N_MOVES_PHASE1; m++) {
                int co2 = coMove[co][m];
                int sl2 = sliceMove[sl][m];
                int idx = co2 * N_SLICE + sl2;
                if (pdbP1CoSlice[idx] == -1) {
                    pdbP1CoSlice[idx] = (byte) (d + 1);
                    q.add(new int[]{co2, sl2});
                }
            }
        }

        // Eo + Slice
        q.clear();
        idx0 = eo0 * N_SLICE + sl0;
        pdbP1EoSlice[idx0] = 0;
        q.add(new int[]{eo0, sl0});

        while (!q.isEmpty()) {
            int[] s = q.poll();
            int eo = s[0];
            int sl = s[1];
            int d  = pdbP1EoSlice[eo * N_SLICE + sl] & 0xFF;

            for (int m = 0; m < N_MOVES_PHASE1; m++) {
                int eo2 = eoMove[eo][m];
                int sl2 = sliceMove[sl][m];
                int idx = eo2 * N_SLICE + sl2;
                if (pdbP1EoSlice[idx] == -1) {
                    pdbP1EoSlice[idx] = (byte) (d + 1);
                    q.add(new int[]{eo2, sl2});
                }
            }
        }
    }

    private static void buildPhase2MoveTables() {
        CubieCube tmp = new CubieCube();

        // cpMove2
        for (int idx = 0; idx < N_CP; idx++) {
            setCornerPermFromIndex(tmp, idx);
            for (int i = 0; i < N_MOVES_PHASE2; i++) {
                int m = PHASE2_MOVES[i];
                CubieCube c2 = tmp.copy();
                applyMove18(c2, m);
                cpMove2[idx][i] = cornerPermIndex(c2);
            }
        }

        // udEdgeMove2
        for (int idx = 0; idx < N_UD; idx++) {
            setUDEdgesFromIndex(tmp, idx);
            for (int i = 0; i < N_MOVES_PHASE2; i++) {
                int m = PHASE2_MOVES[i];
                CubieCube c2 = tmp.copy();
                applyMove18(c2, m);
                udEdgeMove2[idx][i] = udEdgePermIndex(c2);
            }
        }

        // slicePermMove2
        for (int idx = 0; idx < N_SP; idx++) {
            setSlicePermFromIndex(tmp, idx);
            for (int i = 0; i < N_MOVES_PHASE2; i++) {
                int m = PHASE2_MOVES[i];
                CubieCube c2 = tmp.copy();
                applyMove18(c2, m);
                slicePermMove2[idx][i] = slicePermIndex(c2);
            }
        }
    }

    private static void buildPhase2PDBs() {
        Arrays.fill(pdbP2Corners, (byte) -1);
        Arrays.fill(pdbP2Edges,   (byte) -1);

        CubieCube solved = new CubieCube();
        int cp0 = cornerPermIndex(solved);
        int ud0 = udEdgePermIndex(solved);
        int sp0 = slicePermIndex(solved);

        // Corners + slice perm
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int idx0 = cp0 * N_SP + sp0;
        pdbP2Corners[idx0] = 0;
        q.add(new int[]{cp0, sp0});

        while (!q.isEmpty()) {
            int[] s = q.poll();
            int cp = s[0];
            int sp = s[1];
            int d  = pdbP2Corners[cp * N_SP + sp] & 0xFF;

            for (int i = 0; i < N_MOVES_PHASE2; i++) {
                int cp2 = cpMove2[cp][i];
                int sp2 = slicePermMove2[sp][i];
                int idx = cp2 * N_SP + sp2;
                if (pdbP2Corners[idx] == -1) {
                    pdbP2Corners[idx] = (byte) (d + 1);
                    q.add(new int[]{cp2, sp2});
                }
            }
        }

        // UD edges + slice perm
        q.clear();
        idx0 = ud0 * N_SP + sp0;
        pdbP2Edges[idx0] = 0;
        q.add(new int[]{ud0, sp0});

        while (!q.isEmpty()) {
            int[] s = q.poll();
            int ud = s[0];
            int sp = s[1];
            int d  = pdbP2Edges[ud * N_SP + sp] & 0xFF;

            for (int i = 0; i < N_MOVES_PHASE2; i++) {
                int ud2 = udEdgeMove2[ud][i];
                int sp2 = slicePermMove2[sp][i];
                int idx = ud2 * N_SP + sp2;
                if (pdbP2Edges[idx] == -1) {
                    pdbP2Edges[idx] = (byte) (d + 1);
                    q.add(new int[]{ud2, sp2});
                }
            }
        }
    }

    // Parsing the input net also writing output
    private static char[] parseInputNet(String filename) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        char[] s = new char[54];

        String line;
        int row = 0;
        while ((line = br.readLine()) != null && row < 9) {
            if (row < 3) {
                String t = line.trim();
                for (int i = 0; i < 3; i++) {
                    s[row * 3 + i] = t.charAt(i);
                }
            } else if (row < 6) {
                int rx = row - 3;
                for (int i = 0; i < 3; i++) {
                    s[9  + rx * 3 + i] = line.charAt(i);       // L
                    s[18 + rx * 3 + i] = line.charAt(3 + i);   // F
                    s[27 + rx * 3 + i] = line.charAt(6 + i);   // R
                    s[36 + rx * 3 + i] = line.charAt(9 + i);   // B
                }
            } else {
                String t = line.trim();
                int rx = row - 6;
                for (int i = 0; i < 3; i++) {
                    s[45 + rx * 3 + i] = t.charAt(i);
                }
            }
            row++;
        }
        br.close();
        return s;
    }

    /**
     * Convert to 1-letter format
     *  e.g. U is "U", U' is "UUU", U2 is "UU"
     */
    private static List<String> convertSolutionToOutput(List<Integer> moves) {
        if (moves == null) return null;
        List<String> out = new ArrayList<>();
        for (int m : moves) {
            String name = MOVE_NAMES[m];
            char face = name.charAt(0);
            if (name.length() == 1) {
                out.add(String.valueOf(face));   // quarter turn
            } else {
                char suff = name.charAt(1);
                if (suff == '\'') {
                    // prime -> 3
                    out.add(String.valueOf(face));
                    out.add(String.valueOf(face));
                    out.add(String.valueOf(face));
                } else { // '2'
                    out.add(String.valueOf(face));
                    out.add(String.valueOf(face));
                }
            }
        }
        return out;
    }

    private static void writeOutput(String filename, List<String> moves) throws IOException {
        try (FileWriter fw = new FileWriter(filename)) {
            if (moves == null || moves.isEmpty()) {
                fw.write("No solution found");
            } else {
                for (String s : moves) {
                    fw.write(s);
                }
            }
        }
    }
}
