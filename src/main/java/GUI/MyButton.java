package GUI;

import Engine.Int2;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;


@Getter @Setter@NoArgsConstructor
public class MyButton extends Button {
    private String field;
    private Int2 coordinate;
    private ImageView imageView;
    private Pane overlay;
    private Pane checkOverlay;
    private List<Int2> validMoves = new ArrayList<>();
    public MyButton(String s){
        super(s);
    }

    public void overlay(boolean b){
        if(b)
            overlay.setOpacity(0.5);
        else
            overlay.setOpacity(0);
    }

    public void checkOverlay(boolean b){
        if(b)
            checkOverlay.setOpacity(0.5);
        else
            checkOverlay.setOpacity(0);
    }
}

