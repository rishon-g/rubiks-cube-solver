# Two-Phase Rubik's Cube Solver
A high-performance Java implementation of Kociemba's two-phase algorithm designed to solve any Rubik's Cube configuration efficiently.

# Build & Run Instructions
1. Build

Compile the project and resolve all dependencies using Maven:
```bash
mvn clean compile
```

2. Solve a Cube
   
Run the solver by passing your input file (which defines the color state of your cube) and an output file path:
```bash
mvn exec:java -Dexec.mainClass="rubikscubesolver.Solver" -Dexec.args="scrambles/INSERT_INPUT_FILE_HERE scrambles/output.txt"
```

# Input Format
The solver expects a 9-line input file representing the cube's color state. Do not use spaces between color characters. 
Each character corresponds to a sticker color (e.g., O=Orange, W=White, G=Green, Y=Yellow, B=Blue, R=Red).

Example:
```bash
OOG
OOW
OOW
YGGWWRBBOYBB
GGGWWOYBBYYY
GGGWWWOBBYYY
RRB
RRR
RRR
```
