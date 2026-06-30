package controller;

import app.AppState;
import exception.UnknownMetricException;
import utils.AlertHelper;
import metric.MetricFactory;
import service.DistanceService;
import view.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainController {

    private final AppState appState;
    private final ProjectionController projectionController;
    private final DistanceService distanceService;

    private final WordCloud2DView cloud2D;
    private final WordCloud3DView cloud3D;
    private final ControlPanelView leftPanel;

    public MainController(
            AppState appState,
            ProjectionController projectionController,
            DistanceService distanceService,
            WordCloud2DView cloud2D,
            WordCloud3DView cloud3D,
            ControlPanelView leftPanel
    ) {
        this.appState = appState;
        this.projectionController = projectionController;
        this.distanceService = distanceService;
        this.cloud2D = cloud2D;
        this.cloud3D = cloud3D;
        this.leftPanel = leftPanel;
    }

    public void onReset() {
        boolean is3D = projectionController.is3DMode();

        resetAppState();
        resetMetric();
        reset2DView();
        reset3DView();
        leftPanel.resetControls(is3D);
        resetProjection();

    }

    private void resetAppState() {
        appState.getSelectedWords().clear();
        appState.coordinatesProperty().set("");
        appState.distanceResultProperty().set("—");
        appState.clearNeighbors();
        appState.setMetricName(MetricFactory.DEFAULT_METRIC);
    }

    private void resetMetric() {
        try {
            distanceService.setMetric(MetricFactory.get(MetricFactory.DEFAULT_METRIC));
        } catch (UnknownMetricException ignored) {
        }
    }

    private void reset2DView() {
        cloud2D.clearHighlights();
        cloud2D.setDistanceWords(Set.of());
        cloud2D.clearArithPath();
        cloud2D.resetView();
    }

    private void reset3DView() {
        cloud3D.setSelected(Set.of());
        cloud3D.setNeighbors(Map.of());
        cloud3D.setDistanceWords(Set.of());
        cloud3D.clearArithPath();
        cloud3D.resetView();
    }

    private void resetProjection() {
        projectionController.resetToDefaultProjection();
        projectionController.reprojectAndDraw();
    }

    public void onMetricChanged(String name) {
        try {
            var metric = MetricFactory.get(name);
            distanceService.setMetric(metric);
            appState.setMetricName(metric.name());
            onSelectionChanged(new ArrayList<>(appState.getSelectedWords()));
        } catch (UnknownMetricException e) {
            AlertHelper.showError(e.getMessage());
        }
    }

    public void onSelectionChanged(List<String> words) {
        if (words.size() == 2 && appState.isLoaded()) {
            var a = appState.getFullSpace().find(words.get(0));
            var b = appState.getFullSpace().find(words.get(1));

            if (a.isPresent() && b.isPresent()) {
                double dist = distanceService.compute(a.get(), b.get());
                appState.distanceResultProperty().set(
                        String.format("%.4f  [%s]", dist, distanceService.getMetric().name())
                );
                return;
            }
        }

        appState.distanceResultProperty().set("—");
    }
}