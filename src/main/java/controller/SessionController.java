package controller;

import app.AppState;
import app.SessionState;
import loader.SessionRepository;
import utils.AlertHelper;
import view.ControlPanelView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class SessionController {

    private final AppState             appState;
    private final ProjectionController projectionCtrl;
    private final SessionRepository    sessionService;
    private final Stage                stage;

    public SessionController(
            AppState appState,
            ProjectionController projectionCtrl,
            Stage stage
    ) {
        this.appState         = appState;
        this.projectionCtrl   = projectionCtrl;
        this.sessionService   = new SessionRepository();
        this.stage            = stage;
    }

    public void onSaveAs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Session");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Session files", "*.session.json"));
        chooser.setInitialFileName("session.session.json");
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        saveToPath(file.toPath());
    }

    public void loadSession(Path path, ControlPanelView leftPanel, MainController mainCtrl) {
        try {
            SessionState state = sessionService.load(path);
            restoreState(state, leftPanel, mainCtrl);
        } catch (Exception e) {
            AlertHelper.showError("Could not load session: " + e.getMessage());
        }
    }

    private void saveToPath(Path path) {
        if (!appState.isLoaded()) return;
        try {
            SessionState state = buildState();
            sessionService.save(state, path);
        } catch (Exception e) {
            AlertHelper.showError("Save failed: " + e.getMessage());
        }
    }

    private SessionState buildState() {
        SessionState s = new SessionState();
        s.setSelectedWords(List.copyOf(appState.getSelectedWords()));
        s.setMetricName(appState.metricNameProperty().get());
        s.setAxes(projectionCtrl.getCurrentAxes());
        s.setIs3D(projectionCtrl.is3DMode());
        return s;
    }

    private void restoreState(SessionState state, ControlPanelView leftPanel, MainController mainCtrl) {
        if (state.getMetricName() != null) {
            leftPanel.setMetric(state.getMetricName());
            mainCtrl.onMetricChanged(state.getMetricName());
        }

        if (state.getAxes() != null && state.getAxes().length >= 2) {
            leftPanel.setAxes(state.getAxes());
            projectionCtrl.onAxesChanged(state.getAxes());
        }

        leftPanel.setMode(state.isIs3D());
        projectionCtrl.onModeChanged(state.isIs3D());

        if (state.getSelectedWords() != null && !state.getSelectedWords().isEmpty()) {
            appState.getSelectedWords().setAll(state.getSelectedWords());
        }
    }
}
