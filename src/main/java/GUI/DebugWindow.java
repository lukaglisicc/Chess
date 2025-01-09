package GUI;

import Engine.*;
import Observer.NotificationType;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.Getter;

import java.util.List;

public class DebugWindow {
    private Scene scene;
    @Getter
    private Stage stage;

    public DebugWindow(MainGUI maingui, Engine engine) {
        VBox root = new VBox();

        Button button1 = new Button("Show white attack");
        button1.setOnAction(e -> {
            List<Int2> f = engine.getDebugger().getWhiteAttack();
            maingui.clearHighlight();
            maingui.update(NotificationType.HIGHLIGHT_FIELDS, f);
        });
        Button button2 = new Button("Show black attack");
        button2.setOnAction(e -> {
            List<Int2> f = engine.getDebugger().getBlackAttack();
            maingui.clearHighlight();
            maingui.update(NotificationType.HIGHLIGHT_FIELDS, f);
        });
        Button button3 = new Button("Show white pieces");
        button3.setOnAction(e -> {
            List<Int2> f = engine.getDebugger().getWhitePieces();
            maingui.clearHighlight();
            maingui.update(NotificationType.HIGHLIGHT_FIELDS, f);
        });
        Button button4 = new Button("Show black pieces");
        button4.setOnAction(e -> {
            List<Int2> f = engine.getDebugger().getBlackPieces();
            maingui.clearHighlight();
            maingui.update(NotificationType.HIGHLIGHT_FIELDS, f);
        });
        Button button5 = new Button("Pieces checking white");
        button5.setOnAction(e -> {
            List<Int2> f = engine.getDebugger().getCheckingPieces(true);
            maingui.clearHighlight();
            maingui.update(NotificationType.HIGHLIGHT_FIELDS, f);
        });
        Button button6 = new Button("Pieces checking black");
        button6.setOnAction(e -> {
            List<Int2> f = engine.getDebugger().getCheckingPieces(false);
            maingui.clearHighlight();
            maingui.update(NotificationType.HIGHLIGHT_FIELDS, f);
        });
        Button button7 = new Button("Pinned white pieces");
        button7.setOnAction(e -> {
            List<Int2> f = engine.getDebugger().getPinnedPiece(true);
            maingui.clearHighlight();
            maingui.update(NotificationType.HIGHLIGHT_FIELDS, f);
        });
        Button button8 = new Button("Pinned black pieces");
        button8.setOnAction(e -> {
            List<Int2> f = engine.getDebugger().getPinnedPiece(false);
            maingui.clearHighlight();
            maingui.update(NotificationType.HIGHLIGHT_FIELDS, f);
        });
        Button button9 = new Button("Get specific attack column");
        button9.setOnAction(e -> {
            List<Int2> f = engine.getDebugger().getSpecificAttack(AttackType.COLUMN_UP, true);
            maingui.clearHighlight();
            maingui.update(NotificationType.HIGHLIGHT_FIELDS, f);
        });
        Button button10 = new Button("Get specific attack row");
        button10.setOnAction(e -> {
            List<Int2> f = engine.getDebugger().getSpecificAttack(AttackType.ROW_RIGHT, true);
            maingui.clearHighlight();
            maingui.update(NotificationType.HIGHLIGHT_FIELDS, f);
        });
        Button button11 = new Button("Update board");
        button11.setOnAction(e -> {
            engine.getDebugger().updateBoard();
        });
        Button button12 = new Button("Undo last move");
        button12.setOnAction(e -> {
            engine.getDebugger().undoLastMove();
        });
        Button button13 = new Button("Perft 6");
        button13.setOnAction(e -> {
            engine.getDebugger().perft(6);
        });
        Button button15 = new Button("Perft 5");
        button15.setOnAction(e -> {
            engine.getDebugger().perft(5);
        });
        Button button16 = new Button("Perft 4");
        button16.setOnAction(e -> {
            engine.getDebugger().perft(4);
        });
        Button button17 = new Button("Perft 1");
        button17.setOnAction(e -> {
            engine.getDebugger().perft(1);
//            engine.getDebugger().perft(2);
//            engine.getDebugger().perft(3);
//            engine.getDebugger().perft(4);
//            engine.getDebugger().perft(5);
//            engine.getDebugger().perft(6);
        });
        Button button18 = new Button("Perft 2");
        button18.setOnAction(e -> {
            engine.getDebugger().perft(2);
        });
        Button button19 = new Button("Perft 3");
        button19.setOnAction(e -> {
            engine.getDebugger().perft(3);
        });
        Button button20 = new Button("Get en passant mask");
        button20.setOnAction(e -> {
            List<Int2> f = engine.getDebugger().getEPMask();
            maingui.clearHighlight();
            maingui.update(NotificationType.HIGHLIGHT_FIELDS, f);
        });
        Button button21 = new Button("Compare perft");
        button21.setOnAction(e -> {
            engine.getDebugger().comparePerft();
        });
        Button button22 = new Button("Load bad state");
        button22.setOnAction(e -> {
            engine.getDebugger().loadBadState();
        });
        Button button23 = new Button("Get all captures");
        button23.setOnAction(e -> {
            List<Int2> f = engine.getDebugger().getAllAvailableCaptures();
            maingui.clearHighlight();
            maingui.update(NotificationType.HIGHLIGHT_FIELDS, f);
        });



        root.getChildren().add(button1);
        root.getChildren().add(button2);
        root.getChildren().add(button3);
        root.getChildren().add(button4);
        root.getChildren().add(button5);
        root.getChildren().add(button6);
        root.getChildren().add(button7);
        root.getChildren().add(button8);
        root.getChildren().add(button9);
        root.getChildren().add(button10);
        root.getChildren().add(button11);
        root.getChildren().add(button12);
        root.getChildren().add(button13);
        root.getChildren().add(button15);
        root.getChildren().add(button16);
        root.getChildren().add(button17);
        root.getChildren().add(button18);
        root.getChildren().add(button19);
        root.getChildren().add(button20);
        root.getChildren().add(button21);
        root.getChildren().add(button22);
        root.getChildren().add(button23);

        scene = new Scene(root);
        stage = new Stage();
        stage.setScene(scene);
        stage.setTitle("Debug Window");
        stage.setMinHeight(200);
        stage.setMinWidth(300);
    }
}
