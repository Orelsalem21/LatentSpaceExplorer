package app;

import utils.AlertHelper;
import utils.ButtonStyler;
import command.CommandHistory;
import command.ReversibleCommand;
import controller.*;
import metric.MetricFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import projection.ProjectionContext;
import service.*;
import view.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainApp extends Application {

    private AppState appState;
    private WordDistanceService wordDistanceService;
    private NearestNeighborService neighborService;
    private CentroidService centroidService;
    private VectorArithmeticService arithmeticService;

    private WordCloud2DView cloud2D;
    private WordCloud3DView cloud3D;
    private ControlPanelView leftPanel;
    private DetailsPanelView rightPanel;
    private MainView mainView;

    private String                        currentMetricName = MetricFactory.DEFAULT_METRIC;
    private CommandHistory               commandHistory;
    private MainController               mainController;
    private EmbeddingLoaderController    loaderController;
    private SessionController            sessionCtrl;
    private ProjectionController         projectionCtrl;
    private SearchController             searchCtrl;
    private NeighborController           neighborCtrl;
    private VectorArithmeticController   arithmeticCtrl;

    @Override
    public void start(Stage primaryStage) {
        build(primaryStage);
        wireProjection();
        wireCommands();
        wireStaticCallbacks();
        wireInteractionControllers();
        wireLoadListener();

        Scene scene = new Scene(mainView.getRoot(), 1280, 800);
        primaryStage.setTitle("LatentSpace Explorer");
        primaryStage.setScene(scene);
        primaryStage.show();

        loaderController.setOnPythonStart(() ->
                cloud2D.setStatusMessage("Generating embeddings with Python, please wait..."));
        loaderController.setOnPythonDone(() ->
                cloud2D.setStatusMessage("Load a file to display the word cloud"));
        Platform.runLater(loaderController::autoLoadOnStartup);
    }

    private void build(Stage stage) {
        appState = new AppState();

        AppConfig config = new AppConfig();
        DistanceService distanceService = config.distanceService();
        wordDistanceService = new WordDistanceService(distanceService);
        neighborService = config.neighborService();
        arithmeticService = config.arithmeticService();
        centroidService = config.centroidService();
        ProjectionContext projectionService = config.projectionService();

        cloud2D = new WordCloud2DView();
        cloud3D = new WordCloud3DView();
        leftPanel = new ControlPanelView();
        rightPanel = new DetailsPanelView();
        mainView = new MainView(leftPanel, cloud2D, cloud3D, rightPanel);

        commandHistory = new CommandHistory();

        projectionCtrl = new ProjectionController(
                appState, projectionService, mainView, cloud2D, cloud3D
        );
        loaderController = new EmbeddingLoaderController(
                appState, projectionCtrl, new PythonEmbeddingService(), stage, commandHistory
        );
        sessionCtrl = new SessionController(appState, projectionCtrl, stage, commandHistory);
        mainController = new MainController(
                appState, projectionCtrl,
                distanceService,
                cloud2D, cloud3D, leftPanel
        );

        searchCtrl = new SearchController(appState, cloud2D, cloud3D, commandHistory, projectionCtrl);
        neighborCtrl = new NeighborController(
                appState, neighborService, centroidService,
                commandHistory, cloud2D, cloud3D, rightPanel
        );
        arithmeticCtrl = new VectorArithmeticController(
                appState, arithmeticService, rightPanel, cloud2D, cloud3D
        );
    }

    private void wireProjection() {
        projectionCtrl.wireCallbacks(leftPanel, commandHistory,
                (from, to) -> {
                    if (neighborCtrl != null) neighborCtrl.onCustomAxisWords(from, to);
                });
    }

    private void wireCommands() {
        Button undoBtn = new Button("↩ Undo");
        Button redoBtn = new Button("↪ Redo");
        ButtonStyler.style(undoBtn);
        ButtonStyler.style(redoBtn);
        undoBtn.setDisable(true);
        redoBtn.setDisable(true);

        commandHistory.setOnChanged(() -> {
            undoBtn.setDisable(!commandHistory.canUndo());
            redoBtn.setDisable(!commandHistory.canRedo());
        });
        undoBtn.setOnAction(e -> commandHistory.undo());
        redoBtn.setOnAction(e -> commandHistory.redo());

        Button resetBtn = new Button("Reset");
        ButtonStyler.styleDanger(resetBtn);
        resetBtn.setOnAction(e -> doReset());

        mainView.addToToolbar(undoBtn, redoBtn);
        mainView.addToToolbarRight(resetBtn);
    }

    private void wireStaticCallbacks() {
        leftPanel.setOnLoadFile(loaderController::onLoadFile);
        leftPanel.setOnSaveAs(sessionCtrl::onSaveAs);
        loaderController.setOnSessionFile(path ->
                sessionCtrl.loadSession(path, leftPanel, mainController, neighborCtrl));

        leftPanel.setOnMetricChanged(name -> {
            String prev = currentMetricName;
            commandHistory.execute(new ReversibleCommand(
                    () -> applyMetric(name),
                    () -> applyMetric(prev)
            ));
        });

        appState.getSelectedWords().addListener((ListChangeListener<String>) change -> {
            List<String> words = new ArrayList<>(appState.getSelectedWords());
            mainController.onSelectionChanged(words);
            syncSelectionToClouds();
        });
    }

    private void applyMetric(String metricName) {
        currentMetricName = metricName;
        leftPanel.setMetric(metricName);
        mainController.onMetricChanged(metricName);
        rightPanel.recalculateDistance();

        if (neighborCtrl != null) {
            neighborCtrl.restoreNeighborState(new ArrayList<>(appState.getSelectedWords()));
        }
    }

    private void wireInteractionControllers() {
        wireCanvasSync();
        searchCtrl.wirePanel(rightPanel, neighborCtrl);
        wireLivePanelCallbacks();
    }

    private void wireLoadListener() {
        appState.loadedProperty().addListener((obs, o, loaded) -> {
            if (!loaded) return;

            neighborCtrl.wireCallbacks(leftPanel);
        });
    }

    private void wireCanvasSync() {
        cloud2D.setOnWordSelected(word -> {
            searchCtrl.onWordSelected(word);
            neighborCtrl.onWordSelected(word);
            syncSelectionToClouds();
        });

        cloud3D.setOnWordSelected(word -> {
            searchCtrl.onWordSelected(word);
            neighborCtrl.onWordSelected(word);
            syncSelectionToClouds();
        });
    }

    private void wireLivePanelCallbacks() {

        rightPanel.setOnDistanceWordsChanged(words -> {
            cloud2D.setDistanceWords(words);
            cloud3D.setDistanceWords(words);
        });

        rightPanel.setOnComputeDistanceMatrix(words -> commandHistory.execute(new ReversibleCommand(
            () -> {
                try {
                    wordDistanceService.validateWords(words, appState.getFullSpace());
                    List<String> lines = wordDistanceService.compute(words, appState.getFullSpace());
                    rightPanel.setDistanceMatrixResult(lines);
                    var wordSet = new HashSet<>(words);
                    cloud2D.setDistanceWords(wordSet);
                    cloud3D.setDistanceWords(wordSet);
                } catch (exception.WordNotFoundException e) {
                    AlertHelper.showError(e.getMessage());
                }
            },
            () -> {
                rightPanel.setDistanceMatrixResult(List.of());
                cloud2D.setDistanceWords(Set.of());
                cloud3D.setDistanceWords(Set.of());
            }
        )));

        rightPanel.setOnArithmetic(words -> {
            String expr = String.join(",", words);
            commandHistory.execute(new ReversibleCommand(
                    () -> arithmeticCtrl.onCompute(expr),
                    arithmeticCtrl::clearResult
            ));
        });
    }

    private void syncSelectionToClouds() {
        var selected = new HashSet<>(appState.getSelectedWords());
        cloud2D.setSelected(selected);
        cloud3D.setSelected(selected);
    }

    private void doReset() {
        mainController.onReset();
        leftPanel.resetCustomAxis();
        rightPanel.resetAll();
        commandHistory.clear();
        if (neighborCtrl != null) neighborCtrl.reset();
    }


    public static void main(String[] args) {
        launch(args);
    }
}