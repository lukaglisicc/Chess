package GUI;
import Engine.*;
import Observer.ISubscriber;
import Observer.NotificationType;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import lombok.Getter;

import java.util.List;


public class MainGUI implements ISubscriber {

    private MyButton[][] buttons = new MyButton[8][8];
    private Engine engine;
    private ImageView mouseImage;
    @Getter
    private DebugWindow debugWindow;
    Pane mainPane;

    public MainGUI(Engine engine) {
        //Get engine reference
        this.engine = engine;
    }


    public Scene initialize() {
        //Init gui
        mainPane = new BorderPane();
        DebugWindow debugWindow = new DebugWindow(this, engine);
        //Init grid
        GridPane grid = new GridPane();
        ToolBar toolBar = new ToolBar();
        Button debugButton = new Button("Debug");
        debugButton.setOnAction(e -> {
            if (debugWindow.getStage().getOwner() == null)
                debugWindow.getStage().initOwner(mainPane.getScene().getWindow());
            debugWindow.getStage().show();
        });
        Button resetButton = new Button("Reset board");
        resetButton.setOnAction(e -> {
            clearBoard();
            engine.interpretFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        });
        toolBar.getItems().addAll(debugButton, resetButton);
        ((BorderPane)mainPane).setTop(toolBar);


        //Set row labels
        for(int i = 0; i < 8; i++){
            Label label = new Label("     " + (8 - i) + "     ");
            grid.add(label, 0,i);
        }

        //Add fields as buttons
        for (int i = 1; i < 9; i++) {
            for (int j = 0; j < 8; j++) {

                StackPane pane = new StackPane();

                //MyButton button = new MyButton(i - 1 + "," + j);
                MyButton button = new MyButton();
                pane.getChildren().add(button);

                //Make field overlay
                Pane overlay = new Pane();
                overlay.setStyle("-fx-background-color: rgb(207,148,0);");
                overlay.setOpacity(0);
                overlay.setMouseTransparent(true);
                pane.getChildren().add(overlay);
                button.setOverlay(overlay);

                //Make check overlay
                Pane checkOverlay = new Pane();
                checkOverlay.setStyle("-fx-background-color: rgb(207,0,0);");
                checkOverlay.setOpacity(0);
                checkOverlay.setMouseTransparent(true);
                pane.getChildren().add(checkOverlay);
                button.setCheckOverlay(checkOverlay);

                //Make image view
                ImageView imageView = new ImageView();
                imageView.setFitHeight(100);
                imageView.setFitWidth(100);
                button.setImageView(imageView);
                pane.getChildren().add(imageView);

                //When the piece image is pressed, set it to null and set the mouse image to it
                imageView.setOnMousePressed(e -> {
                    if (e.getButton() == MouseButton.PRIMARY && !engine.lockInput) {
                        //Set mouse image location
                        mouseImage.setLayoutX(e.getSceneX() - 50);
                        mouseImage.setLayoutY(e.getSceneY() - 50);
                        //Set mouse image and clear this
                        mouseImage.setImage(imageView.getImage());
                        imageView.setImage(null);
                        //Call clicked on field
                        button.setValidMoves(clickedOnField(button.getCoordinate()));
                        clearHighlight();
                        update(NotificationType.HIGHLIGHT_FIELDS, button.getValidMoves());
                    }
                });
                //When mouse released, if over this button, revert changes
                imageView.setOnMouseReleased(e -> {
                    if (e.getButton() == MouseButton.PRIMARY && !engine.lockInput) {
                        //Get the field we are over
                        MyButton releasedOver = getButtonAt(e.getSceneX(), e.getSceneY());
                        //Check if the field is this field or null
                        if(releasedOver == null || releasedOver.equals(button) || button.getValidMoves() == null || !(button.getValidMoves().contains(releasedOver.getCoordinate()))) {
                            imageView.setImage(mouseImage.getImage());
                        } else {
                            button.getValidMoves().clear();
                            String move = button.getField() + releasedOver.getField();
                            if (engine.isMovePromotion(move)){
                                if (engine.isWhiteTurn)
                                    move = move.concat("Q");
                                else
                                    move = move.concat("q");
                            }
                            engine.move(move);
                            clearHighlight();
                        }
                        mouseImage.setImage(null);
                    }
                });
                //On mouse dragged update mouse image location
                imageView.setOnMouseDragged(e -> {
                    if (e.getButton() == MouseButton.PRIMARY) {
                        mouseImage.setLayoutX(e.getSceneX() - 50);
                        mouseImage.setLayoutY(e.getSceneY() - 50);
                    }
                });

                //Set button coordinate and field variables
                button.setCoordinate(new Int2(i-1,j));
                button.setField(String.valueOf((char)('a'+i-1)) + String.valueOf(8-j));

                //Set size
                button.setMinSize(100, 100);
                button.setMaxSize(100, 100);

                //Color buttons
                if((i+j)%2!=0)
                    button.setBackground(new Background(new BackgroundFill(Color.rgb(99,54,32), null, null)));
                else
                    button.setBackground(new Background(new BackgroundFill(Color.rgb(215,184,169), null, null)));
                grid.add(pane, i, j);
                /*button.setOnAction(e -> {
                    System.out.println(button.getField());
                    buttonMove(button.getField());
                });*/
                //Add button to matrix
                buttons[i-1][j] = button;
            }
        }

        //Set column labels
        grid.add(new Label("            A"), 1, 8);
        grid.add(new Label("            B"), 2, 8);
        grid.add(new Label("            C"), 3, 8);
        grid.add(new Label("            D"), 4, 8);
        grid.add(new Label("            E"), 5, 8);
        grid.add(new Label("            F"), 6, 8);
        grid.add(new Label("            G"), 7, 8);
        grid.add(new Label("            H"), 8, 8);

        //Init dragged image
        mouseImage = new ImageView();
        mouseImage.setFitHeight(100);
        mouseImage.setFitWidth(100);
        mouseImage.setMouseTransparent(true);
        ((BorderPane)mainPane).setCenter(grid);
        mainPane.getChildren().add(mouseImage);


        return new Scene(mainPane);
    }


    @Override
    public void update(NotificationType type, Object o) {
        if (type.equals(NotificationType.PIECE_AT_FIELD)){
            String message = (String)o;
            String[] strings = message.split(" ");
            Image img = getPieceImage(strings[0].charAt(0));
            int col = Integer.parseInt(strings[1]);
            int i = Integer.parseInt(strings[2]);
            buttons[col][i].getImageView().setImage(img);
        }
        if (type.equals(NotificationType.HIGHLIGHT_FIELDS)){
            List<Int2> fields = (List<Int2>)o;
            for (Int2 field : fields){
                buttons[field.x][field.y].overlay(true);
            }
        }
        if (type.equals(NotificationType.CHECK)){
            clearCheck();
            if (o != null){
                Int2 field = (Int2)o;
                buttons[field.x][field.y].checkOverlay(true);
            }
        }
    }

    public void clearBoard(){
        for (int i  = 0; i < 8; i++){
            for (int j  = 0; j < 8; j++){
                buttons[i][j].getImageView().setImage(null);
            }
        }
    }

    private Image getPieceImage(char piece) {
        return switch (piece) {
            case 'k' -> new Image(getClass().getResourceAsStream("/Pieces/kingb.png"));
            case 'K' -> new Image(getClass().getResourceAsStream("/Pieces/kingw.png"));
            case 'q' -> new Image(getClass().getResourceAsStream("/Pieces/queenb.png"));
            case 'Q' -> new Image(getClass().getResourceAsStream("/Pieces/queenw.png"));
            case 'r' -> new Image(getClass().getResourceAsStream("/Pieces/rookb.png"));
            case 'R' -> new Image(getClass().getResourceAsStream("/Pieces/rookw.png"));
            case 'n' -> new Image(getClass().getResourceAsStream("/Pieces/knightb.png"));
            case 'N' -> new Image(getClass().getResourceAsStream("/Pieces/knightw.png"));
            case 'b' -> new Image(getClass().getResourceAsStream("/Pieces/bishopb.png"));
            case 'B' -> new Image(getClass().getResourceAsStream("/Pieces/bishopw.png"));
            case 'p' -> new Image(getClass().getResourceAsStream("/Pieces/pawnb.png"));
            case 'P' -> new Image(getClass().getResourceAsStream("/Pieces/pawnw.png"));
            default -> null;
        };
    }

    public void clearHighlight() {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                buttons[i][j].overlay(false);
            }
        }
    }

    public void clearCheck(){
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                buttons[i][j].checkOverlay(false);
            }
        }
    }

    private List<Int2> clickedOnField(Int2 field) {
        int index = field.x + field.y * 8;
        return engine.getFieldsFromBitmask(engine.getValidMoves(index));
    }



    MyButton getButtonAt(double x, double y) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                Point2D p = buttons[i][j].sceneToLocal(x, y);
                if (buttons[i][j].contains(p))
                    return buttons[i][j];
            }
        }
        return null;
    }

}

