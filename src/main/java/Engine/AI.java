package Engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AI {

    private final Engine engine;
    private int pruneCount;
    private int evalCount;

    private final long SECOND = 1000000000;
    private final long maxTime = SECOND * 2;
    private long startTime;

    public AI(Engine engine) {
        this.engine = engine;
    }

    private double evaluate(){
        double result = 0;
        evalCount++;
        result += Long.bitCount(engine.pawnw) - Long.bitCount(engine.pawnb);
        result += (Long.bitCount(engine.bishopw) - Long.bitCount(engine.bishopb)) * 3;
        result += (Long.bitCount(engine.knightw) - Long.bitCount(engine.knightb)) * 3.5;
        result += (Long.bitCount(engine.rookw) - Long.bitCount(engine.rookb)) * 5;
        result += (Long.bitCount(engine.queenw) - Long.bitCount(engine.queenb)) * 9;
        if (engine.isBlackInCheck)
            result += 5;
        if (engine.isWhiteInCheck)
            result -= 5;
        return result;
    }

    public int getMove (){
        long time = System.nanoTime();
        pruneCount = 0;
        evalCount = 0;
        List<Integer> moves = engine.getAllValidMoves();
        List<Double> moveEvaluations = new ArrayList<>(moves.size());
        if (moves.isEmpty())
            return 0;
        if (moves.size() == 1)
            return moves.getFirst();



        int depth = 0;
        int bestMove = moves.getFirst();
        double alpha = -Double.MAX_VALUE;
        double beta = Double.MAX_VALUE;

        startTime = System.nanoTime();
        while (System.nanoTime() - startTime < maxTime) {

            int bestMoveInDepth = moves.getFirst();
            moveEvaluations.clear();
            double minValue = Double.MAX_VALUE;
            for (Integer move : moves){

                if (System.nanoTime() - startTime >= maxTime) break;

                engine.move(move);
                double value = max(depth, alpha, beta);
                engine.undoMove(move);
                moveEvaluations.add(value);
                if (value < minValue){
                    minValue = value;
                    bestMoveInDepth = move;
                    beta = value;
                }
            }

            if (System.nanoTime() - startTime >= maxTime) break;

            bestMove = bestMoveInDepth;
            sortTwoLists(moves, moveEvaluations);

            depth++;
        }




        System.out.println("Depth: " + depth);
        System.out.println("Evaluations: " + evalCount);
        System.out.println("Pruned: " + pruneCount);
        System.out.println("Time: " + (System.nanoTime() - time) / 1_000_000.0 + " ms");
        Move.printMove(bestMove);
        return bestMove;
    }

    private double min(int depth, double alpha, double beta){
        if (depth == 0)
            return evaluate();

        if (System.nanoTime() - startTime >= maxTime) return 0;

        List<Integer> moves = engine.getAllValidMoves();
        if (moves.isEmpty()){
            if (engine.isBlackInCheck)
                return Double.MAX_VALUE - 1;
            else
                return 0;
        }


        double min = Double.MAX_VALUE;
        for (Integer move : moves) {
            engine.move(move);
            double value = max(depth - 1, alpha, beta);
            if (value < min) {
                min = value;
                beta = value;
            }
            engine.undoMove(move);
            if (min < alpha) {
                pruneCount++;
                return min;
            }
        }
        return min;
    }

    private double max(int depth, double alpha, double beta){
        if (depth == 0)
            return evaluate();

        if (System.nanoTime() - startTime >= maxTime) return 0;

        List<Integer> moves = engine.getAllValidMoves();
        if (moves.isEmpty()){
            if (engine.isWhiteInCheck)
                return -Double.MAX_VALUE + 1;
            else
                return 0;
        }


        double max = -Double.MAX_VALUE;
        for (Integer move : moves) {
            engine.move(move);
            double value = min(depth - 1, alpha, beta);
            if (value > max) {
                max = value;
                alpha = value;
            }
            engine.undoMove(move);
            if (max > beta) {
                pruneCount++;
                return max;
            }
        }
        return max;
    }

    public static void sortTwoLists(List<Integer> move, List<Double> values) {
        // Create a list of pairs (index + value)
        List<Object[]> pairedList = new ArrayList<>();
        for (int i = 0; i < move.size(); i++) {
            pairedList.add(new Object[]{move.get(i), values.get(i)});
        }

        // Sort the paired list by the second element (values)
        pairedList.sort(Comparator.comparingDouble(o -> (double) o[1]));

        // Update the original lists based on the sorted pairs
        for (int i = 0; i < pairedList.size(); i++) {
            move.set(i, (Integer) pairedList.get(i)[0]);
            values.set(i, (Double) pairedList.get(i)[1]);
        }
    }
}
