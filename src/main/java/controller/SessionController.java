package controller;

import app.AppConfig;
import app.AppState;
import app.SessionState;
import command.CommandHistory;
import loader.SessionRepository;
import utils.AlertHelper;
import utils.ErrorMessages;
import view.ControlPanelView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;

public class SessionController {

    private final AppState               appState;
    private final SessionRepository      sessionService;
    private final SessionStateController stateController;
    private final Stage                  stage;
    private final CommandHistory         commandHistory;

    public SessionController(
            AppState appState,
            ProjectionController projectionCtrl,
            Stage stage,
            CommandHistory commandHistory
    ) {
        this.appState        = appState;
        this.sessionService  = new SessionRepository();
        this.stateController = new SessionStateController(appState, projectionCtrl);
        this.stage           = stage;
        this.commandHistory  = commandHistory;
    }

    public void onSaveAs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Session");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Session files", "*" + AppConfig.SESSION_EXTENSION));
        chooser.setInitialFileName("session" + AppConfig.SESSION_EXTENSION);
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        saveToPath(file.toPath());
    }

    public void loadSession(Path path, ControlPanelView leftPanel, MainController mainCtrl, NeighborController neighborCtrl) {
        try {
            SessionState state = sessionService.load(path);
            stateController.applyState(state, leftPanel, mainCtrl, neighborCtrl);
            commandHistory.clear();
        } catch (Exception e) {
            AlertHelper.showError(ErrorMessages.sessionLoadFailed(e.getMessage()));
        }
    }

    private void saveToPath(Path path) {
        if (!appState.isLoaded()) return;
        try {
            sessionService.save(stateController.captureState(), path);
        } catch (Exception e) {
            AlertHelper.showError(ErrorMessages.sessionSaveFailed(e.getMessage()));
        }
    }
}
