package Engine;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Stack;

@NoArgsConstructor
public class BoardState {
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
    boolean canWhiteCastleQ = true;
    boolean canWhiteCastleK = true;
    boolean canBlackCastleQ = true;
    boolean canBlackCastleK = true;

    public BoardState(BoardState boardState) {
        pawnw = boardState.pawnw;
        pawnb = boardState.pawnb;
        kingb = boardState.kingb;
        kingw = boardState.kingw;
        queenb = boardState.queenb;
        queenw = boardState.queenw;
        rookb = boardState.rookb;
        rookw = boardState.rookw;
        bishopb = boardState.bishopb;
        bishopw = boardState.bishopw;
        knightb = boardState.knightb;
        knightw = boardState.knightw;
        piecemask = boardState.piecemask;
        piecemaskw = boardState.piecemaskw;
        piecemaskb = boardState.piecemaskb;
        whiteAttack = boardState.whiteAttack;
        blackAttack = boardState.blackAttack;
        enPassant = boardState.enPassant;
        isWhiteTurn = boardState.isWhiteTurn;
        isBlackInCheck = boardState.isBlackInCheck;
        isWhiteInCheck = boardState.isWhiteInCheck;
        whiteChecks = boardState.whiteChecks.clone();
        blackChecks = boardState.blackChecks.clone();
        whitePins = boardState.whitePins.clone();
        blackPins = boardState.blackPins.clone();
        canWhiteCastleQ = boardState.canWhiteCastleQ;
        canWhiteCastleK = boardState.canWhiteCastleK;
        canBlackCastleQ = boardState.canBlackCastleQ;
        canBlackCastleK = boardState.canBlackCastleK;
    }
}
