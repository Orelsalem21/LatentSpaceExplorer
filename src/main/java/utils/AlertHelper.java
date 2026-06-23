package utils;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public class AlertHelper {

    public static void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait();
    }

    public static void showWarning(String message) {
        new Alert(Alert.AlertType.WARNING, message, ButtonType.OK).showAndWait();
    }
}
