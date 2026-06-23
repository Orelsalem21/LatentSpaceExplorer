package utils;

import javafx.scene.control.Button;

public class ButtonStyler {

    private static final String NORMAL        = "-fx-background-color: #dee2e6; -fx-text-fill: #212529; -fx-cursor: hand; -fx-border-radius: 4; -fx-background-radius: 4;";
    private static final String HOVER         = "-fx-background-color: #ced4da; -fx-text-fill: #212529; -fx-cursor: hand; -fx-border-radius: 4; -fx-background-radius: 4;";
    private static final String DANGER_NORMAL = "-fx-background-color: #c92a2a; -fx-text-fill: white; -fx-cursor: hand; -fx-border-radius: 4; -fx-background-radius: 4;";
    private static final String DANGER_HOVER  = "-fx-background-color: #a61e1e; -fx-text-fill: white; -fx-cursor: hand; -fx-border-radius: 4; -fx-background-radius: 4;";

    public static void style(Button btn) {
        btn.setStyle(NORMAL);
        btn.setOnMouseEntered(e -> btn.setStyle(HOVER));
        btn.setOnMouseExited(e  -> btn.setStyle(NORMAL));
    }

    public static void styleDanger(Button btn) {
        btn.setStyle(DANGER_NORMAL);
        btn.setOnMouseEntered(e -> btn.setStyle(DANGER_HOVER));
        btn.setOnMouseExited(e  -> btn.setStyle(DANGER_NORMAL));
    }
}
