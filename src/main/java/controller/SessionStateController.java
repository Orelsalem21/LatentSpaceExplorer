package controller;

import app.AppState;
import app.SessionState;
import utils.AlertHelper;
import utils.ErrorMessages;
import view.ControlPanelView;

import java.util.List;

public class SessionStateController {

    private final AppState             appState;
    private final ProjectionController projectionCtrl;

    public SessionStateController(AppState appState, ProjectionController projectionCtrl) {
        this.appState       = appState;
        this.projectionCtrl = projectionCtrl;
    }

    public SessionState captureState() {
        SessionState s = new SessionState();
        s.setSelectedWords(List.copyOf(appState.getSelectedWords()));
        s.setMetricName(appState.metricNameProperty().get());
        s.setAxes(projectionCtrl.getCurrentAxes());
        s.setIs3D(projectionCtrl.is3DMode());
        s.setDataFingerprint(appState.getFullSpace().fingerprint());
        return s;
    }

    public void applyState(
            SessionState state,
            ControlPanelView leftPanel,
            MainController mainCtrl,
            NeighborController neighborCtrl
    ) {
        if (appState.isLoaded()
                && state.getDataFingerprint() != null
                && !state.getDataFingerprint().equals(appState.getFullSpace().fingerprint())) {
            AlertHelper.showWarning(ErrorMessages.sessionDataMismatch());
        }

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

        List<String> selectedWords = state.getSelectedWords();

        if (selectedWords == null || selectedWords.isEmpty()) {
            appState.getSelectedWords().clear();
        } else {
            appState.getSelectedWords().setAll(selectedWords);
        }

        if (neighborCtrl != null) {
            neighborCtrl.restoreNeighborState(selectedWords);
        }
    }
}
