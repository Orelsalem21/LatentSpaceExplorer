package controller;

import app.AppState;
import command.CommandHistory;
import command.ReversibleCommand;
import utils.AlertHelper;
import exception.InvalidAxisException;
import exception.WordNotFoundException;
import model.EmbeddingSpace;
import model.ProjectedPoint;
import projection.ProjectionContext;
import view.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

public class ProjectionController {

    private final AppState          appState;
    private final ProjectionContext projectionService;

    private final MainView        mainView;
    private final WordCloud2DView cloud2D;
    private final WordCloud3DView cloud3D;

    private int     currentX = 0;
    private int     currentY = 1;
    private int     currentZ = 2;
    private boolean is3D     = false;

    private List<ProjectedPoint> currentPoints = List.of();

    public ProjectionController(
            AppState appState,
            ProjectionContext projectionService,
            MainView mainView,
            WordCloud2DView cloud2D,
            WordCloud3DView cloud3D
    ) {
        this.appState          = appState;
        this.projectionService = projectionService;
        this.mainView          = mainView;
        this.cloud2D           = cloud2D;
        this.cloud3D           = cloud3D;
    }


    public void wireCallbacks(
            ControlPanelView leftPanel,
            CommandHistory commandHistory,
            BiConsumer<String, String> onCustomAxisNeighbors
    ) {
        leftPanel.setOnAxesChanged(axes -> {
            int[] prev = getCurrentAxes();

            commandHistory.execute(new ReversibleCommand(
                    () -> { leftPanel.setAxes(axes); onAxesChanged(axes); },
                    () -> { leftPanel.setAxes(prev); onAxesChanged(prev); }
            ));
        });

        leftPanel.setOnModeChanged(is3D -> {
            boolean prev = is3DMode();

            commandHistory.execute(new ReversibleCommand(
                    () -> {
                        leftPanel.setMode(is3D);
                        onModeChanged(is3D);
                    },
                    () -> {
                        leftPanel.setMode(prev);
                        onModeChanged(prev);
                    }
            ));
        });

        leftPanel.setOnCustomProjection((from, to) -> {
            if (!appState.isLoaded()) return;

            // Validate before touching history — failed projection must not leave an undo entry
            var fromVec = appState.getFullSpace().find(from);
            var toVec   = appState.getFullSpace().find(to);
            try {
                if (fromVec.isEmpty()) throw new WordNotFoundException(from);
                if (toVec.isEmpty())   throw new WordNotFoundException(to);
            } catch (WordNotFoundException e) {
                AlertHelper.showError(e.getMessage());
                return;
            }

            int[] prevAxes = getCurrentAxes();
            List<String> prevSelected = new ArrayList<>(appState.getSelectedWords());
            List<String> prevNeighborLabels = new ArrayList<>(appState.getNeighbors());
            Map<String, Double> prevNeighborMap = new LinkedHashMap<>(appState.getNeighborMap());

            commandHistory.execute(new ReversibleCommand(
                    () -> {
                        onCustomProjection(from, to);

                        if (onCustomAxisNeighbors != null) {
                            onCustomAxisNeighbors.accept(from, to);
                        }
                    },
                    () -> {
                        leftPanel.setAxes(prevAxes);
                        restorePCAProjection(prevAxes);

                        appState.getSelectedWords().setAll(prevSelected);
                        appState.setNeighborMap(prevNeighborMap);
                        appState.getNeighbors().setAll(prevNeighborLabels);

                        cloud2D.setSelected(new HashSet<>(prevSelected));
                        cloud2D.setNeighbors(prevNeighborMap);

                        cloud3D.setSelected(new HashSet<>(prevSelected));
                        cloud3D.setNeighbors(prevNeighborMap);

                        leftPanel.setAxisProjectionResult("-");
                    }
            ));
        });
    }


    public void onAxesChanged(int[] axes) {
        currentX = axes[0];
        currentY = axes[1];
        currentZ = axes.length > 2 ? axes[2] : 2;

        applyProjectionStrategy();
        reprojectAndDraw();
    }

    public void onModeChanged(boolean is3D) {
        this.is3D = is3D;

        applyProjectionStrategy();

        Set<String> selectedWords = new HashSet<>(appState.getSelectedWords());

        if (is3D) mainView.show3D(); else mainView.show2D();
        reprojectAndDraw();
        activeView().setSelected(selectedWords);
        activeView().setNeighbors(appState.getNeighborMap());
    }

    public void resetToDefaultProjection() {
        currentX = 0;
        currentY = 1;
        currentZ = 2;

        if (is3D) {
            projectionService.useThreeDimensionalProjection(currentZ);
            mainView.show3D();
            activeView().setAxisLabels("PC1", "PC2", "PC3");
            cloud3D.resetView();
        } else {
            projectionService.usePCAProjection();
            mainView.show2D();
            activeView().setAxisLabels("PC1", "PC2", "PC3");
            cloud2D.resetView();
        }

        cloud2D.setSelected(Set.of());
        cloud2D.setNeighbors(Map.of());
        cloud3D.setSelected(Set.of());
        cloud3D.setNeighbors(Map.of());

        appState.getSelectedWords().clear();
        appState.clearNeighbors();
    }

    public void restorePCAProjection(int[] axes) {
        currentX = axes[0];
        currentY = axes[1];
        currentZ = axes.length > 2 ? axes[2] : 2;

        if (is3D) {
            projectionService.useThreeDimensionalProjection(currentZ);
        } else {
            projectionService.usePCAProjection();
        }
        reprojectAndDraw();
    }

    public void onCustomProjection(String fromWord, String toWord) {
        if (!appState.isLoaded()) return;

        var from = appState.getFullSpace().find(fromWord);
        var to   = appState.getFullSpace().find(toWord);

        try {
            if (from.isEmpty()) throw new WordNotFoundException(fromWord);
            if (to.isEmpty())   throw new WordNotFoundException(toWord);
        } catch (WordNotFoundException e) {
            AlertHelper.showError(e.getMessage());
            return;
        }

        try {
            projectionService.useCustomAxisProjection(
                    from.get().getVector(),
                    to.get().getVector(),
                    fromWord,
                    toWord
            );

            List<ProjectedPoint> points =
                    projectionService.project(appState.getFullSpace(), currentX, currentY);
            currentPoints = points;

            String customLabel = fromWord + " → " + toWord;
            String yLabel      = "PC" + (currentY + 1);
            Set<String> axisWords = Set.of(fromWord.toLowerCase(), toWord.toLowerCase());

            appState.getSelectedWords().setAll(fromWord.toLowerCase(), toWord.toLowerCase());

            activeView().setAxisLabels(customLabel, yLabel, "PC" + (currentZ + 1));
            activeView().setPoints(points);
            activeView().setSelected(axisWords);
        } catch (InvalidAxisException e) {
            AlertHelper.showError(e.getMessage());
        }
    }

    public void reprojectAndDraw() {
        if (!appState.isLoaded()) return;

        try {
            EmbeddingSpace projSpace = projectionService.isCustomProjection()
                    ? appState.getFullSpace()
                    : appState.getSpace();

            List<ProjectedPoint> points =
                    projectionService.project(projSpace, currentX, currentY);
            currentPoints = points;

            String xLabel = "PC" + (currentX + 1);
            String yLabel = "PC" + (currentY + 1);
            String zLabel = "PC" + (currentZ + 1);

            appState.setAxisLabels(xLabel, yLabel);

            activeView().setAxisLabels(xLabel, yLabel, zLabel);
            activeView().setPoints(points);

            appState.getSelectedWords().stream().findFirst().ifPresent(word ->
                    findPoint(word).ifPresentOrElse(
                            p -> appState.coordinatesProperty().set(
                                    is3D
                                        ? String.format("%s (X): %.4f   %s (Y): %.4f\n%s (Z): %.4f",
                                                xLabel, p.getX(), yLabel, p.getY(), zLabel, p.getZ())
                                        : String.format("%s (X): %.4f   %s (Y): %.4f",
                                                xLabel, p.getX(), yLabel, p.getY())
                            ),
                            () -> appState.coordinatesProperty().set("")
                    )
            );
        } catch (InvalidAxisException e) {
            AlertHelper.showError(e.getMessage());
        }
    }


    public int[] getCurrentAxes() {
        return new int[]{currentX, currentY, currentZ};
    }

    public Optional<ProjectedPoint> findPoint(String word) {
        return currentPoints.stream().filter(p -> p.getWord().equals(word)).findFirst();
    }

    public boolean is3DMode() {
        return is3D;
    }

    private WordCloudView activeView() {
        return is3D ? cloud3D : cloud2D;
    }

    private void applyProjectionStrategy() {
        if (is3D) {
            projectionService.useThreeDimensionalProjection(currentZ);
        } else if (!projectionService.isCustomProjection()) {
            projectionService.usePCAProjection();
        }
    }
}
