package controller;

import utils.AlertHelper;
import exception.WordNotFoundException;
import app.AppState;
import command.CommandHistory;
import command.ReversibleCommand;
import view.DetailsPanelView;
import view.WordCloudView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SearchController {

    private final AppState             appState;
    private final CommandHistory       commandHistory;
    private final ProjectionController projectionCtrl;

    private final WordCloudView   cloud2D;
    private final WordCloudView   cloud3D;

    public SearchController(
            AppState appState,
            WordCloudView cloud2D,
            WordCloudView cloud3D,
            CommandHistory commandHistory,
            ProjectionController projectionCtrl
    ) {
        this.appState       = appState;
        this.cloud2D        = cloud2D;
        this.cloud3D        = cloud3D;
        this.commandHistory = commandHistory;
        this.projectionCtrl = projectionCtrl;
    }


    public void wirePanel(DetailsPanelView rightPanel, NeighborController neighborCtrl) {
        rightPanel.setSelectedWordsItems(appState.getSelectedWords());
        rightPanel.setNeighborsItems(appState.getNeighbors());

        rightPanel.setDistanceResultText(appState.distanceResultProperty().get());
        rightPanel.setCoordinatesText(appState.coordinatesProperty().get());
        rightPanel.setMetricActiveText(appState.metricNameProperty().get());

        appState.distanceResultProperty().addListener((obs, o, n) -> rightPanel.setDistanceResultText(n));
        appState.coordinatesProperty().addListener((obs, o, n) -> rightPanel.setCoordinatesText(n));
        appState.metricNameProperty().addListener((obs, o, n) -> rightPanel.setMetricActiveText(n));

        rightPanel.setOnSearch(query -> {
            String normalized = query.trim().toLowerCase();

            onSearch(normalized);

            if (!normalized.isEmpty() && appState.getSpace().contains(normalized)) {
                neighborCtrl.onWordSelected(normalized);
            }
        });

        rightPanel.setOnCenterWord(word -> {
            onWordSelected(word);
            neighborCtrl.onWordSelected(word);
        });

        rightPanel.setOnNeighborSelected(word -> {
            onWordSelected(word);
            neighborCtrl.onWordSelected(word);
        });
    }


    public void onWordSelected(String word) {
        SelectionSnapshot prev = snapshot();

        commandHistory.execute(new ReversibleCommand(
                () -> applySingleSelection(word),
                () -> restore(prev)
        ));
    }

    public void onSearch(String query) {
        if (query.isBlank() || !appState.isLoaded()) return;

        String normalized = query.trim().toLowerCase();
        SelectionSnapshot prev = snapshot();

        if (!appState.getSpace().contains(normalized)) {
            AlertHelper.showError(new WordNotFoundException(query).getMessage());
            commandHistory.execute(new ReversibleCommand(
                    this::applyClearedSelection,
                    () -> restore(prev)
            ));
            return;
        }

        commandHistory.execute(new ReversibleCommand(
                () -> applySingleSelection(normalized),
                () -> restore(prev)
        ));
    }


    private void applyClearedSelection() {
        appState.getSelectedWords().clear();
        appState.clearNeighbors();
        syncClouds(Set.of(), Map.of());
    }

    private void applySingleSelection(String word) {
        cloud2D.clearArithPath();
        cloud3D.clearArithPath();

        appState.getSelectedWords().setAll(word);

        cloud2D.setSelected(Set.of(word));
        cloud2D.centerOnWord(word);
        cloud3D.centerOnWord(word);

        updateCoordinates(word);
    }

    private void updateCoordinates(String word) {
        projectionCtrl.findPoint(word).ifPresentOrElse(
                p -> appState.coordinatesProperty().set(
                        String.format(
                                "%s (X): %.4f   %s (Y): %.4f",
                                appState.getXAxisLabel(), p.getX(),
                                appState.getYAxisLabel(), p.getY()
                        )
                ),
                () -> appState.coordinatesProperty().set("")
        );
    }


    private SelectionSnapshot snapshot() {
        return new SelectionSnapshot(
                new ArrayList<>(appState.getSelectedWords()),
                new ArrayList<>(appState.getNeighbors()),
                new LinkedHashMap<>(appState.getNeighborMap()),
                appState.coordinatesProperty().get()
        );
    }

    private void restore(SelectionSnapshot snapshot) {
        Set<String> selected = new HashSet<>(snapshot.selectedWords());

        appState.getSelectedWords().setAll(snapshot.selectedWords());
        appState.setNeighborMap(snapshot.neighborMap());
        appState.getNeighbors().setAll(snapshot.neighborLabels());
        appState.coordinatesProperty().set(snapshot.coordinates());

        syncClouds(selected, snapshot.neighborMap());
    }

    private record SelectionSnapshot(
            List<String> selectedWords,
            List<String> neighborLabels,
            Map<String, Double> neighborMap,
            String coordinates
    ) {}


    private void syncClouds(Set<String> selected, Map<String, Double> neighbors) {
        cloud2D.setSelected(selected);
        cloud2D.setNeighbors(neighbors);
        cloud3D.setSelected(selected);
        cloud3D.setNeighbors(neighbors);
    }
}
