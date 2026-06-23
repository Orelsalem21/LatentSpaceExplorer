package app;

import utils.AlertHelper;
import utils.ButtonStyler;
import command.ChangeMetricCommand;
import command.CommandHistory;
import command.VectorArithmeticCommand;
import controller.*;
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

public class MainApp extends Application {

    private AppState appState;
    private DistanceService distanceService;
    private WordDistanceService wordDistanceService;
    private NearestNeighborService neighborService;
    private CentroidService centroidService;
    private VectorArithmeticService arithmeticService;

    private WordCloud2DView cloud2D;
    private WordCloud3DView cloud3D;
    private ControlPanelView leftPanel;
    private DetailsPanelView rightPanel;
    private MainView mainView;

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
        distanceService = config.distanceService();
        wordDistanceService = new WordDistanceService(distanceService);
        neighborService = config.neighborService();
        arithmeticService = config.arithmeticService();
        centroidService = config.centroidService();
        ProjectionContext projectionService = config.projectionService();

        cloud2D = new WordCloud2DView();
        cloud3D = new WordCloud3DView();
        leftPanel = new ControlPanelView();
        rightPanel = new DetailsPanelView(appState);
        mainView = new MainView(leftPanel, cloud2D, cloud3D, rightPanel);

        commandHistory = new CommandHistory();

        projectionCtrl = new ProjectionController(
                appState, projectionService, mainView, cloud2D, cloud3D
        );
        loaderController = new EmbeddingLoaderController(
                appState, projectionCtrl, new PythonEmbeddingService(), stage
        );
        sessionCtrl = new SessionController(appState, projectionCtrl, stage);
        mainController = new MainController(
                appState, projectionCtrl,
                distanceService,
                cloud2D, cloud3D, leftPanel
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
                sessionCtrl.loadSession(path, leftPanel, mainController));

        leftPanel.setOnMetricChanged(name -> {
            String prev = distanceService.getMetric().name();
            commandHistory.execute(new ChangeMetricCommand(name, prev, metricName -> {
                leftPanel.setMetric(metricName);
                mainController.onMetricChanged(metricName);
                rightPanel.recalculateDistance();

                if (neighborCtrl != null && !appState.getSelectedWords().isEmpty()) {
                    appState.getSelectedWords().stream()
                            .findFirst()
                            .ifPresent(neighborCtrl::onWordSelected);
                }
            }));
        });

        appState.getSelectedWords().addListener((ListChangeListener<String>) change -> {
            List<String> words = new ArrayList<>(appState.getSelectedWords());
            mainController.onSelectionChanged(words);
            syncSelectionToClouds();
        });
    }

    private void wireLoadListener() {
        appState.loadedProperty().addListener((obs, o, loaded) -> {
            if (!loaded) return;

            searchCtrl = new SearchController(appState, cloud2D, cloud3D, commandHistory);
            neighborCtrl = new NeighborController(
                    appState, neighborService, centroidService,
                    commandHistory, cloud2D, cloud3D, rightPanel
            );
            arithmeticCtrl = new VectorArithmeticController(
                    appState, arithmeticService, rightPanel, cloud2D, cloud3D
            );

            wireCanvasSync();
            searchCtrl.wirePanel(rightPanel, neighborCtrl);
            neighborCtrl.wireCallbacks(leftPanel);
            wireLivePanelCallbacks();
        });
    }

    private void wireCanvasSync() {
        cloud2D.setOnWordSelected(word -> {
            searchCtrl.onWordSelected(word);
            neighborCtrl.onWordSelected(word);
            syncSelectionToClouds();
        });
        cloud2D.setOnWordAdded(word -> {
            searchCtrl.onWordAdded(word);
            syncSelectionToClouds();
        });

        cloud3D.setOnWordSelected(word -> {
            searchCtrl.onWordSelected(word);
            neighborCtrl.onWordSelected(word);
            syncSelectionToClouds();
        });
        cloud3D.setOnWordAdded(word -> {
            searchCtrl.onWordAdded(word);
            syncSelectionToClouds();
        });
    }

    private void wireLivePanelCallbacks() {

        rightPanel.setOnDistanceWordsChanged(words -> {
            cloud2D.setDistanceWords(words);
            cloud3D.setDistanceWords(words);
        });

        rightPanel.setOnComputeDistanceMatrix(words -> {
            try {
                // Validate word existence FIRST before computing distance
                wordDistanceService.validateWords(words, appState.getFullSpace());
                List<String> lines = wordDistanceService.compute(words, appState.getFullSpace());
                rightPanel.setDistanceMatrixResult(lines);
            } catch (exception.WordNotFoundException e) {
                AlertHelper.showError(e.getMessage());
            }
        });

        rightPanel.setOnArithmetic(expr ->
                commandHistory.execute(new VectorArithmeticCommand(
                        expr,
                        arithmeticCtrl::onCompute,
                        arithmeticCtrl::clearResult
                ))
        );
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