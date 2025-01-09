package App;

import GUI.MainGUI;
import javafx.application.Application;
import javafx.stage.Stage;
import Engine.*;



public class App extends Application {

    Engine engine;
    MainGUI gui;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        engine = Engine.getEngine();
        gui = new MainGUI(engine);
        engine.addSubscriber(gui);
        primaryStage.setScene(gui.initialize());
        primaryStage.setTitle("Chess");
        primaryStage.show();

        engine.interpretFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        //engine.interpretFEN("rnbq1rk1/1ppp1ppp/p4n2/2b1p3/2B1P3/3P1Q1P/PPP2PP1/RNB1K1NR w KQ - 0 6");
        //engine.interpretFEN("r1bk2Pr/p1ppBpNp/n4n2/1p1NP2P/1p4P1/3P4/P1P1K3/qR4b1");
        //engine.interpretFEN("K7/8/8/8/8/8/8/7k");

        //engine.move("c4c4", null);
    }
}
