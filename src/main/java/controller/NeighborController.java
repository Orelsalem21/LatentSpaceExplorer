package controller;

import utils.AlertHelper;
import app.AppState;
import command.CommandHistory;
import command.ReversibleCommand;
import model.CentroidResult;
import model.NeighborResult;
import model.WordVector;
import service.CentroidService;
import service.NearestNeighborService;
import view.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class NeighborController {

    private final AppState               appState;
    private final NearestNeighborService neighborService;
    private final CentroidService        centroidService;
    private final CommandHistory         commandHistory;

    private final WordCloudView     cloud2D;
    private final WordCloudView     cloud3D;
    private final DetailsPanelView  rightPanel;

    private int              currentK             = 5;
    private String           currentWord          = null;  // null = centroid mode
    private List<String>     currentCentroidWords = List.of();
    private ControlPanelView leftPanel            = null;

    public NeighborController(
            AppState appState,
            NearestNeighborService neighborService,
            CentroidService centroidService,
            CommandHistory commandHistory,
            WordCloudView cloud2D,
            WordCloudView cloud3D,
            DetailsPanelView rightPanel
    ) {
        this.appState        = appState;
        this.neighborService = neighborService;
        this.centroidService = centroidService;
        this.commandHistory  = commandHistory;
        this.cloud2D         = cloud2D;
        this.cloud3D         = cloud3D;
        this.rightPanel      = rightPanel;
    }


    public void wireCallbacks(ControlPanelView leftPanel) {
        this.leftPanel = leftPanel;
        leftPanel.setMaxK(appState.getFullSpace().size());

        leftPanel.setOnKChanged(k -> {
            int prevK = currentK;
            commandHistory.execute(new ReversibleCommand(
                    () -> { leftPanel.setK(k); onKChanged(k); },
                    () -> { leftPanel.setK(prevK); onKChanged(prevK); }
            ));
        });

        rightPanel.setOnComputeCentroid(words -> {
            List<String> wordsCopy = new ArrayList<>(words);

            commandHistory.execute(new ReversibleCommand(
                    () -> {
                        this.currentWord = null;   // enter centroid mode
                        onComputeCentroid(wordsCopy);
                    },
                    this::clearCentroid
            ));
        });
    }


    public void onWordSelected(String word) {
        this.currentWord = word;
        rightPanel.setCentroidStatus("—");
        if (appState.isLoaded()) doFind();
    }

    public void reset() {
        this.currentK             = 5;
        this.currentWord          = null;
        this.currentCentroidWords = List.of();
    }

    public void restoreNeighborState(List<String> selectedWords) {
        if (selectedWords == null || selectedWords.isEmpty()) {
            currentWord = null;
            clearCentroid();
            return;
        }

        if (selectedWords.size() == 1) {
            onWordSelected(selectedWords.getFirst());
        } else {
            currentWord = null;
            onComputeCentroid(selectedWords);
        }
    }

    public void onCustomAxisWords(String fromWord, String toWord) {
        if (!appState.isLoaded()) return;

        var fromOpt = appState.getFullSpace().find(fromWord);
        var toOpt   = appState.getFullSpace().find(toWord);

        if (fromOpt.isEmpty() || toOpt.isEmpty()) return;

        NeighborResult fromResult = neighborService.findNearest(fromOpt.get(), appState.getFullSpace(), currentK);
        NeighborResult toResult   = neighborService.findNearest(toOpt.get(), appState.getFullSpace(), currentK);

        Map<String, Double> merged = new java.util.LinkedHashMap<>();
        fromResult.getNeighbors().forEach(e -> merged.put(e.word(), e.distance()));
        toResult.getNeighbors().forEach(e -> merged.putIfAbsent(e.word(), e.distance()));

        List<String> labels = merged.entrySet().stream()
                .map(e -> "%s (%.4f)".formatted(e.getKey(), e.getValue()))
                .toList();

        appState.setNeighborMap(merged);
        appState.getNeighbors().setAll(labels);
        syncNeighbors(merged);
    }

    public void onKChanged(int k) {
        this.currentK = k;

        if (currentWord != null) {
            doFind();                                  // single-word mode
        } else if (!currentCentroidWords.isEmpty()) {
            onComputeCentroid(currentCentroidWords);   // centroid mode
        }
    }


    public void clearCentroid() {
        this.currentCentroidWords = List.of();
        appState.clearNeighbors();
        syncNeighbors(Map.of());
        rightPanel.setCentroidStatus("—");
    }

    public void onComputeCentroid(List<String> words) {
        try {
            if (!appState.isLoaded() || words.isEmpty()) {
                rightPanel.setCentroidStatus("No words selected");
                return;
            }

            this.currentCentroidWords = List.copyOf(words);

            List<WordVector> wordVectors = words.stream()
                    .map(w -> appState.getFullSpace().find(w))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();

            if (wordVectors.isEmpty()) {
                rightPanel.setCentroidStatus("None of the selected words found in space");
                return;
            }

            // sync K from UI in case slider moved without triggering onKChanged
            if (leftPanel != null) this.currentK = leftPanel.getCurrentK();

            CentroidResult centroid = centroidService.compute(wordVectors);

            List<NeighborResult.Entry> nearest =
                    neighborService.findNearestToVector(centroid.getCentroid(), appState.getFullSpace(), currentK);

            Map<String, Double> neighborMap = new LinkedHashMap<>();
            nearest.forEach(e -> neighborMap.put(e.word(), e.distance()));

            List<String> labels = nearest.stream()
                    .map(e -> "%s (%.4f)".formatted(e.word(), e.distance()))
                    .toList();

            appState.setNeighborMap(neighborMap);
            appState.getNeighbors().setAll(labels);
            syncNeighbors(neighborMap);

            rightPanel.setCentroidStatus("↑ Showing " + currentK + " nearest to centroid of "
                    + centroid.getSourceWords().size() + " words: "
                    + String.join(", ", centroid.getSourceWords()));
        } catch (Exception e) {
            AlertHelper.showError(e.getMessage());
        }
    }


    private void doFind() {
        if (leftPanel != null) this.currentK = leftPanel.getCurrentK();
        appState.getFullSpace().find(currentWord)
                .ifPresent(wv -> applyNeighborResult(neighborService.findNearest(wv, appState.getFullSpace(), currentK)));
    }

    private void applyNeighborResult(NeighborResult result) {
        Map<String, Double> neighborMap = result.getNeighbors().stream()
                .collect(Collectors.toMap(
                        NeighborResult.Entry::word,
                        NeighborResult.Entry::distance,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<String> labels = result.getNeighbors().stream()
                .map(e -> "%s (%.4f)".formatted(e.word(), e.distance()))
                .toList();

        appState.setNeighborMap(neighborMap);
        appState.getNeighbors().setAll(labels);
        syncNeighbors(neighborMap);
    }

    private void syncNeighbors(Map<String, Double> neighbors) {
        cloud2D.setNeighbors(neighbors);
        cloud3D.setNeighbors(neighbors);
    }
}
