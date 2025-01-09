package Engine;


import Observer.IPublisher;
import Observer.ISubscriber;
import Observer.NotificationType;
import lombok.Getter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.*;

public class Engine implements IPublisher {

    long pawnb = 0;
    long pawnw = 0;
    long kingb = 0;
    long kingw = 0;
    long queenb = 0;
    long queenw = 0;
    long rookb = 0;
    long rookw = 0;
    long bishopb = 0;
    long bishopw = 0;
    long knightb = 0;
    long knightw = 0;
    long piecemask = 0;
    long piecemaskw = 0;
    long piecemaskb = 0;
    long whiteAttack = 0;
    long blackAttack = 0;
    long enPassant = 0;
    public boolean isWhiteTurn = true;
    boolean isBlackInCheck = false;
    boolean isWhiteInCheck = false;
    long[] whiteChecks = new long[2];
    long[] blackChecks = new long[2];
    long[] whitePins = new long[8];
    long[] blackPins = new long[8];
    long whitePinMask = 0;
    long blackPinMask = 0;
    boolean canWhiteCastleQ = true;
    boolean canWhiteCastleK = true;
    boolean canBlackCastleQ = true;
    boolean canBlackCastleK = true;

    BoardState badState;
    boolean error = false;

    private static Engine instance;
    long[] kingmoveLUT = new long[64];
    long[] rookmoveLUT = new long[64];
    long[] bishopmoveLUT = new long[64];
    long[] knightmoveLUT = new long[64];
    long[] pawnmoveWLUT = new long[64];
    long[] pawnattackWLUT = new long[64];
    long[] pawnmoveBLUT = new long[64];
    long[] pawnattackBLUT = new long[64];
    long[] leftDiagLUT = new long[64];
    long[] rightDiagLUT = new long[64];
    long[] fieldMasksLUT = new long[64];
    private List<Integer>[] rowsLUT = new List[256];
    private byte[][] byteMaskFriendlyLUT = new byte[8][256];
    private byte[][] byteMaskEnemyLUT = new byte[8][256];
    private long[] byteColumnLUT = new long[256];
    private String[] indexFields = {"a8","b8","c8","d8","e8","f8","g8","h8","a7","b7","c7","d7","e7","f7","g7","h7","a6","b6","c6","d6","e6","f6","g6","h6","a5","b5","c5","d5","e5","f5","g5","h5","a4","b4","c4","d4","e4","f4","g4","h4","a3","b3","c3","d3","e3","f3","g3","h3","a2","b2","c2","d2","e2","f2","g2","h2","a1","b1","c1","d1","e1","f1","g1","h1"};
    private int[] indexXLUT = new int[64];
    private int[] indexYLUT = new int[64];
    @Getter
    private final Debugger debugger = new Debugger(this);
    private final AI ai = new AI(this);

    public boolean lockInput = false;

    final int KING_WHITE = 0;
    final int QUEEN_WHITE = 1;
    final int ROOK_WHITE = 2;
    final int BISHOP_WHITE = 3;
    final int KNIGHT_WHITE = 4;
    final int PAWN_WHITE = 5;
    final int KING_BLACK = 6;
    final int QUEEN_BLACK = 7;
    final int ROOK_BLACK = 8;
    final int BISHOP_BLACK = 9;
    final int KNIGHT_BLACK = 10;
    final int PAWN_BLACK = 11;

    long checkCheckTime = 0;
    long checkMaskTime = 0;
    long updateAttacksTime = 0;
    long pinTime = 0;
    long utilityTime = 0;
    long kingTime = 0;
    long queenTime = 0;
    long rookTime = 0;
    long bishopTime = 0;
    long knightTime = 0;
    long pawnTime = 0;

    private List<ISubscriber> subs = new ArrayList<ISubscriber>();



    public static Engine getEngine() {
        if (instance == null) {
            instance = new Engine();
            instance.initialize();
        }
        return instance;
    }

    public void initialize() {
        precomputeIndexLUT();
        precomputeFieldMasks();
        precomputeRows();
        precomputeByteMasks();
        precomputeByteColumns();

        precomputeKingMove();
        precomputeRookMove();
        precomputeBishopMove();
        precomputeKnightMove();
        precomputePawnMove();
    }


    @Override
    public void notify(NotificationType type, Object o) {
        for (ISubscriber s : subs) {
            s.update(type,o);
        }
    }

    @Override
    public void addSubscriber(ISubscriber subscriber) {
        subs.add(subscriber);
    }

    @Override
    public void removeSubscriber(ISubscriber subscriber) {
        subs.remove(subscriber);
    }


    /**
     * Updates the gui with the current state of the board.
     */
    public void updateGUIBoard() {
        //Go through every board index
        for (int i = 0; i < 64; i++){
            //Get piece type and update board square
            long fieldMask = getFieldMask(i);
            char type = getCharPieceType(getPieceType(fieldMask));
            notify(NotificationType.PIECE_AT_FIELD, type + " " + i % 8 + " " + i / 8);
        }
        //Update check
        notify(NotificationType.CHECK, null);
        if (isWhiteInCheck){
            notify(NotificationType.CHECK, getFieldsFromBitmask(kingw).getFirst());
        }
        if (isBlackInCheck){
            notify(NotificationType.CHECK, getFieldsFromBitmask(kingb).getFirst());
        }
    }

    /**
     * Parses a UCI move and makes it on the board.
     * It's safe to pass an invalid move as it's verified before being made.
     * @param move UCI move to make
     */
    public void move(String move) {
        int packedMove = parseUCIMove(move);
        if (packedMove != 0) {
            move(packedMove);
            updateGUIBoard();

            makeAIMove();
        } else
            updateGUIBoard();
    }

    private void makeAIMove(){
        lockInput = true;
        CompletableFuture.supplyAsync(() -> {
            int aiMove = 0;
            try {
                aiMove = ai.getMove();
            } catch (Exception e){

            }
            return aiMove;
        }).thenAccept(result -> {
            if (result != 0){
                move(result);
                updateGUIBoard();
                lockInput = false;
                notify(NotificationType.HIGHLIGHT_FIELDS, getFieldsFromBitmask(getFieldMask((result >> 6) & 0x3F)));
            }
        });
    }

    /**
     * Makes the provided move on the board. Move is provided in packed format.
     * The input move should be valid as it isn't verified before being made.
     * @param move Move in packed int format
     */
    public void move (int move){
        //Unpack move
        int startIndex = move & 0x3F;
        int endIndex = (move >> 6) & 0x3F;
        int startPiece = (move >> 12) & 0xF;
        int endPiece = (move >> 16) & 0xF;
        boolean castle = ((move >> 20) & 0x1) != 0;
        boolean isEnPassant = ((move >> 21) & 0x1) != 0;
        boolean promotion = ((move >> 22) & 0x1) != 0;
        boolean removedCastleBK = ((move >> 23) & 0x1) != 0;
        boolean removedCastleBQ = ((move >> 24) & 0x1) != 0;
        boolean removedCastleWK = ((move >> 25) & 0x1) != 0;
        boolean removedCastleWQ = ((move >> 26) & 0x1) != 0;

        //Clear en passant target square
        enPassant = 0;

        if (castle){
            //Do castle
            if (startPiece == KING_WHITE){
                canWhiteCastleQ = false;
                canWhiteCastleK = false;
                updateBitMasksCastle(true, endIndex == 62);
            } else {
                canBlackCastleQ = false;
                canBlackCastleK = false;
                updateBitMasksCastle(false, endIndex == 6);
            }
        } else if (isEnPassant){
            //Do en passant
            updateBitMasksEP(startPiece == PAWN_WHITE, startIndex, endIndex);
        } else if (promotion){
            //Do promotion
            updateBitMasksPromote(isWhiteTurn, startIndex, endIndex, endPiece, startPiece);
            //Remove castling rights
            if (removedCastleBK) canBlackCastleK = false;
            if (removedCastleBQ) canBlackCastleQ = false;
            if (removedCastleWK) canWhiteCastleK = false;
            if (removedCastleWQ) canWhiteCastleQ = false;
        } else {
            //Do regular move
            //Remove castling rights
            if (removedCastleBK) canBlackCastleK = false;
            if (removedCastleBQ) canBlackCastleQ = false;
            if (removedCastleWK) canWhiteCastleK = false;
            if (removedCastleWQ) canWhiteCastleQ = false;

            //Set en passant mask
            if((startPiece == PAWN_WHITE || startPiece == PAWN_BLACK) && Math.abs(startIndex - endIndex) == 16)
                enPassant = getFieldMask(startIndex + (endIndex - startIndex) / 2);

            updateBitMasks(startPiece, endPiece, startIndex, endIndex);
        }

        //Update global piecemask, check for pins and check
        piecemask = piecemaskb | piecemaskw;
        inCheck(!isWhiteTurn);
        checkForPinsAndCheck(true);
        checkForPinsAndCheck(false);
        //Set active side
        isWhiteTurn = !isWhiteTurn;


        if (getIndicesFromBitmask(piecemaskb).size() > 16 && !error){
            Move.printMove(move);
            badState = saveBoardState();
            error = true;
        }
    }

    public void undoLastMove() {
//        if (moveStack.empty())
//            return;
//        int move = moveStack.peek();
//        undoMove(move);
//        updateGUIBoard();
    }

    /**
     * Undoes a move on the board.Move is provided in packed format.
     * The input move should be valid as it isn't verified before being made.
     * @param move Move in packed int format
     */
    public void undoMove (int move) {
        //Unpack move
        int startIndex = move & 0x3F;
        int endIndex = (move >> 6) & 0x3F;
        int startPiece = (move >> 12) & 0xF;
        int endPiece = (move >> 16) & 0xF;
        boolean castle = ((move >> 20) & 0x1) != 0;
        boolean isEnPassant = ((move >> 21) & 0x1) != 0;
        boolean promotion = ((move >> 22) & 0x1) != 0;
        boolean removedCastleBK = ((move >> 23) & 0x1) != 0;
        boolean removedCastleBQ = ((move >> 24) & 0x1) != 0;
        boolean removedCastleWK = ((move >> 25) & 0x1) != 0;
        boolean removedCastleWQ = ((move >> 26) & 0x1) != 0;
        boolean lastMoveEP = ((move >> 27) & 0x1) != 0;
        int targetEP = ((move >> 28) & 0x7);


        //Restore en passant target square
        if (lastMoveEP){
            if (isWhiteTurn){
                //if it's white's turn, last was black's
                enPassant = getFieldMask(targetEP + 40);
            } else {
                enPassant = getFieldMask(targetEP + 16);
            }
        } else {
            enPassant = 0;
        }


        if (castle){
            //Undo castle
            if (startPiece == KING_WHITE){
                undoBitMasksCastle(true, endIndex == 62);
            } else {
                undoBitMasksCastle(false, endIndex == 6);
            }
            //Restore castling rights
            if (removedCastleBK) canBlackCastleK = true;
            if (removedCastleBQ) canBlackCastleQ = true;
            if (removedCastleWK) canWhiteCastleK = true;
            if (removedCastleWQ) canWhiteCastleQ = true;
        } else if (isEnPassant){
            //Undo en passant
            undoBitMasksEP(startPiece == PAWN_WHITE, startIndex, endIndex);
        } else if (promotion){
            //Undo promotion
            undoBitMasksPromote(!isWhiteTurn, startIndex, endIndex, endPiece, startPiece);
            //Restore castling rights
            if (removedCastleBK) canBlackCastleK = true;
            if (removedCastleBQ) canBlackCastleQ = true;
            if (removedCastleWK) canWhiteCastleK = true;
            if (removedCastleWQ) canWhiteCastleQ = true;
        } else {
            //Undo regular move
            //Restore castling rights
            if (removedCastleBK) canBlackCastleK = true;
            if (removedCastleBQ) canBlackCastleQ = true;
            if (removedCastleWK) canWhiteCastleK = true;
            if (removedCastleWQ) canWhiteCastleQ = true;


            undoBitMasks(startPiece, endPiece, startIndex, endIndex);
        }

        //Update global piecemask, check for pins and check
        piecemask = piecemaskb | piecemaskw;
        inCheck(!isWhiteTurn);
        checkForPinsAndCheck(true);
        checkForPinsAndCheck(false);
        //Set active side
        isWhiteTurn = !isWhiteTurn;

    }

    /**
     * Returns a bitmask of all valid moves that can be made from the provided square on the board.
     * Requires additional information about starting piece to minimize repeated calculations.
     * Used in bulk move generation.
     * @param index Index of starting square
     * @param piece Type of starting piece
     * @param isWhite Color of starting piece
     * @param fieldMask Bitmask of starting square
     * @return Bitmask of valid moves
     */
    public long getValidMoves(int index, int piece, boolean isWhite, long fieldMask){
        boolean inCheck;
        //If the piece on the provided square doesn't belong to the active side, return 0
        if (isWhite) {
            if ((fieldMask & piecemaskw) == 0)
                return 0;
            inCheck = isWhiteInCheck;
        } else {
            if ((fieldMask & piecemaskb) == 0)
                return 0;
            inCheck = isBlackInCheck;
        }

        //Get pin mask
        long pinMask = getPinMask(fieldMask, isWhite);

        if (inCheck){
            //If in check, get check mask
            long epMask = 0;
            long checkMask;
            if (isWhite) {
                checkMask = whiteChecks[0];
                if (whiteChecks[1] != 0)
                    checkMask = checkMask & whiteChecks[1];
                else if ((checkMask & pawnb) != 0 && enPassant != 0){
                    epMask = enPassant;
                }
            } else {
                checkMask = blackChecks[0];
                if (blackChecks[1] != 0)
                    checkMask = checkMask & blackChecks[1];
                else if ((checkMask & pawnw) != 0 && enPassant != 0){
                    epMask = enPassant;
                }
            }

            switch (piece){
                case KING_WHITE, KING_BLACK -> {
                    return  getKingMove(index, isWhite);
                }
                case ROOK_WHITE, ROOK_BLACK -> {
                    return (getRookMove(index, indexXLUT[index], indexYLUT[index], isWhite) & pinMask) & checkMask;
                }
                case PAWN_WHITE, PAWN_BLACK -> {
                    return  (getPawnMove(index, indexXLUT[index], indexYLUT[index], isWhite) & pinMask) & (checkMask | epMask);
                }
                case BISHOP_WHITE, BISHOP_BLACK -> {
                    return  (getBishopMove(index, indexXLUT[index], indexYLUT[index], isWhite) & pinMask) & checkMask;
                }
                case KNIGHT_WHITE, KNIGHT_BLACK -> {
                    return  (getKnightMove(index, isWhite) & pinMask) & checkMask;
                }
                case QUEEN_WHITE, QUEEN_BLACK -> {
                    return  (getQueenMove(index, indexXLUT[index], indexYLUT[index], isWhite) & pinMask) & checkMask;
                }
                default -> {
                    return 0;
                }
            }
        } else {
            //If not in check, no need to retrieve check mask
            switch (piece){
                case KING_WHITE, KING_BLACK -> {
                    return getKingMove(index, isWhite);
                }
                case ROOK_WHITE, ROOK_BLACK -> {
                    return (getRookMove(index, indexXLUT[index], indexYLUT[index], isWhite) & pinMask);
                }
                case PAWN_WHITE, PAWN_BLACK -> {
                    return  (getPawnMove(index, indexXLUT[index], indexYLUT[index], isWhite) & pinMask);
                }
                case BISHOP_WHITE, BISHOP_BLACK -> {
                    return  (getBishopMove(index, indexXLUT[index], indexYLUT[index], isWhite) & pinMask);
                }
                case KNIGHT_WHITE, KNIGHT_BLACK -> {
                    return  (getKnightMove(index, isWhite) & pinMask);
                }
                case QUEEN_WHITE, QUEEN_BLACK -> {
                    return  (getQueenMove(index, indexXLUT[index], indexYLUT[index], isWhite) & pinMask);
                }
                default -> {
                    return 0;
                }
            }
        }
    }

    /**
     * Returns a bitmask of all valid moves that can be made from the provided square on the board.
     * Only requires starting index and isn't suitable for bulk calculations.
     * @param index Index of starting square
     * @return Bitmask of valid moves
     */
    public long getValidMoves(int index){
        //Get required info about piece
        boolean isWhite = isWhiteTurn;
        long fieldMask = getFieldMask(index);
        int piece = getPieceType(fieldMask);
        //Call other method
        return getValidMoves(index, piece, isWhite, fieldMask);
    }

    /**
     * Computes all valid moves of the active side for the current state of the board.
     * @return List of moves in packed int format
     */
    public List<Integer> getAllValidMoves(){

        //Set initial capacity to average number of valid moves in a chess position
        List<Integer> validMoves = new ArrayList<>(30);

        //Code is identical for both cases, but it's done like this to minimize setting of temporary variables
        //Very likely unnecessary, but might provide marginal speed up in bulk move generation
        if (isWhiteTurn){
            //Get indices for all active squares
            for (int startIndex : getIndicesFromBitmask(piecemaskw)){
                //Calculate piece info
                long startMask = getFieldMask(startIndex);
                int startPiece = getPieceType(startMask, true);

                //Get all valid moves for piece
                long moveMask = getValidMoves(startIndex, startPiece, true, startMask);
                //Get indices of all valid moves
                for (int endIndex : getIndicesFromBitmask(moveMask)){
                    //Calculate move info
                    long endMask = getFieldMask(endIndex);
                    int endPiece = getPieceType(endMask, false);
                    boolean castle = ((startPiece == KING_WHITE) && (startIndex == 60) && ((endIndex == 62) || (endIndex == 58)));
                    boolean isEnPassant = ((startPiece == PAWN_WHITE) && ((endMask & enPassant) != 0));
                    boolean promotion = ((startPiece == PAWN_WHITE) && (endIndex < 8));

                    //Determine whether to remove castling rights
                    boolean removedCastleBK = false;
                    boolean removedCastleBQ = false;
                    boolean removedCastleWK = false;
                    boolean removedCastleWQ = false;
                    if (startPiece == KING_WHITE){
                        removedCastleWK = canWhiteCastleK;
                        removedCastleWQ = canWhiteCastleQ;
                    } else if (startPiece == ROOK_WHITE){
                        if (startIndex == 56)
                            removedCastleWQ = canWhiteCastleQ;
                        if (startIndex == 63)
                            removedCastleWK = canWhiteCastleK;
                    }
                    if (endPiece == ROOK_BLACK){
                        if (endIndex == 0)
                            removedCastleBQ = canBlackCastleQ;
                        if (endIndex == 7)
                            removedCastleBK = canBlackCastleK;
                    }

                    //Remember en passant target square, so it can be restored when undoing move
                    boolean lastMoveEP = (enPassant != 0);
                    int targetEP = 0;
                    if (lastMoveEP){
                        targetEP = indexXLUT[getIndexFromBitmask(enPassant)];
                    }

                    //If promotion add every variation
                    if (!promotion)
                        validMoves.add(Move.toPackedInteger(startIndex, endIndex, startPiece, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                    else{
                        validMoves.add(Move.toPackedInteger(startIndex, endIndex, QUEEN_WHITE, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                        validMoves.add(Move.toPackedInteger(startIndex, endIndex, KNIGHT_WHITE, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                        validMoves.add(Move.toPackedInteger(startIndex, endIndex, ROOK_WHITE, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                        validMoves.add(Move.toPackedInteger(startIndex, endIndex, BISHOP_WHITE, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                    }
                }
            }
        } else {
            //Get indices for all active squares
            for (int startIndex : getIndicesFromBitmask(piecemaskb)){
                //Calculate piece info
                long startMask = getFieldMask(startIndex);
                int startPiece = getPieceType(startMask, false);

                //Get all valid moves for piece
                long moveMask = getValidMoves(startIndex, startPiece, false, startMask);
                //Get indices of all valid moves
                for (int endIndex : getIndicesFromBitmask(moveMask)){
                    //Calculate move info
                    long endMask = getFieldMask(endIndex);
                    int endPiece = getPieceType(endMask, true);
                    boolean castle = ((startPiece == KING_BLACK) && (startIndex == 4) && ((endIndex == 2) || (endIndex == 6)));
                    boolean isEnPassant = ((startPiece == PAWN_BLACK) && ((endMask & enPassant) != 0));
                    boolean promotion = ((startPiece == PAWN_BLACK) && (endIndex > 55));

                    //Determine whether to remove castling rights
                    boolean removedCastleBK = false;
                    boolean removedCastleBQ = false;
                    boolean removedCastleWK = false;
                    boolean removedCastleWQ = false;
                    if (startPiece == KING_BLACK){
                        removedCastleBK = canBlackCastleK;
                        removedCastleBQ = canBlackCastleQ;
                    } else if (startPiece == ROOK_BLACK){
                        if (startIndex == 0)
                            removedCastleBQ = canBlackCastleQ;
                        if (startIndex == 7)
                            removedCastleBK = canBlackCastleK;
                    }
                    if (endPiece == ROOK_WHITE){
                        if (endIndex == 56)
                            removedCastleWQ = canWhiteCastleQ;
                        if (endIndex == 63)
                            removedCastleWK = canWhiteCastleK;
                    }

                    //Remember en passant target square, so it can be restored when undoing move
                    boolean lastMoveEP = (enPassant != 0);
                    int targetEP = 0;
                    if (lastMoveEP){
                        targetEP = indexXLUT[getIndexFromBitmask(enPassant)];
                    }

                    //If promotion add every variation
                    if (!promotion)
                        validMoves.add(Move.toPackedInteger(startIndex, endIndex, startPiece, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                    else{
                        validMoves.add(Move.toPackedInteger(startIndex, endIndex, QUEEN_BLACK, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                        validMoves.add(Move.toPackedInteger(startIndex, endIndex, KNIGHT_BLACK, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                        validMoves.add(Move.toPackedInteger(startIndex, endIndex, ROOK_BLACK, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                        validMoves.add(Move.toPackedInteger(startIndex, endIndex, BISHOP_BLACK, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                    }
                }
            }
        }
        return validMoves;
    }

    /**
     * Computes a list of all valid capture moves for the current position.
     * @return List of all available captures
     */
    public List<Integer> getAllValidCaptures(){

        //Set initial capacity to average number of valid moves in a chess position
        List<Integer> validCaptures = new ArrayList<>(30);

        //Code is identical for both cases, but it's done like this to minimize setting of temporary variables
        //Very likely unnecessary, but might provide marginal speed up in bulk move generation
        if (isWhiteTurn){
            //Get indices for all active squares
            for (int startIndex : getIndicesFromBitmask(piecemaskw)){
                //Calculate piece info
                long startMask = getFieldMask(startIndex);
                int startPiece = getPieceType(startMask, true);

                //Get all valid captures for piece
                long moveMask = getValidMoves(startIndex, startPiece, true, startMask) & piecemaskb;
                //Get indices of all valid moves
                for (int endIndex : getIndicesFromBitmask(moveMask)){
                    //Calculate move info
                    long endMask = getFieldMask(endIndex);
                    int endPiece = getPieceType(endMask, false);
                    boolean castle = ((startPiece == KING_WHITE) && (startIndex == 60) && ((endIndex == 62) || (endIndex == 58)));
                    boolean isEnPassant = ((startPiece == PAWN_WHITE) && ((endMask & enPassant) != 0));
                    boolean promotion = ((startPiece == PAWN_WHITE) && (endIndex < 8));

                    //Determine whether to remove castling rights
                    boolean removedCastleBK = false;
                    boolean removedCastleBQ = false;
                    boolean removedCastleWK = false;
                    boolean removedCastleWQ = false;
                    if (startPiece == KING_WHITE){
                        removedCastleWK = canWhiteCastleK;
                        removedCastleWQ = canWhiteCastleQ;
                    } else if (startPiece == ROOK_WHITE){
                        if (startIndex == 56)
                            removedCastleWQ = canWhiteCastleQ;
                        if (startIndex == 63)
                            removedCastleWK = canWhiteCastleK;
                    }
                    if (endPiece == ROOK_BLACK){
                        if (endIndex == 0)
                            removedCastleBQ = canBlackCastleQ;
                        if (endIndex == 7)
                            removedCastleBK = canBlackCastleK;
                    }

                    //Remember en passant target square, so it can be restored when undoing move
                    boolean lastMoveEP = (enPassant != 0);
                    int targetEP = 0;
                    if (lastMoveEP){
                        targetEP = indexXLUT[getIndexFromBitmask(enPassant)];
                    }

                    //If promotion add every variation
                    if (!promotion)
                        validCaptures.add(Move.toPackedInteger(startIndex, endIndex, startPiece, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                    else{
                        validCaptures.add(Move.toPackedInteger(startIndex, endIndex, QUEEN_WHITE, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                        validCaptures.add(Move.toPackedInteger(startIndex, endIndex, KNIGHT_WHITE, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                        validCaptures.add(Move.toPackedInteger(startIndex, endIndex, ROOK_WHITE, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                        validCaptures.add(Move.toPackedInteger(startIndex, endIndex, BISHOP_WHITE, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                    }
                }
            }
        } else {
            //Get indices for all active squares
            for (int startIndex : getIndicesFromBitmask(piecemaskb)){
                //Calculate piece info
                long startMask = getFieldMask(startIndex);
                int startPiece = getPieceType(startMask, false);

                //Get all valid captures for piece
                long moveMask = getValidMoves(startIndex, startPiece, false, startMask) & piecemaskw;
                //Get indices of all valid moves
                for (int endIndex : getIndicesFromBitmask(moveMask)){
                    //Calculate move info
                    long endMask = getFieldMask(endIndex);
                    int endPiece = getPieceType(endMask, true);
                    boolean castle = ((startPiece == KING_BLACK) && (startIndex == 4) && ((endIndex == 2) || (endIndex == 6)));
                    boolean isEnPassant = ((startPiece == PAWN_BLACK) && ((endMask & enPassant) != 0));
                    boolean promotion = ((startPiece == PAWN_BLACK) && (endIndex > 55));

                    //Determine whether to remove castling rights
                    boolean removedCastleBK = false;
                    boolean removedCastleBQ = false;
                    boolean removedCastleWK = false;
                    boolean removedCastleWQ = false;
                    if (startPiece == KING_BLACK){
                        removedCastleBK = canBlackCastleK;
                        removedCastleBQ = canBlackCastleQ;
                    } else if (startPiece == ROOK_BLACK){
                        if (startIndex == 0)
                            removedCastleBQ = canBlackCastleQ;
                        if (startIndex == 7)
                            removedCastleBK = canBlackCastleK;
                    }
                    if (endPiece == ROOK_WHITE){
                        if (endIndex == 56)
                            removedCastleWQ = canWhiteCastleQ;
                        if (endIndex == 63)
                            removedCastleWK = canWhiteCastleK;
                    }

                    //Remember en passant target square, so it can be restored when undoing move
                    boolean lastMoveEP = (enPassant != 0);
                    int targetEP = 0;
                    if (lastMoveEP){
                        targetEP = indexXLUT[getIndexFromBitmask(enPassant)];
                    }

                    //If promotion add every variation
                    if (!promotion)
                        validCaptures.add(Move.toPackedInteger(startIndex, endIndex, startPiece, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                    else{
                        validCaptures.add(Move.toPackedInteger(startIndex, endIndex, QUEEN_BLACK, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                        validCaptures.add(Move.toPackedInteger(startIndex, endIndex, KNIGHT_BLACK, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                        validCaptures.add(Move.toPackedInteger(startIndex, endIndex, ROOK_BLACK, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                        validCaptures.add(Move.toPackedInteger(startIndex, endIndex, BISHOP_BLACK, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));
                    }
                }
            }
        }
        return validCaptures;
    }

    /**
     * Parses provided UCI move and converts it to internal packed int format.
     * Move validity is checked, and 0 is outputted if it's invalid.
     * Input string should still follow UCI formatting, even if the move can't be made on the board.
     * @param move UCI move to parse
     * @return Provided move in packed int format
     */
    public int parseUCIMove(String move){
        //Parse start and end square
        int startIndex = fieldToIndex(move.substring(0, 2));
        int endIndex = fieldToIndex(move.substring(2, 4));

        //Get bitmasks
        long startMask = getFieldMask(startIndex);
        long endMask = getFieldMask(endIndex);

        //Check for move validity
        if (startIndex == endIndex)
            return 0;
        if ((getValidMoves(startIndex) & endMask) == 0)
            return 0;

        //Get piece types
        int startPiece = getPieceType(startMask, isWhiteTurn);
        int endPiece = getPieceType(endMask, !(isWhiteTurn));

        boolean castle;
        boolean isEnPassant;
        boolean promotion;
        boolean removedCastleBK = false;
        boolean removedCastleBQ = false;
        boolean removedCastleWK = false;
        boolean removedCastleWQ = false;
        if (isWhiteTurn){
            //Calculate move info
            castle = ((startPiece == KING_WHITE) && (startIndex == 60) && ((endIndex == 62) || (endIndex == 58)));
            isEnPassant = ((startPiece == PAWN_WHITE) && ((endMask & enPassant) != 0));
            promotion = ((startPiece == PAWN_WHITE) && (endIndex < 8));

            //Whether to remove castling rights
            if (startPiece == KING_WHITE){
                removedCastleWK = canWhiteCastleK;
                removedCastleWQ = canWhiteCastleQ;
            } else if (startPiece == ROOK_WHITE){
                if (startIndex == 56)
                    removedCastleWQ = canWhiteCastleQ;
                if (startIndex == 63)
                    removedCastleWK = canWhiteCastleK;
            }
            if (endPiece == ROOK_BLACK){
                if (endIndex == 0)
                    removedCastleBQ = canBlackCastleQ;
                if (endIndex == 7)
                    removedCastleBK = canBlackCastleK;
            }

        } else {
            //Calculate move info
            castle = ((startPiece == KING_BLACK) && (startIndex == 4) && ((endIndex == 2) || (endIndex == 6)));
            isEnPassant = ((startPiece == PAWN_BLACK) && ((endMask & enPassant) != 0));
            promotion = ((startPiece == PAWN_BLACK) && (endIndex > 55));

            //Whether to remove castling rights
            if (startPiece == KING_BLACK){
                removedCastleBK = canBlackCastleK;
                removedCastleBQ = canBlackCastleQ;
            } else if (startPiece == ROOK_BLACK){
                if (startIndex == 0)
                    removedCastleBQ = canBlackCastleQ;
                if (startIndex == 7)
                    removedCastleBK = canBlackCastleK;
            }
            if (endPiece == ROOK_WHITE){
                if (endIndex == 56)
                    removedCastleWQ = canWhiteCastleQ;
                if (endIndex == 63)
                    removedCastleWK = canWhiteCastleK;
            }

        }

        //Store en passant, to be restored when undoing move
        boolean lastMoveEP = false;
        int targetEP = 0;
        if (enPassant != 0){
            lastMoveEP = true;
            targetEP = indexXLUT[getIndexFromBitmask(enPassant)];
        }

//        System.out.println("En passant" + enPassant);
//        System.out.println("Promotion" + promotion);
//        System.out.println("Castling" + castle);
//
//        Move.printMove(Move.toPackedInteger(startIndex, endIndex, startPiece, endPiece, castle, enPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP));


        //If the move is a promotion, parse the last character to get promotion piece
        if (!promotion)
            return Move.toPackedInteger(startIndex, endIndex, startPiece, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP);
        else {
            int promotionPiece = getPieceType(move.charAt(4));
            return Move.toPackedInteger(startIndex, endIndex, promotionPiece, endPiece, castle, isEnPassant, promotion, removedCastleBK, removedCastleBQ, removedCastleWK,removedCastleWQ, lastMoveEP, targetEP);
        }
    }

    /**
     * Returns the type of piece on the provided square.
     * Piece color is required, but computes output faster.
     * @param mask Bitmask of the square to check
     * @param isWhite Whether to check only white/black pieces
     * @return Piece type
     */
    private int getPieceType(long mask, boolean isWhite){
        if (isWhite){
            if ((mask & piecemaskw) == 0)
                return 15;
            if ((mask & pawnw) != 0)
                return PAWN_WHITE;
            if ((mask & bishopw) != 0)
                return BISHOP_WHITE;
            if ((mask & knightw) != 0)
                return KNIGHT_WHITE;
            if ((mask & queenw) != 0)
                return QUEEN_WHITE;
            if ((mask & rookw) != 0)
                return ROOK_WHITE;
            if ((mask & kingw) != 0)
                return KING_WHITE;
        } else {
            if ((mask & piecemaskb) == 0)
                return 15;
            if ((mask & pawnb) != 0)
                return PAWN_BLACK;
            if ((mask & bishopb) != 0)
                return BISHOP_BLACK;
            if ((mask & knightb) != 0)
                return KNIGHT_BLACK;
            if ((mask & queenb) != 0)
                return QUEEN_BLACK;
            if ((mask & rookb) != 0)
                return ROOK_BLACK;
            if ((mask & kingb) != 0)
                return KING_BLACK;
        }
        return 15;
    }

    /**
     * Returns the type of piece on the provided square.
     * Piece color isn't required, but computes output slower.
     * @param mask Bitmask of square to check
     * @return Piece type
     */
    private int getPieceType(long mask){
            if ((mask & pawnw) != 0)
                return PAWN_WHITE;
            if ((mask & bishopw) != 0)
                return BISHOP_WHITE;
            if ((mask & knightw) != 0)
                return KNIGHT_WHITE;
            if ((mask & queenw) != 0)
                return QUEEN_WHITE;
            if ((mask & rookw) != 0)
                return ROOK_WHITE;
            if ((mask & kingw) != 0)
                return KING_WHITE;
            if ((mask & pawnb) != 0)
                return PAWN_BLACK;
            if ((mask & bishopb) != 0)
                return BISHOP_BLACK;
            if ((mask & knightb) != 0)
                return KNIGHT_BLACK;
            if ((mask & queenb) != 0)
                return QUEEN_BLACK;
            if ((mask & rookb) != 0)
                return ROOK_BLACK;
            if ((mask & kingb) != 0)
                return KING_BLACK;
        return 15;
    }

    /**
     * Converts char representation of piece type to int.
     * @param type Piece type character
     * @return Piece type integer
     */
    private int getPieceType(char type){
        int result = -1;
        switch (type){
            case 'K' -> result = KING_WHITE;
            case 'B' -> result = BISHOP_WHITE;
            case 'Q' -> result = QUEEN_WHITE;
            case 'R' -> result = ROOK_WHITE;
            case 'N' -> result = KNIGHT_WHITE;
            case 'P' -> result = PAWN_WHITE;
            case 'k' -> result = KING_BLACK;
            case 'b' -> result = BISHOP_BLACK;
            case 'q' -> result = QUEEN_BLACK;
            case 'r' -> result = ROOK_BLACK;
            case 'n' -> result = KNIGHT_BLACK;
            case 'p' -> result = PAWN_BLACK;
        }
        return result;
    }

    /**
     * Converts piece type integer to character representation.
     * @param type Piece type integer
     * @return Piece type character
     */
    private char getCharPieceType(int type){
        char result = '0';
        switch (type){
            case KING_WHITE -> result = 'K';
            case BISHOP_WHITE -> result = 'B';
            case QUEEN_WHITE -> result = 'Q';
            case ROOK_WHITE -> result = 'R';
            case KNIGHT_WHITE -> result = 'N';
            case PAWN_WHITE -> result = 'P';
            case KING_BLACK -> result = 'k';
            case BISHOP_BLACK -> result = 'b';
            case QUEEN_BLACK -> result = 'q';
            case ROOK_BLACK -> result = 'r';
            case KNIGHT_BLACK -> result = 'n';
            case PAWN_BLACK -> result = 'p';
        }
        return result;
    }

    /**
     * Returns a bitmask of all squares under attack by the piece on the provided square.
     * @param index Starting index
     * @return Bitmask of squares under attack
     */
    public long getValidAttack(int index){
        long fieldMask = getFieldMask(index);
        int pieceType = getPieceType(fieldMask);
        boolean isWhite = pieceType < 6;
        long num = 0;
        switch (pieceType){
            case KING_WHITE, KING_BLACK -> num = getKingAttack(index, isWhite);
            case QUEEN_WHITE, QUEEN_BLACK -> num = getQueenAttack(index, indexXLUT[index], indexYLUT[index], isWhite);
            case ROOK_WHITE, ROOK_BLACK -> num = getRookAttack(index, indexXLUT[index], indexYLUT[index], isWhite, true);
            case BISHOP_WHITE, BISHOP_BLACK -> num = getBishopAttack(index, indexXLUT[index], indexYLUT[index], isWhite, true);
            case KNIGHT_WHITE, KNIGHT_BLACK -> num = getKnightAttack(index, isWhite);
            case PAWN_WHITE, PAWN_BLACK -> num = getPawnAttack(index, isWhite);
        }
        return num;
    }

    /**
     * Checks whether the provided UCI move is a promotion.
     * The move can be invalid, but should still follow UCI formatting.
     * @param move UCI move to check
     * @return Whether the move is a promotion
     */
    public boolean isMovePromotion(String move){

        //Get fields
        int start = fieldToIndex(move);
        int end = fieldToIndex(move.substring(2));

        //Check move validity
        if (start == end)
            return false;
        if ((getValidMoves(start) & getFieldMask(end)) == 0)
            return false;

        int startPiece = getPieceType(getFieldMask(start));

        boolean promotion = false;

        //Check if the pawn is on the last file
        if (isWhiteTurn){
            promotion = ((startPiece == PAWN_WHITE) && (end < 8));
        } else {
            promotion = ((startPiece == PAWN_BLACK) && (end > 55));
        }
        return promotion;
    }

    /**
     * Updates the board bitmasks with a regular move.
     * @param startPiece Starting piece
     * @param endPiece Piece on target square
     * @param startIndex Starting square index
     * @param endIndex Target square index
     */
    private void updateBitMasks(int startPiece, int endPiece, int startIndex, int endIndex){
        long startMask = getFieldMask(startIndex);
        long endMask = getFieldMask(endIndex);

        switch (startPiece){
            case KING_WHITE -> {
                kingw = endMask;

                piecemaskw |= endMask;
                piecemaskw &= (~startMask);
            }
            case KING_BLACK -> {
                kingb = endMask;

                piecemaskb |= endMask;
                piecemaskb &= (~startMask);
            }
            case QUEEN_WHITE -> {
                queenw |= endMask;
                queenw = queenw & (~startMask);

                piecemaskw |= endMask;
                piecemaskw &= (~startMask);
            }
            case QUEEN_BLACK -> {
                queenb |= endMask;
                queenb = queenb & (~startMask);

                piecemaskb |= endMask;
                piecemaskb &= (~startMask);
            }
            case BISHOP_WHITE -> {
                bishopw |= endMask;
                bishopw = bishopw & (~startMask);

                piecemaskw |= endMask;
                piecemaskw &= (~startMask);
            }
            case BISHOP_BLACK -> {
                bishopb |= endMask;
                bishopb = bishopb & (~startMask);

                piecemaskb |= endMask;
                piecemaskb &= (~startMask);
            }
            case PAWN_WHITE -> {
                pawnw |= endMask;
                pawnw = pawnw & (~startMask);

                piecemaskw |= endMask;
                piecemaskw &= (~startMask);
            }
            case PAWN_BLACK -> {
                pawnb |= endMask;
                pawnb = pawnb & (~startMask);

                piecemaskb |= endMask;
                piecemaskb &= (~startMask);
            }
            case KNIGHT_WHITE -> {
                knightw |= endMask;
                knightw = knightw & (~startMask);

                piecemaskw |= endMask;
                piecemaskw &= (~startMask);
            }
            case KNIGHT_BLACK -> {
                knightb |= endMask;
                knightb = knightb & (~startMask);

                piecemaskb |= endMask;
                piecemaskb &= (~startMask);
            }
            case ROOK_WHITE -> {
                rookw |= endMask;
                rookw = rookw & (~startMask);

                piecemaskw |= endMask;
                piecemaskw &= (~startMask);
            }
            case ROOK_BLACK -> {
                rookb |= endMask;
                rookb = rookb & (~startMask);

                piecemaskb |= endMask;
                piecemaskb &= (~startMask);
            }
        }

        switch (endPiece){
            case QUEEN_WHITE -> {
                queenw = queenw & (~endMask);
                piecemaskw &= (~endMask);
            }
            case QUEEN_BLACK -> {
                queenb = queenb & (~endMask);
                piecemaskb &= (~endMask);
            }
            case BISHOP_WHITE -> {
                bishopw = bishopw & (~endMask);
                piecemaskw &= (~endMask);
            }
            case BISHOP_BLACK -> {
                bishopb = bishopb & (~endMask);
                piecemaskb &= (~endMask);
            }
            case PAWN_WHITE -> {
                pawnw = pawnw & (~endMask);
                piecemaskw &= (~endMask);
            }
            case PAWN_BLACK -> {
                pawnb = pawnb & (~endMask);
                piecemaskb &= (~endMask);
            }
            case KNIGHT_WHITE -> {
                knightw = knightw & (~endMask);
                piecemaskw &= (~endMask);
            }
            case KNIGHT_BLACK -> {
                knightb = knightb & (~endMask);
                piecemaskb &= (~endMask);
            }
            case ROOK_WHITE -> {
                rookw = rookw & (~endMask);
                piecemaskw &= (~endMask);
            }
            case ROOK_BLACK -> {
                rookb = rookb & (~endMask);
                piecemaskb &= (~endMask);
            }
        }
    }

    /**
     * Updates the board bitmasks by undoing a regular move.
     * @param startPiece Starting piece
     * @param endPiece Piece on target square
     * @param startIndex Starting square index
     * @param endIndex Target square index
     */
    private void undoBitMasks(int startPiece, int endPiece, int startIndex, int endIndex){
        long startMask = getFieldMask(startIndex);
        long endMask = getFieldMask(endIndex);

        switch (startPiece){
            case KING_WHITE -> {
                kingw = startMask;

                piecemaskw &= (~endMask);
                piecemaskw |= startMask;
            }
            case KING_BLACK -> {
                kingb = startMask;

                piecemaskb &= (~endMask);
                piecemaskb |= startMask;
            }
            case QUEEN_WHITE -> {
                queenw &= (~endMask);
                queenw |= startMask;

                piecemaskw &= (~endMask);
                piecemaskw |= startMask;
            }
            case QUEEN_BLACK -> {
                queenb &= (~endMask);
                queenb |= startMask;

                piecemaskb &= (~endMask);
                piecemaskb |= startMask;
            }
            case BISHOP_WHITE -> {
                bishopw &= (~endMask);
                bishopw |= startMask;

                piecemaskw &= (~endMask);
                piecemaskw |= startMask;
            }
            case BISHOP_BLACK -> {
                bishopb &= (~endMask);
                bishopb |= startMask;

                piecemaskb &= (~endMask);
                piecemaskb |= startMask;
            }
            case PAWN_WHITE -> {
                pawnw &= (~endMask);
                pawnw |= startMask;

                piecemaskw &= (~endMask);
                piecemaskw |= startMask;
            }
            case PAWN_BLACK -> {
                pawnb &= (~endMask);
                pawnb |= startMask;

                piecemaskb &= (~endMask);
                piecemaskb |= startMask;
            }
            case KNIGHT_WHITE -> {
                knightw &= (~endMask);
                knightw |= startMask;

                piecemaskw &= (~endMask);
                piecemaskw |= startMask;
            }
            case KNIGHT_BLACK -> {
                knightb &= (~endMask);
                knightb |= startMask;

                piecemaskb &= (~endMask);
                piecemaskb |= startMask;
            }
            case ROOK_WHITE -> {
                rookw &= (~endMask);
                rookw |= startMask;

                piecemaskw &= (~endMask);
                piecemaskw |= startMask;
            }
            case ROOK_BLACK -> {
                rookb &= (~endMask);
                rookb |= startMask;

                piecemaskb &= (~endMask);
                piecemaskb |= startMask;
            }
        }

        switch (endPiece){
            case QUEEN_WHITE -> {
                queenw |= endMask;
                piecemaskw |= endMask;
            }
            case QUEEN_BLACK -> {
                queenb |= endMask;
                piecemaskb |= endMask;
            }
            case BISHOP_WHITE -> {
                bishopw |= endMask;
                piecemaskw |= endMask;
            }
            case BISHOP_BLACK -> {
                bishopb |= endMask;
                piecemaskb |= endMask;
            }
            case PAWN_WHITE -> {
                pawnw |= endMask;
                piecemaskw |= endMask;
            }
            case PAWN_BLACK -> {
                pawnb |= endMask;
                piecemaskb |= endMask;
            }
            case KNIGHT_WHITE -> {
                knightw |= endMask;
                piecemaskw |= endMask;
            }
            case KNIGHT_BLACK -> {
                knightb |= endMask;
                piecemaskb |= endMask;
            }
            case ROOK_WHITE -> {
                rookw |= endMask;
                piecemaskw |= endMask;
            }
            case ROOK_BLACK -> {
                rookb |= endMask;
                piecemaskb |= endMask;
            }
        }
    }

    /**
     * Updates the board bitmasks with an en passant move.
     * @param isWhite Whether the starting pawn is white
     * @param startIndex Starting square index
     * @param endIndex Ending square index
     */
    private void updateBitMasksEP (boolean isWhite, int startIndex, int endIndex){
        long startMask = getFieldMask(startIndex);
        long endMask = getFieldMask(endIndex);
        if (isWhite){
            long targetMask = getFieldMask(endIndex + 8);
            pawnw &= (~startMask);
            pawnw |= endMask;
            pawnb &= (~targetMask);

            piecemaskw &= (~startMask);
            piecemaskw |= endMask;
            piecemaskb &= (~targetMask);
        } else {
            long targetMask = getFieldMask(endIndex - 8);
            pawnb &= (~startMask);
            pawnb |= endMask;
            pawnw &= (~targetMask);

            piecemaskb &= (~startMask);
            piecemaskb |= endMask;
            piecemaskw &= (~targetMask);
        }
    }

    /**
     * Updates the board bitmasks by undoing an en passant move.
     * @param isWhite Whether the starting pawn is white
     * @param startIndex Starting square index
     * @param endIndex Ending square index
     */
    private void undoBitMasksEP (boolean isWhite, int startIndex, int endIndex){
        long startMask = getFieldMask(startIndex);
        long endMask = getFieldMask(endIndex);
        if (isWhite){
            long targetMask = getFieldMask(endIndex + 8);
            pawnw &= (~endMask);
            pawnw |= startMask;
            pawnb |= targetMask;

            piecemaskw &= (~endMask);
            piecemaskw |= startMask;
            piecemaskb |= targetMask;
        } else {
            long targetMask = getFieldMask(endIndex - 8);
            pawnb &= (~endMask);
            pawnb |= startMask;
            pawnw |= targetMask;

            piecemaskb &= (~endMask);
            piecemaskb |= startMask;
            piecemaskw |= targetMask;
        }
    }

    /**
     * Updates the board bitmasks with a castling move.
     * @param isWhite Whether white is moving
     * @param kingside True - kingside, False - queenside
     */
    private void updateBitMasksCastle (boolean isWhite, boolean kingside){
        if (isWhite){
            if (kingside){
                kingw = 0x2L;
                rookw &= 0xFFFFFFFFFFFFFFFEL;
                rookw |= 0x4L;

                piecemaskw &= 0xFFFFFFFFFFFFFFF0L;
                piecemaskw |= 0x6L;
            } else {
                kingw = 0x20L;
                rookw &= 0xFFFFFFFFFFFFFF7FL;
                rookw |= 0x10L;

                piecemaskw &= 0xFFFFFFFFFFFFFF07L;
                piecemaskw |= 0x30L;
            }
        } else {
            if (kingside){
                kingb = 0x0200000000000000L;
                rookb &= 0xFEFFFFFFFFFFFFFFL;
                rookb |= 0x0400000000000000L;

                piecemaskb &= 0xF0FFFFFFFFFFFFFFL;
                piecemaskb |= 0x0600000000000000L;
            } else {
                kingb = 0x2000000000000000L;
                rookb &= 0x7FFFFFFFFFFFFFFFL;
                rookb |= 0x1000000000000000L;

                piecemaskb &= 0x07FFFFFFFFFFFFFFL;
                piecemaskb |= 0x3000000000000000L;
            }
        }
    }

    /**
     * Updates the board bitmasks by undoing a castling move.
     * @param isWhite Whether white is moving
     * @param kingside True - kingside, False - queenside
     */
    private void undoBitMasksCastle (boolean isWhite, boolean kingside){
        if (isWhite){
            kingw = 0x8L;
            if (kingside){
                rookw = rookw & 0xFFFFFFFFFFFFFFFBL;
                rookw |= 0x1L;

                piecemaskw &= 0xFFFFFFFFFFFFFFF0L;
                piecemaskw |= 0x9L;
            } else {
                rookw = rookw & 0xFFFFFFFFFFFFFFEFL;
                rookw |= 0x80L;

                piecemaskw &= 0xFFFFFFFFFFFFFF07L;
                piecemaskw |= 0x88L;
            }
        } else {
            kingb = 0x0800000000000000L;
            if (kingside){
                rookb = rookb & 0xFBFFFFFFFFFFFFFFL;
                rookb |= 0x0100000000000000L;

                piecemaskb &= 0xF0FFFFFFFFFFFFFFL;
                piecemaskb |= 0x0900000000000000L;
            } else {
                rookb = rookb & 0xEFFFFFFFFFFFFFFFL;
                rookb |= 0x8000000000000000L;

                piecemaskb &= 0x0FFFFFFFFFFFFFFFL;
                piecemaskb |= 0x8800000000000000L;
            }
        }
    }

    /**
     * Updates the board bitmasks with a promotion move.
     * @param isWhite Is piece white
     * @param startIndex Index of starting square
     * @param endIndex Index of target square
     * @param endPiece Target piece type
     * @param promotionPiece Promotion piece type
     */
    private void updateBitMasksPromote (boolean isWhite, int startIndex, int endIndex, int endPiece, int promotionPiece){
        long startMask = getFieldMask(startIndex);
        long endMask = getFieldMask(endIndex);
        if (isWhite){
            pawnw &= (~startMask);
            piecemaskw |= endMask;
            piecemaskw &= (~startMask);
            switch (promotionPiece){
                case QUEEN_WHITE -> queenw |= endMask;
                case ROOK_WHITE -> rookw |= endMask;
                case BISHOP_WHITE -> bishopw |= endMask;
                case KNIGHT_WHITE -> knightw |= endMask;
            }
            switch (endPiece){
                case QUEEN_BLACK -> {
                    queenb &= (~endMask);
                    piecemaskb &= (~endMask);
                }
                case ROOK_BLACK -> {
                    rookb &= (~endMask);
                    piecemaskb &= (~endMask);
                }
                case BISHOP_BLACK -> {
                    bishopb &= (~endMask);
                    piecemaskb &= (~endMask);
                }
                case KNIGHT_BLACK -> {
                    knightb &= (~endMask);
                    piecemaskb &= (~endMask);
                }
                case PAWN_BLACK -> {
                    pawnb &= (~endMask);
                    piecemaskb &= (~endMask);
                }
            }
        } else {
            pawnb = pawnb & (~startMask);
            piecemaskb |= endMask;
            piecemaskb &= (~startMask);
            switch (promotionPiece){
                case QUEEN_BLACK -> queenb |= endMask;
                case ROOK_BLACK -> rookb |= endMask;
                case BISHOP_BLACK -> bishopb |= endMask;
                case KNIGHT_BLACK -> knightb |= endMask;
            }
            switch (endPiece){
                case QUEEN_WHITE -> {
                    queenw &= (~endMask);
                    piecemaskw &= (~endMask);
                }
                case ROOK_WHITE -> {
                    rookw &= (~endMask);
                    piecemaskw &= (~endMask);
                }
                case BISHOP_WHITE -> {
                    bishopw &= (~endMask);
                    piecemaskw &= (~endMask);
                }
                case KNIGHT_WHITE -> {
                    knightw &= (~endMask);
                    piecemaskw &= (~endMask);
                }
                case PAWN_WHITE -> {
                    pawnw &= (~endMask);
                    piecemaskw &= (~endMask);
                }
            }
        }
    }

    /**
     * Updates the board bitmasks by undoing a promotion move.
     * @param isWhite Is piece white
     * @param startIndex Index of starting square
     * @param endIndex Index of target square
     * @param endPiece Target piece type
     * @param promotionPiece Promotion piece type
     */
    private void undoBitMasksPromote (boolean isWhite, int startIndex, int endIndex, int endPiece, int promotionPiece){
        long startMask = getFieldMask(startIndex);
        long endMask = getFieldMask(endIndex);
        if (isWhite){
            pawnw &= (~endMask);
            pawnw |= startMask;
            piecemaskw &= (~endMask);
            piecemaskw |= startMask;
            switch (promotionPiece){
                case QUEEN_WHITE -> queenw &= (~endMask);
                case ROOK_WHITE -> rookw &= (~endMask);
                case BISHOP_WHITE -> bishopw &= (~endMask);
                case KNIGHT_WHITE -> knightw &= (~endMask);
            }
            switch (endPiece){
                case QUEEN_BLACK -> {
                    queenb |= endMask;
                    piecemaskb |= endMask;
                }
                case ROOK_BLACK -> {
                    rookb |= endMask;
                    piecemaskb |= endMask;
                }
                case BISHOP_BLACK -> {
                    bishopb |= endMask;
                    piecemaskb |= endMask;
                }
                case KNIGHT_BLACK -> {
                    knightb |= endMask;
                    piecemaskb |= endMask;
                }
                case PAWN_BLACK -> {
                    pawnb |= endMask;
                    piecemaskb |= endMask;
                }
            }
        } else {
            pawnb = pawnb & (~endMask);
            pawnb |= startMask;
            piecemaskb &= (~endMask);
            piecemaskb |= startMask;
            switch (promotionPiece){
                case QUEEN_BLACK -> queenb &= (~endMask);
                case ROOK_BLACK -> rookb &= (~endMask);
                case BISHOP_BLACK -> bishopb &= (~endMask);
                case KNIGHT_BLACK -> knightb &= (~endMask);
            }
            switch (endPiece){
                case QUEEN_WHITE -> {
                    queenw |= endMask;
                    piecemaskw |= endMask;
                }
                case ROOK_WHITE -> {
                    rookw |= endMask;
                    piecemaskw |= endMask;
                }
                case BISHOP_WHITE -> {
                    bishopw |= endMask;
                    piecemaskw |= endMask;
                }
                case KNIGHT_WHITE -> {
                    knightw |= endMask;
                    piecemaskw |= endMask;
                }
                case PAWN_WHITE -> {
                    pawnw |= endMask;
                    piecemaskw |= endMask;
                }
            }
        }
    }

    /**
     * Builds the white, black and global piecemasks from individual masks.
     */
    private void updatePiecemasks (){
        piecemaskw = pawnw | kingw | queenw | bishopw | rookw | knightw;
        piecemaskb = pawnb | kingb | queenb | bishopb | rookb | knightb;
        piecemask = piecemaskb | piecemaskw;
    }

    /**
     * Computes the attack mask of the selected side. Relatively expensive operation.
     * @param white Side to update
     */
    private void updateAttackMasks (boolean white){
        if (white){
            whiteAttack = 0;
            List<Integer> activeFields = getIndicesFromBitmask(piecemaskw);
            for (int field : activeFields) {
                whiteAttack |= getValidAttack(field);
            }
        } else {
            blackAttack = 0;
            List<Integer> activeFields = getIndicesFromBitmask(piecemaskb);
            for (int field : activeFields) {
                blackAttack |= getValidAttack(field);
            }
            activeFields.clear();
        }
    }

    /**
     * Creates new board from the provided FEN string.
     * The new board is set as current and old is discarded.
     * The FEN string is unchecked and should be valid.
     * @param fen FEN string to use
     */
    public void interpretFEN (String fen) {
        resetBoard();
        String[] strings = fen.split(" ");
        String[] boardString = strings[0].split("/");
        for(int i = 0; i < 8; i++){
            int j = 0;
            int col = 0;
            while (j < boardString[i].length()){
                char c = boardString[i].charAt(j);
                long num = 0b1000000000000000000000000000000000000000000000000000000000000000L;
                num = num >>> (col + i * 8);
                switch (c){
                    case 'k': {
                        kingb = kingb | num;
                        col++;
                    } break;
                    case 'K':{
                        kingw = kingw | num;
                        col++;
                    } break;
                    case 'q':{
                        queenb = queenb | num;
                        col++;
                    } break;
                    case 'Q':{
                        queenw = queenw | num;
                        col++;
                    } break;
                    case 'r':{
                        rookb = rookb | num;
                        col++;
                    } break;
                    case 'R':{
                        rookw = rookw | num;
                        col++;
                    } break;
                    case 'n':{
                        knightb = knightb | num;
                        col++;
                    } break;
                    case 'N':{
                        knightw = knightw | num;
                        col++;
                    } break;
                    case 'b':{
                        bishopb = bishopb | num;
                        col++;
                    } break;
                    case 'B':{
                        bishopw = bishopw | num;
                        col++;
                    } break;
                    case 'p':{
                        pawnb = pawnb | num;
                        col++;
                    } break;
                    case 'P': {
                        pawnw = pawnw | num;
                        col++;
                    } break;
                    case '1':col += 1;break;
                    case '2':col += 2;break;
                    case '3':col += 3;break;
                    case '4':col += 4;break;
                    case '5':col += 5;break;
                    case '6':col += 6;break;
                    case '7':col += 7;break;
                    case '8':col += 8;break;
                }
                j++;
            }
        }
        updatePiecemasks();

        if (strings[1].equals("b"))
            isWhiteTurn = false;
        if (!(strings[2].equals("-"))){
            canBlackCastleK = strings[2].contains("k");
            canWhiteCastleK = strings[2].contains("K");
            canBlackCastleQ = strings[2].contains("q");
            canWhiteCastleQ = strings[2].contains("Q");
        } else {
            canBlackCastleK = false;
            canWhiteCastleK = false;
            canBlackCastleQ = false;
            canWhiteCastleQ = false;
        }

        if (!(strings[3].equals("-"))){
            enPassant = getFieldMask(fieldToIndex(strings[3]));
        } else {
            enPassant = 0;
        }

        updateAttackMasks(true);
        updateAttackMasks(false);
        inCheck(isWhiteTurn);
        checkForPinsAndCheck(!isWhiteTurn);
        checkForPinsAndCheck(isWhiteTurn);
        updateGUIBoard();
    }

    private void resetBoard(){
        pawnb = 0;
        pawnw = 0;
        kingb = 0;
        kingw = 0;
        queenb = 0;
        queenw = 0;
        rookb = 0;
        rookw = 0;
        bishopb = 0;
        bishopw = 0;
        knightb = 0;
        knightw = 0;
        piecemask = 0;
        piecemaskw = 0;
        piecemaskb = 0;
        whiteAttack = 0;
        blackAttack = 0;
        enPassant = 0;
        isWhiteTurn = true;
        isBlackInCheck = false;
        isWhiteInCheck = false;
        whiteChecks = new long[2];
        blackChecks = new long[2];
        whitePins = new long[8];
        blackPins = new long[8];
        canWhiteCastleQ = true;
        canWhiteCastleK = true;
        canBlackCastleQ = true;
        canBlackCastleK = true;
    }

    private void printMasks(){
//        System.out.println("Pieces:");
//        printLong(piecemask);
//        System.out.println("White pieces:");
//        printLong(piecemaskw);
//        System.out.println("Black pieces:");
//        printLong(piecemask);
//        System.out.println("White attack:");
//        printLong(whiteAttack);
//        System.out.println("Black attack:");
//        printLong(blackAttack);
//        System.out.println("Black king:");
//        printLong(kingb);
//        System.out.println("White king:");
//        printLong(kingw);
//        System.out.println("Black queen:");
//        printLong(queenb);
//        System.out.println("White queen:");
//        printLong(queenw);
//        System.out.println("Black rooks:");
//        printLong(rookb);
//        System.out.println("White rooks:");
//        printLong(rookw);
//        System.out.println("Black bishops:");
//        printLong(bishopb);
//        System.out.println("White bishops:");
//        printLong(bishopw);
//        System.out.println("Black knights:");
//        printLong(knightb);
//        System.out.println("White knights:");
//        printLong(knightw);
    }

    /**
     * Prints a long, formated as a board bitmask.
     * @param l Bitmask to print
     */
    protected void printLong (long l){
        String print = String.format("%064d", new BigInteger(Long.toBinaryString(l)));
        StringBuilder sb = new StringBuilder(print);
        sb.insert(8, "\n");
        sb.insert(8*2+1, "\n");
        sb.insert(8*3+2, "\n");
        sb.insert(8*4+3, "\n");
        sb.insert(8*5+4, "\n");
        sb.insert(8*6+5, "\n");
        sb.insert(8*7+6, "\n");
        System.out.println(sb.toString());
    }

    /**
     * Prints a byte, formated as a bitmask.
     * @param b Bitmask to print
     */
    private void printByte(byte b){
            for (int i = 7; i >= 0; i--) {
                int bit = (b >>> i) & 1;
                System.out.print(bit);
            }
            System.out.println();
    }

    /**
     * Returns board index of provided field. Board is indexed from top left to bottom right.
     * Field string isn't checked and should be valid.
     * @param field Field name
     * @return Field index
     */
    public int fieldToIndex(String field){
        char c = field.charAt(0);
        int j = 8 - Integer.parseInt(String.valueOf(field.charAt(1)));
        int i = c - 'a';
        return i + j * 8;
    }


    /**
     * Returns a bitmask of the field at the provided index.
     * @param field Field to mask
     * @return Bitmask
     */
    public long getFieldMask (Int2 field){
        if (field.x >= 0 && field.x < 8 && field.y >= 0 && field.y < 8)
            return fieldMasksLUT[field.x + field.y * 8];
        return 0;
    }

    /**
     * Returns a bitmask of the provided index.
     * @param index Square index
     * @return Bitmask
     */
    public long getFieldMask(int index){
        return fieldMasksLUT[index];
    }

    /**
     * Precomputes masks for all fields.
     */
    private void precomputeFieldMasks(){
        for (int i = 0; i < 64; i++){
            fieldMasksLUT[i] = 0b1000000000000000000000000000000000000000000000000000000000000000L >>> i;
        }
    }

    /**
     * Returns a bitmask of all valid moves a king can make from the provided index.
     * @param index
     * @param isWhite
     * @return
     */
    private long getKingMove (int index, boolean isWhite){
        updateAttackMasks(!isWhite);
        if (isWhite) {
            if (!(canWhiteCastleK || canWhiteCastleQ)) {
                return ((kingmoveLUT[index] & (~piecemaskw)) & (~blackAttack));
            } else {
                long castle = 0;
                if (canWhiteCastleK && ((blackAttack & 0xEL) == 0) && ((piecemask & 0x6L) == 0)) {
                    castle |= 0x2L;
                }
                if (canWhiteCastleQ && ((blackAttack & 0x38L) == 0) && ((piecemask & 0x70L) == 0)) {
                    castle |= 0x20L;
                }
                return ((kingmoveLUT[index] & (~piecemaskw)) & (~blackAttack)) | castle;
            }
        } else {
            if (!(canBlackCastleK || canBlackCastleQ)) {
                return ((kingmoveLUT[index] & (~piecemaskb)) & (~whiteAttack));
            } else {
                long castle = 0;
                if (canBlackCastleK && ((whiteAttack & 0x0E00000000000000L) == 0) && ((piecemask & 0x0600000000000000L) == 0)) {
                    castle |= 0x0200000000000000L;
                }
                if (canBlackCastleQ && ((whiteAttack & 0x3800000000000000L) == 0) && ((piecemask & 0x7000000000000000L) == 0)) {
                    castle |= 0x2000000000000000L;
                }
                return ((kingmoveLUT[index] & (~piecemaskb)) & (~whiteAttack)) | castle;
            }
        }
    }

    private long getRookMove (int index, int x, int y, boolean isWhite){
        long f;
        long e;
        if (isWhite){
            f = piecemaskw;
            e = piecemaskb;
        } else {
            f = piecemaskb;
            e = piecemaskw;
        }
        byte colMask = (byte) (getByteMask(y, getColumn(x, f), true) | getByteMask(y, getColumn(x, e), false));
        byte rowMask = (byte) (getByteMask(x, getRow(y, f), true) | getByteMask(x, getRow(y, e), false));
        long mask = ((rowMask & 0xFFL) << ((7 - y) * 8)) | getColumnFromByte(colMask, x);
        return rookmoveLUT[index] & (~mask);
    }

    private long getBishopMove (int index, int x, int y, boolean isWhite){
        long f;
        long e;
        if (isWhite){
            f = piecemaskw;
            e = piecemaskb;
        } else {
            f = piecemaskb;
            e = piecemaskw;
        }
        byte leftMask = (byte) (getByteMask(y, getDiagonal(index, e, true) , false) | getByteMask(y,getDiagonal(index, f, true) , true));
        byte rightMask = (byte) (getByteMask(y, getDiagonal(index, e, false) , false) | getByteMask(y, getDiagonal(index, f, false) , true));
        long mask = diagonalMask(leftMask, rightMask, index, x, y);
        return bishopmoveLUT[index] & (~mask);
    }

    private long getQueenMove (int index, int x, int y, boolean isWhite){
        return getBishopMove(index, x, y, isWhite) | getRookMove(index, x, y, isWhite);
    }

    private long getKnightMove (int index, boolean isWhite){
        if (isWhite)
            return knightmoveLUT[index] & (~piecemaskw);
        else
            return knightmoveLUT[index] & (~piecemaskb);
    }

    private long getPawnMove (int index, int x, int y, boolean isWhite){
        byte colMask = getByteMask(y, getColumn(x, piecemask), true);
        long mask = getColumnFromByte(colMask, x);
        if (isWhite) {
            //If possible en passant
            if ((pawnattackWLUT[index] & enPassant) != 0){
                long targetSquare = enPassant >>> 8;
                int targetIndex = getIndexFromBitmask(targetSquare);
                if ((0x000000FF00000000L & (queenb | rookb)) != 0 && (0x000000FF00000000L & kingw) != 0){
                    if (((getSpecificAttack(index, x ,y , AttackType.ROW) & (kingw | queenb | rookb)) != 0) && ((getSpecificAttack(targetIndex, indexXLUT[targetIndex] , indexYLUT[targetIndex], AttackType.ROW) & (queenb | rookb | kingw)) != 0)){
                        return (pawnmoveWLUT[index] & (~mask)) | (pawnattackWLUT[index] & (piecemaskb));
                    }
                }

                long maskLeft = getSpecificAttack(targetIndex, indexXLUT[targetIndex], indexYLUT[targetIndex], AttackType.DIAGONAL_LEFT);
                long maskRight = getSpecificAttack(targetIndex, indexXLUT[targetIndex], indexYLUT[targetIndex], AttackType.DIAGONAL_RIGHT);
                if (((maskLeft & (bishopb | queenb)) != 0 && (maskLeft & kingw) != 0) || ((maskRight & (bishopb | queenb)) != 0 && (maskRight & kingw) != 0)){
                    return (pawnmoveWLUT[index] & (~mask)) | (pawnattackWLUT[index] & (piecemaskb));
                }
            }
            return (pawnmoveWLUT[index] & (~mask)) | (pawnattackWLUT[index] & (piecemaskb | enPassant));
        } else {
            //If possible en passant
            if ((pawnattackBLUT[index] & enPassant) != 0){
                long targetSquare = enPassant << 8;
                int targetIndex = getIndexFromBitmask(targetSquare);
                if ((0x00000000FF000000L & (queenw | rookw )) != 0 && (0x00000000FF000000L & kingb) != 0){
                    if (((getSpecificAttack(index, x ,y , AttackType.ROW) & (kingb | queenw | rookw)) != 0) && ((getSpecificAttack(targetIndex, indexXLUT[targetIndex] , indexYLUT[targetIndex], AttackType.ROW) & (queenw | rookw | kingb)) != 0)){
                        return (pawnmoveBLUT[index] & (~mask)) | (pawnattackBLUT[index] & (piecemaskw));
                    }
                }

                long maskLeft = getSpecificAttack(targetIndex, indexXLUT[targetIndex], indexYLUT[targetIndex], AttackType.DIAGONAL_LEFT);
                long maskRight = getSpecificAttack(targetIndex, indexXLUT[targetIndex], indexYLUT[targetIndex], AttackType.DIAGONAL_RIGHT);
                if (((maskLeft & (bishopw | queenw)) != 0 && (maskLeft & kingb) != 0) || ((maskRight & (bishopw | queenw)) != 0 && (maskRight & kingb) != 0)){
                    return (pawnmoveBLUT[index] & (~mask)) | (pawnattackBLUT[index] & (piecemaskw));
                }

            }
            return (pawnmoveBLUT[index] & (~mask)) | (pawnattackBLUT[index] & (piecemaskw | enPassant));
        }
    }

    protected long getRookAttack(int index, int x, int y, boolean isWhite, boolean ignoreKing){
        long kingMask = 0;
        if (isWhite && ignoreKing)
            kingMask = kingb;
        else if (ignoreKing)
            kingMask = kingw;
        byte colMask = getByteMask(y, getColumn(x, piecemask & (~kingMask)), false);
        byte rowMask = getByteMask(x, getRow(y, piecemask & (~kingMask)), false);
        long mask = ((rowMask & 0xFFL) << ((7 - y) * 8)) | getColumnFromByte(colMask, x);
        long result = rookmoveLUT[index];
        result = result & (~mask);
        //printLong(result);
        return result;
    }

    protected long getBishopAttack(int index, int x, int y, boolean isWhite, boolean ignoreKing){
        long kingMask = 0;
        if (isWhite && ignoreKing)
            kingMask = kingb;
        else if (ignoreKing)
            kingMask = kingw;
        byte leftMask = getByteMask(y, getDiagonal(index, piecemask & (~kingMask), true) , false);
        byte rightMask = getByteMask(y,getDiagonal(index, piecemask & (~kingMask), false) , false);
        long mask = diagonalMask(leftMask, rightMask, index, x, y);
        return bishopmoveLUT[index] & (~mask);
    }

    protected long getSpecificAttack(int index, int x, int y, AttackType type){
        switch (type){
            case DIAGONAL_LEFT -> {
                byte leftMask = getByteMask(y, getDiagonal(index, piecemask, true) , false);
                return leftDiagLUT[index] & (~diagonalMask(leftMask, (byte)0, index, x, y)) & (~getFieldMask(index));
            }
            case DIAGONAL_RIGHT -> {
                byte rightMask = getByteMask(y, getDiagonal(index, piecemask, false) , false);
                return rightDiagLUT[index] & (~diagonalMask((byte)0, rightMask, index, x, y)) & (~getFieldMask(index));
            }
            case ROW ->{
                byte rowMask = getByteMask(x, getRow(y, piecemask), false);
                return ((((byte)0b11111111) & 0xFFL) << ((7 - y) * 8)) & (~((rowMask & 0xFFL) << ((7 - y) * 8))) & (~getFieldMask(index));
            }
            case COLUMN -> {
                byte colMask = getByteMask(y, getColumn(x, piecemask), false);
                return getColumnFromByte(((byte)0b11111111), x) & (~getColumnFromByte(colMask, x)) & (~getFieldMask(index));
            }
            case COLUMN_UP -> {
                byte colMask = getByteMask(y, getColumn(x, piecemask), false);
                return getColumnFromByte(((byte)0b11111111), x) & (~getColumnFromByte(colMask, x)) & (getSideBitmask(x, y, Sides.UP,true));
            }
            case COLUMN_DOWN -> {
                byte colMask = getByteMask(y, getColumn(x, piecemask), false);
                return getColumnFromByte(((byte)0b11111111), x) & (~getColumnFromByte(colMask, x)) & (getSideBitmask(x, y, Sides.DOWN,true));
            }
            case ROW_LEFT -> {
                byte rowMask = getByteMask(x, getRow(y, piecemask), false);
                return ((((byte)0b11111111) & 0xFFL) << ((7 - y) * 8)) & (~((rowMask & 0xFFL) << ((7 - y) * 8)))  & (getSideBitmask(x, y, Sides.LEFT,true));
            }
            case ROW_RIGHT -> {
                byte rowMask = getByteMask(x, getRow(y, piecemask), false);
                return ((((byte)0b11111111) & 0xFFL) << ((7 - y) * 8)) & (~((rowMask & 0xFFL) << ((7 - y) * 8)))  & (getSideBitmask(x , y, Sides.RIGHT,true));
            }
            case DIAGONAL_LEFT_LEFT -> {
                byte leftMask = getByteMask(y, getDiagonal(index, piecemask, true) , false);
                return leftDiagLUT[index] & (~diagonalMask(leftMask, (byte)0, index, x, y))  & (getSideBitmask(x, y, Sides.LEFT,true));
            }
            case DIAGONAL_LEFT_RIGHT -> {
                byte leftMask = getByteMask(y, getDiagonal(index, piecemask, true) , false);
                return leftDiagLUT[index] & (~diagonalMask(leftMask, (byte)0, index, x, y))  & (getSideBitmask(x, y, Sides.RIGHT,true));
            }
            case DIAGONAL_RIGHT_LEFT -> {
                byte rightMask = getByteMask(y, getDiagonal(index, piecemask, false) , false);
                return rightDiagLUT[index] & (~diagonalMask((byte)0, rightMask, index, x, y))  & (getSideBitmask(x, y, Sides.LEFT,true));
            }
            case DIAGONAL_RIGHT_RIGHT -> {
                byte rightMask = getByteMask(y, getDiagonal(index, piecemask, false) , false);
                return rightDiagLUT[index] & (~diagonalMask((byte)0, rightMask, index, x, y))  & (getSideBitmask(x, y, Sides.RIGHT,true));
            }
        }
        return 0;
    }

    protected long getSideBitmask(int x, int y, Sides side, boolean inclusive){
        switch (side){
            case UP -> {
                if (inclusive)
                    y += 1;
                switch (y){
                    case 0 -> {
                        return 0;
                    }
                    case 1 -> {
                        return 0xFF00000000000000L;
                    }
                    case 2 -> {
                        return 0xFFFF000000000000L;
                    }
                    case 3 -> {
                        return 0xFFFFFF0000000000L;
                    }
                    case 4 -> {
                        return 0xFFFFFFFF00000000L;
                    }
                    case 5 -> {
                        return 0xFFFFFFFFFF000000L;
                    }
                    case 6 -> {
                        return 0xFFFFFFFFFFFF0000L;
                    }
                    case 7 -> {
                        return 0xFFFFFFFFFFFFFF00L;
                    }
                    case 8 -> {
                        return 0xFFFFFFFFFFFFFFFFL;
                    }
                    default -> {
                        return 0;
                    }
                }
            }
            case DOWN -> {
                if (inclusive)
                    y -= 1;
                switch (y){
                    case -1 -> {
                        return 0xFFFFFFFFFFFFFFFFL;
                    }
                    case 0 -> {
                        return 0xFFFFFFFFFFFFFFL;
                    }
                    case 1 -> {
                        return 0xFFFFFFFFFFFFL;
                    }
                    case 2 -> {
                        return 0xFFFFFFFFFFL;
                    }
                    case 3 -> {
                        return 0xFFFFFFFFL;
                    }
                    case 4 -> {
                        return 0xFFFFFFL;
                    }
                    case 5 -> {
                        return 0xFFFFL;
                    }
                    case 6 -> {
                        return 0xFFL;
                    }
                    case 7 -> {
                        return 0;
                    }
                    default -> {
                        return 0;
                    }
                }
            }
            case LEFT -> {
                if (inclusive)
                    x += 1;
                switch (x){
                    case 0 -> {
                        return 0;
                    }
                    case 1 -> {
                        return 0x8080808080808080L;
                    }
                    case 2 -> {
                        return 0xC0C0C0C0C0C0C0C0L;
                    }
                    case 3 -> {
                        return 0xE0E0E0E0E0E0E0E0L;
                    }
                    case 4 -> {
                        return 0xF0F0F0F0F0F0F0F0L;
                    }
                    case 5 -> {
                        return 0xF8F8F8F8F8F8F8F8L;
                    }
                    case 6 -> {
                        return 0xFCFCFCFCFCFCFCFCL;
                    }
                    case 7 -> {
                        return 0xFEFEFEFEFEFEFEFEL;
                    }
                    case 8 -> {
                        return 0xFFFFFFFFFFFFFFFFL;
                    }
                    default -> {
                        return 0;
                    }
                }

            }
            case RIGHT -> {
                if (inclusive)
                    x -= 1;
                switch (x){
                    case -1 -> {
                        return 0xFFFFFFFFFFFFFFFFL;
                    }
                    case 0 -> {
                        return 0x7F7F7F7F7F7F7F7FL;
                    }
                    case 1 -> {
                        return 0x3F3F3F3F3F3F3F3FL;
                    }
                    case 2 -> {
                        return 0x1F1F1F1F1F1F1F1FL;
                    }
                    case 3 -> {
                        return 0x0F0F0F0F0F0F0F0FL;
                    }
                    case 4 -> {
                        return 0x0707070707070707L;
                    }
                    case 5 -> {
                        return 0x0303030303030303L;
                    }
                    case 6 -> {
                        return 0x0101010101010101L;
                    }
                    case 7 -> {
                        return 0;
                    }
                    default -> {
                        return 0;
                    }
                }

            }
        }
        return 0;
    }

    private void checkForPinsAndCheck(boolean isWhite){
        //Set mask values

        long kingMask;
        long friendly;
        long queenMask;
        long rookMask;
        long bishopMask;
        long pawnMask;
        long knightMask;
        if (isWhite) {
            kingMask = kingw;
            friendly = piecemaskw;
            queenMask = queenb;
            rookMask = rookb;
            bishopMask = bishopb;
            pawnMask = pawnb;
            knightMask = knightb;
        } else {
            kingMask = kingb;
            friendly = piecemaskb;
            queenMask = queenw;
            rookMask = rookw;
            bishopMask = bishopw;
            pawnMask = pawnw;
            knightMask = knightw;
        }
        //Get king position

        int kingPos = getIndexFromBitmask(kingMask);
        int x = indexXLUT[kingPos];
        int y = indexYLUT[kingPos];

        //long start = System.nanoTime();
        long[] result = new long[8];
        long pinMask = 0;
        int i = 0;
        boolean possiblePin = ((bishopmoveLUT[kingPos] & (bishopMask | queenMask)) !=0) || ((rookmoveLUT[kingPos] & (rookMask | queenMask)) != 0);
        if (possiblePin){
            //Check for column pin
            long pieceMask = getSpecificAttack(kingPos, x, y, AttackType.COLUMN) & friendly;
            List<Integer> pieces = getIndicesFromBitmask(pieceMask);
            for (int piece : pieces) {
                long attackmask = getSpecificAttack(piece, indexXLUT[piece], indexYLUT[piece], AttackType.COLUMN);
                if ( (attackmask & kingMask) != 0 && ((attackmask & queenMask) != 0 || (attackmask & rookMask) != 0)){
                    result[i] = (attackmask | getFieldMask(piece)) & (~kingMask);
                    pinMask |= result[i];
                    i++;
                }
            }
            //Check for row pin
            pieceMask = getSpecificAttack(kingPos, x, y, AttackType.ROW) & friendly;
            pieces = getIndicesFromBitmask(pieceMask);
            for (int piece : pieces) {
                long attackmask = getSpecificAttack(piece, indexXLUT[piece], indexYLUT[piece], AttackType.ROW);
                if ( (attackmask & kingMask) != 0 && ((attackmask & queenMask) != 0 || (attackmask & rookMask) != 0)){
                    result[i] = (attackmask | getFieldMask(piece)) & (~kingMask);
                    pinMask |= result[i];
                    i++;
                }
            }
            //Check for left diag pin
            pieceMask = getSpecificAttack(kingPos, x, y, AttackType.DIAGONAL_LEFT) & friendly;
            pieces = getIndicesFromBitmask(pieceMask);
            for (int piece : pieces) {
                long attackmask = getSpecificAttack(piece, indexXLUT[piece], indexYLUT[piece], AttackType.DIAGONAL_LEFT);
                if ( (attackmask & kingMask) != 0 && ((attackmask & queenMask) != 0 || (attackmask & bishopMask) != 0)){
                    result[i] = (attackmask | getFieldMask(piece)) & (~kingMask);
                    pinMask |= result[i];
                    i++;
                }
            }
            //Check for right diag pin
            pieceMask = getSpecificAttack(kingPos, x, y, AttackType.DIAGONAL_RIGHT) & friendly;
            pieces = getIndicesFromBitmask(pieceMask);
            for (int piece : pieces) {
                long attackmask = getSpecificAttack(piece, indexXLUT[piece], indexYLUT[piece], AttackType.DIAGONAL_RIGHT);
                if ( (attackmask & kingMask) != 0 && ((attackmask & queenMask) != 0 || (attackmask & bishopMask) != 0)){
                    result[i] = (attackmask | getFieldMask(piece)) & (~kingMask);
                    pinMask |= result[i];
                    i++;
                }
            }
        }
        //Apply result
        if (isWhite) {
            whitePins = result;
            whitePinMask = pinMask;
        }
        else {
            blackPins = result;
            blackPinMask = pinMask;
        }

        //pinTime += System.nanoTime() - start;


        //start = System.nanoTime();
        i = 0;
        if (isWhite && isWhiteInCheck) {
            whiteChecks[0] = 0;
            whiteChecks[1] = 0;
            long checkMask = getSpecificAttack(kingPos, x , y, AttackType.COLUMN_UP) & (queenMask | rookMask);
            int attackIndex;
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                whiteChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.COLUMN_DOWN);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.COLUMN_DOWN) & (queenMask | rookMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                whiteChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.COLUMN_UP);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.ROW_LEFT) & (queenMask | rookMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                whiteChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.ROW_RIGHT);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.ROW_RIGHT) & (queenMask | rookMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                whiteChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.ROW_LEFT);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.DIAGONAL_LEFT_LEFT) & (queenMask | bishopMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                whiteChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.DIAGONAL_LEFT_RIGHT);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.DIAGONAL_LEFT_RIGHT) & (queenMask | bishopMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                whiteChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.DIAGONAL_LEFT_LEFT);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.DIAGONAL_RIGHT_LEFT) & (queenMask | bishopMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                whiteChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.DIAGONAL_RIGHT_RIGHT);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.DIAGONAL_RIGHT_RIGHT) & (queenMask | bishopMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                whiteChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.DIAGONAL_RIGHT_LEFT);
                i++;
            }
            checkMask = getKnightAttack(kingPos, true) & knightMask;
            if (checkMask != 0){
                whiteChecks[i] = checkMask;
                i++;
            }
            checkMask = getPawnAttack(kingPos, true) & pawnMask;
            if (checkMask != 0){
                whiteChecks[i] = checkMask;
            }
        } else if (!isWhite && isBlackInCheck){
            blackChecks[0] = 0;
            blackChecks[1] = 0;
            long checkMask = getSpecificAttack(kingPos, x , y, AttackType.COLUMN_UP) & (queenMask | rookMask);
            int attackIndex;
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                blackChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.COLUMN_DOWN);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.COLUMN_DOWN) & (queenMask | rookMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                blackChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.COLUMN_UP);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.ROW_LEFT) & (queenMask | rookMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                blackChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.ROW_RIGHT);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.ROW_RIGHT) & (queenMask | rookMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                blackChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.ROW_LEFT);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.DIAGONAL_LEFT_LEFT) & (queenMask | bishopMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                blackChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.DIAGONAL_LEFT_RIGHT);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.DIAGONAL_LEFT_RIGHT) & (queenMask | bishopMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                blackChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.DIAGONAL_LEFT_LEFT);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.DIAGONAL_RIGHT_LEFT) & (queenMask | bishopMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                blackChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.DIAGONAL_RIGHT_RIGHT);
                i++;
            }
            checkMask = getSpecificAttack(kingPos, x , y, AttackType.DIAGONAL_RIGHT_RIGHT) & (queenMask | bishopMask);
            if (checkMask != 0){
                attackIndex = getIndexFromBitmask(checkMask);
                blackChecks[i] = getSpecificAttack(attackIndex, indexXLUT[attackIndex], indexYLUT[attackIndex], AttackType.DIAGONAL_RIGHT_LEFT);
                i++;
            }
            checkMask = getKnightAttack(kingPos, true) & knightMask;
            if (checkMask != 0){
                blackChecks[i] = checkMask;
                i++;
            }
            checkMask = getPawnAttack(kingPos, false) & pawnMask;
            if (checkMask != 0){
                blackChecks[i] = checkMask;
            }
        }
        //checkMaskTime += System.nanoTime() - start;
    }

    private void inCheck(boolean isWhite){
        //long start = System.nanoTime();
        if (isWhite){
            isBlackInCheck = false;
            int kingPos = getIndexFromBitmask(kingw);
            int x = indexXLUT[kingPos];
            int y = indexYLUT[kingPos];
            isWhiteInCheck = ((getBishopAttack(kingPos, x, y, true, false) & (queenb | bishopb)) != 0) || ((getRookAttack(kingPos, x, y, true, false) & (queenb |rookb)) != 0) || ((knightmoveLUT[kingPos] & knightb) != 0) || ((getPawnAttack(kingPos, true) & pawnb) != 0);
        } else {
            isWhiteInCheck = false;
            int kingPos = getIndexFromBitmask(kingb);
            int x = indexXLUT[kingPos];
            int y = indexYLUT[kingPos];
            isBlackInCheck = ((getBishopAttack(kingPos, x, y, false, false) & (queenw | bishopw)) != 0) || ((getRookAttack(kingPos, x, y, false, false) & (queenw | rookw)) != 0) || ((knightmoveLUT[kingPos] & knightw) != 0) || ((getPawnAttack(kingPos, false) & pawnw) != 0);
        }
        //checkCheckTime += System.nanoTime() - start;
    }

    private long getPinMask(long fieldMask, boolean isWhite){
        long[] pins;
        if (isWhite){
            if ((fieldMask & whitePinMask) == 0)
                return 0xFFFFFFFFFFFFFFFFL;
            pins = whitePins;
        } else {
            if ((fieldMask & blackPinMask) == 0)
                return 0xFFFFFFFFFFFFFFFFL;
            pins = blackPins;
        }

        for (int i = 0; i < 8; i++){
            if (pins[i] == 0)
                return 0b1111111111111111111111111111111111111111111111111111111111111111L;
            if ((pins[i] & fieldMask) != 0)
                return pins[i];
        }
        return 0b1111111111111111111111111111111111111111111111111111111111111111L;
    }

    protected long getQueenAttack(int index, int x, int y, boolean isWhite){
        return getBishopAttack(index, x, y , isWhite, true) | getRookAttack(index, x, y , isWhite, true);
    }

    protected long getKingAttack(int index, boolean isWhite){
        return kingmoveLUT[index];
    }

    protected long getKnightAttack(int index, boolean isWhite){
        return knightmoveLUT[index];
    }

    protected long getPawnAttack (int index, boolean isWhite){
        if (isWhite)
            return pawnattackWLUT[index];
        else
            return pawnattackBLUT[index];
    }
    /**
     * Precomputes the lookup table of king moves.
     * <p>Shouldn't be called after initialization.
     */
    private void precomputeKingMove (){
        for (int i = 0; i < 64; i++){
            long num = 0b1000000000000000000000000000000000000000000000000000000000000000L >>> i;
            if ((num & (~0b1111111110000001100000011000000110000001100000011000000111111111L)) != 0){
                kingmoveLUT[i] = 0b1110000010100000111000000000000000000000000000000000000000000000L >>> (i - 9);
            } else if ((num & 0b1000000000000000000000000000000000000000000000000000000000000000L) != 0){
                kingmoveLUT[i] = 0b0100000011000000000000000000000000000000000000000000000000000000L;
            } else if ((num & 0b0000000100000000000000000000000000000000000000000000000000000000L) != 0){
                kingmoveLUT[i] = 0b0000001000000011000000000000000000000000000000000000000000000000L;
            } else if ((num & 0b0000000000000000000000000000000000000000000000000000000010000000L) != 0){
                kingmoveLUT[i] = 0b0000000000000000000000000000000000000000000000001100000001000000L;
            } else if ((num & 0b0000000000000000000000000000000000000000000000000000000000000001L) != 0){
                kingmoveLUT[i] = 0b0000000000000000000000000000000000000000000000000000001100000010L;
            } else if ((num & 0b0111111000000000000000000000000000000000000000000000000000000000L) != 0){
                kingmoveLUT[i] = 0b1010000011100000000000000000000000000000000000000000000000000000L >>> (i - 1);
            } else if ((num & 0b0000000000000000000000000000000000000000000000000000000001111110L) != 0){
                kingmoveLUT[i] = 0b0000000000000000000000000000000000000000000000001110000010100000L >>> (i - 57);
            } else if ((num & 0b0000000010000000100000001000000010000000100000001000000000000000L) != 0){
                kingmoveLUT[i] = 0b1100000001000000110000000000000000000000000000000000000000000000L >>> (i - 8);
            } else if ((num & 0b0000000000000001000000010000000100000001000000010000000100000000L) != 0){
                kingmoveLUT[i] = 0b0000001100000010000000110000000000000000000000000000000000000000L >>> (i - 15);
            }
        }
    }

    /**
     * Precomputes the lookup table of rook moves.
     * <p>Shouldn't be called after initialization.
     */
    private void precomputeRookMove (){
        for (int i = 0; i < 64; i++){
            int x = i % 8;
            int y = i / 8;
            long col = 0b1000000010000000100000001000000010000000100000001000000010000000L >>> x;
            long row = 0b1111111100000000000000000000000000000000000000000000000000000000L >>> y * 8;
            long field = 0b1000000000000000000000000000000000000000000000000000000000000000L >>> i;
            rookmoveLUT[i] = (col | row) & (~field);
        }
    }

    /**
     * Precomputes the lookup table of bishop moves.
     * <p>Shouldn't be called after initialization.
     */
    private void precomputeBishopMove (){
        for (int i = 0; i < 64; i++){
            long rightDiag = 0b0000000000000000000000000000000000000000000000000000000000000000L;
            long leftDiag = 0b0000000000000000000000000000000000000000000000000000000000000000L;
            int col = i % 8;
            int row = i / 8;
            for (int y = 0; y < 8; y++){
                for (int x = 0; x < 8; x++){
                    if (x + y == col + row)
                        rightDiag = rightDiag | (0b1000000000000000000000000000000000000000000000000000000000000000L >>> (x + y * 8));
                    if (x - y == col - row)
                        leftDiag = leftDiag | (0b1000000000000000000000000000000000000000000000000000000000000000L >>> (x + y * 8));
                }
            }
            bishopmoveLUT[i] = (leftDiag | rightDiag) & (~(0b1000000000000000000000000000000000000000000000000000000000000000L >>> i));
            leftDiagLUT[i] = leftDiag;
            rightDiagLUT[i] = rightDiag;
        }
    }

    /**
     * Precomputes the lookup table of knight moves.
     * <p>Shouldn't be called after initialization.
     */
    private void precomputeKnightMove (){
        Int2[] moves = {new Int2(2,1), new Int2(2,-1), new Int2(-2,1), new Int2(-2,-1), new Int2(1,2), new Int2(1,-2), new Int2(-1,2), new Int2(-1,-2)};
        for (int i = 0; i < 64; i++){
            long num = 0b0000000000000000000000000000000000000000000000000000000000000000L;
            int x = i % 8;
            int y = i / 8;
            for (Int2 move : moves){
                int col = x + move.x;
                int row = y + move.y;
                if (!(col < 0 || col >= 8 || row < 0 || row >= 8)){
                    num = num | 0b1000000000000000000000000000000000000000000000000000000000000000L >>> col + row * 8;
                }
            }
            knightmoveLUT[i] = num;
        }
    }

    /**
     * Precomputes the lookup table of pawn moves.
     * <p>Shouldn't be called after initialization.
     */
    private void precomputePawnMove (){
        for (int i = 0; i < 64; i++){
            Int2 field = new Int2(i % 8, i / 8);
            long num = 0;
            num = num | getFieldMask(new Int2(field.x,field.y - 1));
            if (field.y == 6)
                num = num | getFieldMask(new Int2(field.x,field.y - 2));
            pawnmoveWLUT[i] = num;
            num = 0;
            num = num | (getFieldMask(new Int2(field.x + 1,field.y - 1)) | getFieldMask(new Int2(field.x - 1,field.y - 1)));
            pawnattackWLUT[i] = num;
        }
        for (int i = 0; i < 64; i++){
            Int2 field = new Int2(i % 8, i / 8);
            long num = 0;
            num = num | getFieldMask(new Int2(field.x,field.y + 1));
            if (field.y == 1)
                num = num | getFieldMask(new Int2(field.x,field.y + 2));
            pawnmoveBLUT[i] = num;
            num = 0;
            num = num | (getFieldMask(new Int2(field.x + 1,field.y + 1)) | getFieldMask(new Int2(field.x - 1,field.y + 1)));
            pawnattackBLUT[i] = num;
        }
    }


    /**
     * Returns a list of indices of all the masked fields of the provided bitmask.
     * @param bitmask Bitmask
     * @return List of field indices
     */
    public List<Int2> getFieldsFromBitmask (long bitmask){
        List<Int2> fields = new ArrayList<>();
        for (int i = 0; i < 8; i++){
            byte row = (byte) bitmask;
            List<Integer> columns = getColumnInRow(row);
            for (Integer column : columns){
                fields.add(new Int2(column, 7 - i));
            }
            bitmask >>>= 8;
        }
        return fields;
    }

    public List<Integer> getIndicesFromBitmask (long bitmask){
        List<Integer> fields = new ArrayList<>(Long.bitCount(bitmask));
        int row = 56;
        while (bitmask != 0){
            for (Integer column : getColumnInRow((byte) bitmask)){
                fields.add(column + row);
            }
            row -= 8;
            bitmask >>>= 8;
        }
        return fields;
    }

    public int getIndexFromBitmask (long bitmask){
        return Long.numberOfLeadingZeros(bitmask);
    }

    /**
     * Returns a list of masked positions in the provided byte bitmask.
     * @param row Byte bitmask
     * @return List of masked positions
     */
    private List<Integer> getColumnInRow (byte row){
        return rowsLUT[row + 128];
    }

    /**
     * Precomputes a lookup table of masked positions for all byte permutations.
     * <p>Shouldn't be called after initialization.
     */
    private void precomputeRows(){
        for (byte i = -128; ; i++){
            List<Integer> columns = new ArrayList<>();
            int comp = 0b10000000;
            for (int j = 0; j < 8; j++){
                if ((i & comp) != 0)
                    columns.add(j);
                comp = comp >>> 1;
            }
            rowsLUT[i + 128] = columns;
            if (i == 127) break;
        }
    }

    /**
     * Returns a byte bitmask of the selected row in the provided bitmask.
     * @param row Selected row index
     * @param bitmask Bitmask
     * @return Row mask
     */
    private byte getRow (int row, long bitmask){
        bitmask = bitmask >>> ((7 - row) * 8) & 0xFF;
        return (byte) bitmask;
    }

    /**
     * Returns a byte bitmask of the selected column in the provided bitmask.
     * @param col Selected column index
     * @param bitmask Bitmask
     * @return Column mask
     */
    private byte getColumn (int col, long bitmask){
        byte result = 0; // Resulting column as a byte
        for (int row = 0; row < 8; row++) {
            // Shift the bitmask to isolate the current row
            int rowByte = (int) ((bitmask >>> ((7 - row) * 8)) & 0xFF);

            // Extract the specific bit from the row corresponding to the column
            int bit = (rowByte >>> (7 - col)) & 1;

            // Set the extracted bit in the result at the appropriate position
            result |= (byte) (bit << (7 - row));
        }
        return result;
    }

    public String fieldFromIndex (Int2 field){
        return indexFields[field.x + field.y * 8];
    }

    public String fieldFromIndex (int field){
        return indexFields[field];
    }

    private byte getDiagonal (int index, long bitmask, boolean left){
        if (left)
            bitmask = bitmask & leftDiagLUT[index];
        else
            bitmask = bitmask & rightDiagLUT[index];
        byte result = 0;
        for (int y = 0; y < 8; y++){
            int rowByte = (int) ((bitmask >>> ((7 - y) * 8)) & 0xFF);
            if (rowByte != 0)
                result = (byte) (result | 0b10000000 >>> y);
        }
        return result;
    }

    private long diagonalMask (byte leftMask, byte rightMask, int index, int x, int y){
        long left = 0;
        long right = 0;
        long mask = 0b1111111100000000000000000000000000000000000000000000000000000000L;
        long leftDiagMask = leftDiagLUT[index];
        long rightDiagMask = rightDiagLUT[index];
        for (int i = 0; i < 8; i++){
            int rowRmax = 7 + i;
            int rowLmin = -i;
            int rowLmax = 7 - i;
            boolean l = (x - y) >= rowLmin && (x - y) <= rowLmax;
            boolean r = (x + y) >= i && (x + y) <= rowRmax;
            if ((((leftMask >>> ( 7 - i)) & 1) != 0) && l)
                left |= mask >>> (i * 8);
            if ((((rightMask >>> ( 7 - i)) & 1) != 0) && r)
                right |= mask >>> (i * 8);
        }
        return (left & leftDiagMask) | (right & rightDiagMask);
    }

    /**
     * From a byte and a position, computes a byte mask so that after the first 1 to the left or right
     * of the position, all other bits are also 1.
     * <p> If friendly, the first 1 will remain, if enemy the first 1 will become a 0.
     * @param pos Origin index
     * @param mask Byte mask
     * @param friendly If friendly
     * @return Byte bitmask
     */
    private byte computeByteMask (int pos, byte mask, boolean friendly){
//        System.out.println("pos: " + pos);
//        System.out.print("mask: ");
//        printByte(mask);

        byte left = mask;
        byte right = mask;
        if (pos != 0)
            left = (byte) (mask & ((byte)0b10000000 >> (pos - 1)));
        else
            left = (byte) 0;

//        System.out.print("left mask: ");
//        printByte(left);

        right = (byte) (mask & (byte) (0xFF >>> (pos + 1)));

//        System.out.print("right mask: ");
//        printByte(right);

        int leftPos = 7 - Math.min(Integer.numberOfTrailingZeros(left),8);
//        System.out.println("left pos: " + leftPos);
        int rightPos = Integer.numberOfLeadingZeros(right) - 24;
//        System.out.println("right pos: " + rightPos);

        if (leftPos > 0)
            left = (byte) ((byte)0b10000000 >> leftPos);
        right = (byte) (byte) (0xFF >>> (rightPos));
        if (!friendly){
            left = (byte) (left << 1);
            right = (byte) (right >>> 1);
        }
        return (byte) (left | right);
    }

    /**
     * Precomputes a lookup table of byte masks.
     * <p>Shouldn't be called after initialization.
     */
    private void precomputeByteMasks(){
        for (int pos = 0; pos < 8; pos++) {
            for (int mask = -128; mask <= 127; mask++) {
                byteMaskFriendlyLUT[pos][mask + 128] = computeByteMask(pos, (byte) mask, true);
                byteMaskEnemyLUT[pos][mask + 128] = computeByteMask(pos, (byte) mask, false);
            }
        }
    }

    /**
     * From a byte and a position, returns a byte mask so that after the first 1 to the left or right
     * of the position, all other bits are also 1.
     * <p> If friendly, the first 1 will remain, if enemy the first 1 will become a 0.
     * @param pos Origin index
     * @param mask Byte mask
     * @param friendly If friendly
     * @return Byte bitmask
     */
    private byte getByteMask (int pos, byte mask, boolean friendly){
        if (friendly)
            return byteMaskFriendlyLUT[pos][mask + 128];
        else
            return byteMaskEnemyLUT[pos][mask + 128];
    }

    /**
     * Maps a byte mask to be the first column of a 64bitmask.
     * @param mask Byte bitmask
     * @return 64bitmask
     */
    private long computeColumnFromByte(byte mask){
        long result = 0L;

        for (int row = 0; row < 8; row++) {
            int bit = (mask >>> (7 - row)) & 1;

            int position = 63 - ((row * 8));
            result |= ((long) bit << position);
        }
        return result;
    }

    /**
     * Precomputes a lookup table of 64bitmasks, for every byte permutation,
     * so that the byte is mapped on the first column of the bitmask.
     * <p>Shouldn't be called after initialization.
     */
    private void precomputeByteColumns(){
        for (int mask = -128; mask <= 127; mask++) {
            byteColumnLUT[mask + 128] = computeColumnFromByte((byte)mask);
        }
    }

    /**
     * Maps a byte mask to the specified column of a 64bitmask.
     * @param mask Byte bitmask
     * @param col Column index
     * @return 64bitmask
     */
    private long getColumnFromByte (byte mask, int col){
        return byteColumnLUT[mask + 128] >>> col;
    }


    private void precomputeIndexLUT(){
        for (int i = 0; i < 64; i++){
            indexXLUT[i] = i % 8;
            indexYLUT[i] = i / 8;
        }
    }

    private BoardState saveBoardState(){
        BoardState board = new BoardState();
        board.pawnw = pawnw;
        board.pawnb = pawnb;
        board.kingb = kingb;
        board.kingw = kingw;
        board.queenb = queenb;
        board.queenw = queenw;
        board.rookb = rookb;
        board.rookw = rookw;
        board.bishopb = bishopb;
        board.bishopw = bishopw;
        board.knightb = knightb;
        board.knightw = knightw;
        board.piecemask = piecemask;
        board.piecemaskw = piecemaskw;
        board.piecemaskb = piecemaskb;
        board.whiteAttack = whiteAttack;
        board.blackAttack = blackAttack;
        board.enPassant = enPassant;
        board.isWhiteTurn = isWhiteTurn;
        board.isBlackInCheck = isBlackInCheck;
        board.isWhiteInCheck = isWhiteInCheck;
        board.whiteChecks = whiteChecks.clone();
        board.blackChecks = blackChecks.clone();
        board.whitePins = whitePins.clone();
        board.blackPins = blackPins.clone();
        board.canWhiteCastleQ = canWhiteCastleQ;
        board.canWhiteCastleK = canWhiteCastleK;
        board.canBlackCastleQ = canBlackCastleQ;
        board.canBlackCastleK = canBlackCastleK;
        return board;
    }

    void loadBoardState(BoardState board){
        pawnw = board.pawnw;
        pawnb = board.pawnb;
        kingb = board.kingb;
        kingw = board.kingw;
        queenb = board.queenb;
        queenw = board.queenw;
        rookb = board.rookb;
        rookw = board.rookw;
        bishopb = board.bishopb;
        bishopw = board.bishopw;
        knightb = board.knightb;
        knightw = board.knightw;
        piecemask = board.piecemask;
        piecemaskw = board.piecemaskw;
        piecemaskb = board.piecemaskb;
        whiteAttack = board.whiteAttack;
        blackAttack = board.blackAttack;
        enPassant = board.enPassant;
        isWhiteTurn = board.isWhiteTurn;
        isBlackInCheck = board.isBlackInCheck;
        isWhiteInCheck = board.isWhiteInCheck;
        whiteChecks = board.whiteChecks.clone();
        blackChecks = board.blackChecks.clone();
        whitePins = board.whitePins.clone();
        blackPins = board.blackPins.clone();
        canWhiteCastleQ = board.canWhiteCastleQ;
        canWhiteCastleK = board.canWhiteCastleK;
        canBlackCastleQ = board.canBlackCastleQ;
        canBlackCastleK = board.canBlackCastleK;

        updateGUIBoard();
    }
}
