package controller;

import app.AppState;
import exception.EmbeddingLoadException;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import loader.EmbeddingRepository;
import loader.EmbeddingRepositoryFactory;
import loader.JsonEmbeddingRepository;
import utils.AlertHelper;
import service.PythonEmbeddingService;
import java.io.File;
import java.nio.file.Path;

public class EmbeddingLoaderController {

    private final AppState               appState;
    private final ProjectionController   projectionController;
    private final PythonEmbeddingService pythonService;
    private final Stage                  stage;

    private final EmbeddingRepository jsonRepository = new JsonEmbeddingRepository();
    private java.util.function.Consumer<java.nio.file.Path> onSessionFile = p -> {};
    private Runnable onPythonStart = () -> {};
    private Runnable onPythonDone  = () -> {};

    public EmbeddingLoaderController(AppState appState, ProjectionController projectionController, PythonEmbeddingService pythonService, Stage stage) {
        this.appState             = appState;
        this.projectionController = projectionController;
        this.pythonService        = pythonService;
        this.stage                = stage;
    }

    public void setOnSessionFile(java.util.function.Consumer<Path> handler) { this.onSessionFile = handler; }
    public void setOnPythonStart(Runnable handler) { this.onPythonStart = handler; }
    public void setOnPythonDone(Runnable handler)  { this.onPythonDone  = handler; }

    public void onLoadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Embeddings");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JSON files", "*.json")
        );
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        Path path = file.toPath();
        if (path.getFileName().toString().endsWith(".session.json")) {
            onSessionFile.accept(path);
        } else {
            loadFromPath(path);
        }
    }

    public void loadFromPath(Path path) {
        try {
            EmbeddingRepository repo = EmbeddingRepositoryFactory.forFile(path);
            var space = repo.load(path);
            appState.setFullSpace(null);
            appState.setSpace(space);
            projectionController.resetToDefaultProjection();
            projectionController.reprojectAndDraw();
        } catch (EmbeddingLoadException e) {
            AlertHelper.showError(e.getMessage());
        }
    }

    public void autoLoadOnStartup() {
        Path dataDir = Path.of("python").toAbsolutePath();
        Path pcaFile = dataDir.resolve("pca_vectors.json");
        if (pcaFile.toFile().exists()) {
            loadWithFullSpace(pcaFile, dataDir.resolve("full_vectors.json"));
        } else {
            Platform.runLater(onPythonStart);
            new Thread(() -> {
                try {
                    pythonService.run(Path.of("python", "embedder.py").toAbsolutePath(), dataDir);
                    Platform.runLater(() -> {
                        onPythonDone.run();
                        loadWithFullSpace(
                                dataDir.resolve("pca_vectors.json"),
                                dataDir.resolve("full_vectors.json")
                        );
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        onPythonDone.run();
                        AlertHelper.showError(e.getMessage());
                    });
                }
            }).start();
        }
    }

    private void loadWithFullSpace(Path pcaFile, Path fullFile) {
        try {
            var fullSpace = fullFile.toFile().exists() ? jsonRepository.load(fullFile) : null;
            var pcaSpace  = jsonRepository.load(pcaFile);
            if (fullSpace != null) appState.setFullSpace(fullSpace);
            appState.setSpace(pcaSpace);

            if (appState.getFullSpace() != null
                    && !appState.getFullSpace().getVectors().isEmpty()) {

                appState.getFullSpace().getVectors().getFirst().getDimension();
            }
            projectionController.resetToDefaultProjection();
            projectionController.reprojectAndDraw();
        } catch (EmbeddingLoadException e) {
            AlertHelper.showError(e.getMessage());
        }
    }
}
