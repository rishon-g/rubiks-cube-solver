package org.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Small tool to verify that a solution string actually works
 */
public class VerifySolution {

    public static void main(String[] args) {
        String scrambleFile = args[0];
        String solutionFile = args[1];

        try {
            char[] net = parseInputNet(scrambleFile);
            FaceCube fc = new FaceCube(net);
            CubieCube cube = fc.toCubieCube();

            String solution = readAll(solutionFile).trim();
            System.out.println("Scramble: " + scrambleFile);
            System.out.println("Solution string: " + solution);

            for (int i = 0; i < solution.length(); i++) {
                char m = solution.charAt(i);
                int face = moveCharToFace(m);
                if (face == -1) {
                    System.err.println("Invalid move character '" + m + "' at position " + i);
                    return;
                }
                cube.applyFace(face);
            }

            if (cube.isSolved()) {
                System.out.println("Solution works");
            } else {
                System.out.println("Solution doesn't work");
            }

        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        } catch (RuntimeException re) {
            System.err.println("Runtime error: " + re.getMessage());
            re.printStackTrace();
        }
    }

    // Same net parser in Solver
    private static char[] parseInputNet(String filename) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        char[] s = new char[54];
        String line;
        int row = 0;

        while ((line = br.readLine()) != null && row < 9) {
            if (row < 3) {
                // Top (U)
                String t = line.trim();
                for (int i = 0; i < 3; i++) {
                    s[row * 3 + i] = t.charAt(i);
                }
            } else if (row < 6) {
                // Middle: L F R B
                int rx = row - 3;
                for (int i = 0; i < 3; i++) {
                    s[9  + rx * 3 + i] = line.charAt(i);       // L
                    s[18 + rx * 3 + i] = line.charAt(3 + i);   // F
                    s[27 + rx * 3 + i] = line.charAt(6 + i);   // R
                    s[36 + rx * 3 + i] = line.charAt(9 + i);   // B
                }
            } else {
                // Bottom (D)
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

    private static String readAll(String filename) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line.trim());
            }
        }
        return sb.toString();
    }

    /**
     * Map move letter to CubieCube face index.
     * 0=U, 1=R, 2=F, 3=D, 4=L, 5=B
     */
    private static int moveCharToFace(char c) {
        switch (c) {
            case 'U': return 0;
            case 'R': return 1;
            case 'F': return 2;
            case 'D': return 3;
            case 'L': return 4;
            case 'B': return 5;
            default:  return -1;
        }
    }
}
