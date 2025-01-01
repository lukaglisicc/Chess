package Engine;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

public class Debugger {

    private Engine engine;
    private boolean isBencmarking = false;
    private long benchStart = 0;

    private long moveGenerationTime = 0;
    private long moveTime = 0;
    private long undoTime = 0;

    public Debugger(Engine engine) {
        this.engine = engine;
    }



    public void benchmark (boolean start){
        if (start){
            benchStart = System.nanoTime();
            isBencmarking = true;
        } else if (isBencmarking){
            long end = System.nanoTime();
            isBencmarking = false;
            long duration = end - benchStart;
            System.out.println("Time taken (ms): " + duration / 1_000_000.0 + " ms");
        }
    }

    public List<Int2> getWhiteAttack (){
        return engine.getFieldsFromBitmask(engine.whiteAttack);
    }

    public List<Int2> getBlackAttack (){
        return engine.getFieldsFromBitmask(engine.blackAttack);
    }

    public List<Int2> getWhitePieces (){
        return engine.getFieldsFromBitmask(engine.piecemaskw);
    }

    public List<Int2> getBlackPieces (){
        return engine.getFieldsFromBitmask(engine.piecemaskb);
    }

    public List<Int2> getCheckingPieces (boolean isWhite){
//        Int2 kingPos;
//        long kingMask;
//        long queenMask;
//        long rookMask;
//        long bishopMask;
//        long knightMask;
//        long pawnMask;
//        if (isWhite) {
//            kingMask = engine.kingw;
//            queenMask = engine.queenb;
//            rookMask = engine.rookb;
//            bishopMask = engine.bishopb;
//            knightMask = engine.knightb;
//            pawnMask = engine.pawnb;
//        } else {
//            kingMask = engine.kingb;
//            queenMask = engine.queenw;
//            rookMask = engine.rookw;
//            bishopMask = engine.bishopw;
//            knightMask = engine.knightw;
//            pawnMask = engine.pawnw;
//        }
//        kingPos = engine.getFieldsFromBitmask(kingMask).getFirst();
//        long mask = engine.getQueenAttack(kingPos, isWhite) | engine.getKnightAttack(kingPos, isWhite);
//        queenMask = queenMask & mask;
//        rookMask = rookMask & mask;
//        bishopMask = bishopMask & mask;
//        knightMask = knightMask & mask;
//        pawnMask = pawnMask & mask;
//        //Idk what is faster here
////        queenMask = queenMask & engine.getQueenAttack(kingPos, isWhite, engine.getCurrentBoard());
////        rookMask = rookMask & engine.getRookAttack(kingPos, isWhite, engine.getCurrentBoard());
////        bishopMask = bishopMask & engine.getBishopAttack(kingPos, isWhite, engine.getCurrentBoard());
////        knightMask = knightMask & engine.getKnightAttack(kingPos, isWhite, engine.getCurrentBoard());
////        pawnMask = pawnMask & engine.getPawnAttack(kingPos, isWhite, engine.getCurrentBoard());
//
//        long result = 0;
//
//        for (Int2 f : engine.getFieldsFromBitmask(queenMask)) {
//            if ((engine.getQueenAttack(f, isWhite) & kingMask) != 0)
//                result |= engine.getFieldMask(f);
//        }
//        for (Int2 f : engine.getFieldsFromBitmask(rookMask)) {
//            if ((engine.getRookAttack(f, isWhite) & kingMask) != 0)
//                result |= engine.getFieldMask(f);
//        }
//        for (Int2 f : engine.getFieldsFromBitmask(bishopMask)) {
//            if ((engine.getBishopAttack(f, isWhite) & kingMask) != 0)
//                result |= engine.getFieldMask(f);
//        }
//        for (Int2 f : engine.getFieldsFromBitmask(knightMask)) {
//            if ((engine.getKnightAttack(f, isWhite) & kingMask) != 0)
//                result |= engine.getFieldMask(f);
//        }
//        for (Int2 f : engine.getFieldsFromBitmask(pawnMask)) {
//            if ((engine.getPawnAttack(f, !isWhite) & kingMask) != 0)
//                result |= engine.getFieldMask(f);
//        }
//        return engine.getFieldsFromBitmask(result);
        return null;
    }

    public List<Int2> getPinnedPiece(boolean isWhite){
        long result = 0;
        if (isWhite) {
            for (int i = 0; i < 8; i++){
                result |= engine.whitePins[i];
            }
        } else {
            for (int i = 0; i < 8; i++){
                result |= engine.blackPins[i];
            }
        }
        return engine.getFieldsFromBitmask(result);
    }

    public List<Int2> getSpecificAttack(AttackType type, boolean isWhite){
//        long kingMask;
//        if (isWhite)
//            kingMask = engine.kingw;
//        else
//            kingMask = engine.kingb;
//        Int2 kingPos = engine.getFieldsFromBitmask(kingMask).getFirst();
//        long mask = engine.getSpecificAttack(kingPos, type);
//        return engine.getFieldsFromBitmask(mask);
        return null;
    }

    public void bencmarkMoveGeneration(){
        benchmark(true);
        List<Integer> moves = engine.getAllValidMoves();
        benchmark(false);
    }


    public List<Int2> getCheckMasks(boolean isWhite){
        long[] masks;
        if (isWhite)
            masks = engine.whiteChecks;
        else
            masks = engine.blackChecks;
        List<Int2> checkMasks = new ArrayList<Int2>();
        for (int i = 0; i < masks.length; i++){
            checkMasks.addAll(engine.getFieldsFromBitmask(masks[i]));
        }
        return checkMasks;
    }

    public void checkCheck (){
        System.out.println("White in check:" + engine.isWhiteInCheck);
        System.out.println("Black in check:" + engine.isBlackInCheck);
    }

    public void perft (int depth){
        moveGenerationTime = 0;
        moveTime = 0;
        undoTime = 0;
        int result = 0;
        benchmark(true);
        result = recursiveMove(depth);
        benchmark(false);
        System.out.println("Perft: " + depth + " Nodes: " + result);
//        System.out.println("Move generation time (ms): " + moveGenerationTime / 1_000_000.0 + " ms");
//        System.out.println("Move time (ms): " + moveTime / 1_000_000.0 + " ms");
//        System.out.println("Undo time (ms): " + undoTime / 1_000_000.0 + " ms");
//        System.out.println();

//        List<Integer> moves = engine.getAllValidMoves();
//        List<String> moveStrings = new ArrayList<>(moves.size());
//        List<Integer> moveCount = new ArrayList<>(moves.size());
//
//        for (int move : moves) {
//            Move idk = Move.fromPackedInteger(move);
//            String start = engine.fieldFromIndex(idk.startIndex);
//            String end = engine.fieldFromIndex(idk.endIndex);
//            moveStrings.add(start + end);
//            int result = 0;
//            engine.move(move);
//            result += recursiveMove(depth - 1);
//            engine.undoMove(move);
//            moveCount.add(result);
//        }
//
//        for (int i = 0; i < moveStrings.size(); i++){
//            System.out.println(moveStrings.get(i) + ": " + moveCount.get(i));
//        }
//        int result = 0;
//        for (Integer count : moveCount){
//            result += count;
//        }
//        System.out.println(result);
//        System.out.println();
    }

    private int recursiveMove (int depth){
        int result = 0;
        //long start = System.nanoTime();
        List<Integer> moves = engine.getAllValidMoves();
        //moveGenerationTime += System.nanoTime() - start;
        if (depth == 1)
            return moves.size();
        if (depth == 0)
            return 1;

        for (Integer move : moves){
            //start = System.nanoTime();
            engine.move(move);
            //moveTime += System.nanoTime() - start;
            result += recursiveMove(depth - 1);
            //start = System.nanoTime();
            engine.undoMove(move);
            //undoTime += System.nanoTime() - start;
        }
        return result;
    }

    public void printBoardState(){
        System.out.println(engine.isWhiteTurn);
    }

    public void updateBoard(){
        engine.updateGUIBoard();
    }

    public void undoLastMove(){
        engine.undoLastMove();
    }

    public List<Int2> getEPMask (){
        return engine.getFieldsFromBitmask(engine.enPassant);
    }

    public void comparePerft(){
        try {
            // Create a File object for the text file
            File file = new File("src/main/resources/Perft.txt");

            // Create a Scanner to read the file
            Scanner scanner = new Scanner(file);

            List<String> myMoves = new ArrayList<>();
            List<Integer> myNodeCount = new ArrayList<>();
            List<String> sfMoves = new ArrayList<>();
            List<Integer> sfNodeCount = new ArrayList<>();

            boolean scanMy = true;
            // Read the file line by line
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.isBlank()) {
                    scanMy = false;
                    continue;
                }
                String[] res = line.split(": ");
                if (scanMy) {
                    myMoves.add(res[0]);
                    myNodeCount.add(Integer.parseInt(res[1]));
                } else {
                    sfMoves.add(res[0]);
                    sfNodeCount.add(Integer.parseInt(res[1]));
                }
            }

            for (int i = 0; i < myMoves.size(); i++){
                int x = 0;
                boolean hasCounterpart = false;
                for (int j = 0; j < sfMoves.size(); j++){
                    if (myMoves.get(i).equals(sfMoves.get(j))){
                        hasCounterpart = true;
                        x = j;
                        break;
                    }
                }
                if (!hasCounterpart){
                    System.out.println(myMoves.get(i) + ": " + myNodeCount.get(i) + " no counterpart");
                } else {
                    if (!Objects.equals(myNodeCount.get(i), sfNodeCount.get(x))){
                        System.out.println(myMoves.get(i) + ": " + myNodeCount.get(i) + " stockfish: " + sfNodeCount.get(x));
                    }
                }
            }
            if (sfMoves.size() > myNodeCount.size()) {
                for (int i = 0; i < sfMoves.size(); i++) {
                    boolean hasCounterpart = false;
                    for (int j = 0; j < myMoves.size(); j++) {
                        if (myMoves.get(j).equals(sfMoves.get(i))) {
                            hasCounterpart = true;
                            break;
                        }
                    }
                    if (!hasCounterpart) {
                        System.out.println("stockfish " + sfMoves.get(i) + ": " + sfNodeCount.get(i) + " no counterpart");
                    }
                }
            }
            // Close the scanner
            scanner.close();

        } catch (FileNotFoundException e) {
            System.out.println("File not found: " + e.getMessage());
        }
    }

    public void loadBadState(){
        if (engine.error){
            engine.error = false;
            engine.loadBoardState(engine.badState);
            engine.badState = null;
        }
    }

}
