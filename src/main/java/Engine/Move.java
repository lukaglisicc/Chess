package Engine;

public class Move {
    int startIndex;
    int endIndex;
    int startPiece;
    int endPiece;
    boolean castle;
    boolean enPassant;
    boolean promotion;
    boolean removedCastleBK;
    boolean removedCastleBQ;
    boolean removedCastleWK;
    boolean removedCastleWQ;
    boolean lastMoveEP;
    int targetEP;

    static int toPackedInteger(int startIndex, int endIndex, int startPiece, int endPiece, boolean castle, boolean enPassant, boolean promotion, boolean removedCastleBK, boolean removedCastleBQ, boolean removedCastleWK, boolean removedCastleWQ, boolean lastMoveEP, int targetEP) {
        int packed = startIndex;
        packed |= (endIndex << 6);
        packed |= (startPiece << 12);
        packed |= (endPiece << 16);
        packed |= (castle ? 1 : 0) << 20;
        packed |= (enPassant ? 1 : 0) << 21;
        packed |= (promotion ? 1 : 0) << 22;
        packed |= (removedCastleBK ? 1 : 0) << 23;
        packed |= (removedCastleBQ ? 1 : 0) << 24;
        packed |= (removedCastleWK ? 1 : 0) << 25;
        packed |= (removedCastleWQ ? 1 : 0) << 26;
        packed |= (lastMoveEP ? 1 : 0) << 27;
        packed |= (targetEP << 28);

        return packed;
    }

    static Move fromPackedInteger(int packed) {
        Move move = new Move();
        move.startIndex = packed & 0x3F;
        move.endIndex = (packed >> 6) & 0x3F;
        move.startPiece = (packed >> 12) & 0xF;
        move.endPiece = (packed >> 16) & 0xF;
        move.castle = ((packed >> 20) & 0x1) != 0;
        move.enPassant = ((packed >> 21) & 0x1) != 0;
        move.promotion = ((packed >> 22) & 0x1) != 0;
        move.removedCastleBK = ((packed >> 23) & 0x1) != 0;
        move.removedCastleBQ = ((packed >> 24) & 0x1) != 0;
        move.removedCastleWK = ((packed >> 25) & 0x1) != 0;
        move.removedCastleWQ = ((packed >> 26) & 0x1) != 0;
        move.lastMoveEP = ((packed >> 27) & 0x1) != 0;
        move.targetEP = ((packed >> 28) & 0x7);

        return move;
    }

    public static void printMove(int packed){
        int startIndex = packed & 0x3F;
        int endIndex = (packed >> 6) & 0x3F;
        int startPiece = (packed >> 12) & 0xF;
        int endPiece = (packed >> 16) & 0xF;
        boolean castle = ((packed >> 20) & 0x1) != 0;
        boolean enPassant = ((packed >> 21) & 0x1) != 0;
        boolean promotion = ((packed >> 22) & 0x1) != 0;
        boolean removedCastleBK = ((packed >> 23) & 0x1) != 0;
        boolean removedCastleBQ = ((packed >> 24) & 0x1) != 0;
        boolean removedCastleWK = ((packed >> 25) & 0x1) != 0;
        boolean removedCastleWQ = ((packed >> 26) & 0x1) != 0;
        boolean lastMoveEP = ((packed >> 27) & 0x1) != 0;
        int targetEP = ((packed >> 28) & 0x7);

        System.out.println("Start: " + startIndex + "End: " + endIndex + "Start piece: " + startPiece + "End piece: " + endPiece + "Castle: " + castle + "En passant:" + enPassant + "Promotion:" + promotion + "Removed castle rights wk wq bk bq" + removedCastleWK + removedCastleWQ + removedCastleBK + removedCastleBQ + "Last move EP:" + lastMoveEP + "Target EP:" + targetEP);
    }
}
