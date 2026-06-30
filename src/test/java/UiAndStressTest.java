import app.AppState;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import loader.JsonEmbeddingRepository;
import metric.CosineDistance;
import model.*;
import org.junit.jupiter.api.*;
import projection.PCAProjection;
import service.CentroidService;
import service.DistanceService;
import service.NearestNeighborService;
import projection.ProjectionContext;
import view.WordCloud2DView;
import view.WordCloud3DView;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LatentSpaceExplorer - UI smoke, stress and real-data load tests")
class UiAndStressTest {
    private static final double EPS = 1e-9;
    private static EmbeddingSpace fullSpace;
    private static EmbeddingSpace pcaSpace;

    @BeforeAll
    static void loadDataAndStartFx() throws Exception {
        JsonEmbeddingRepository repository = new JsonEmbeddingRepository();
        fullSpace = repository.load(resolveDataFile("full_vectors.json"));
        pcaSpace = repository.load(resolveDataFile("pca_vectors.json"));
        startFxToolkit();
    }

    private static Path resolveDataFile(String fileName) {
        List<Path> candidates = List.of(
                Path.of("python", fileName),
                Path.of("LatentSpaceExplorer", "python", fileName),
                Path.of("src", "main", "resources", "loader", fileName),
                Path.of("target", "classes", "loader", fileName),
                Path.of("src", "main", "resources", "repository", fileName),
                Path.of("target", "classes", "repository", fileName)
        );
        return candidates.stream().filter(Files::exists).findFirst()
                .orElseThrow(() -> new AssertionError("Missing data file: " + fileName));
    }

    private static void startFxToolkit() throws Exception {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX toolkit did not start");
        } catch (IllegalStateException alreadyStarted) {
            // JavaFX already started by another test class.
        }
    }

    private static void runFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable t) { thrown.set(t); }
            finally { latch.countDown(); }
        });
        assertTrue(latch.await(20, TimeUnit.SECONDS), "JavaFX action timed out");
        if (thrown.get() != null) throw new AssertionError(thrown.get());
    }

    private static WordVector word(EmbeddingSpace space, String value) {
        return space.find(value).orElseThrow(() -> new AssertionError("Missing word: " + value));
    }

    private static List<ProjectedPoint> tinyPoints() {
        return List.of(
                new ProjectedPoint("alpha", -1, -1, -1),
                new ProjectedPoint("beta", 1, 1, 1),
                new ProjectedPoint("gamma", 0, 0, 0),
                new ProjectedPoint("delta", 0.5, -0.5, 0.25)
        );
    }

    private static Canvas findCanvas(Pane root) {
        for (Node node : root.getChildrenUnmodifiable()) {
            if (node instanceof Canvas canvas) return canvas;
            if (node instanceof Pane pane) {
                Canvas nested = findCanvas(pane);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static Canvas prepare2DCanvas(WordCloud2DView view, double width, double height) {
        Pane root = view.getRoot();
        root.setMinSize(width, height);
        root.setPrefSize(width, height);
        root.resize(width, height);
        root.layout();
        Canvas canvas = findCanvas(root);
        assertNotNull(canvas, "WordCloud2DView should contain a Canvas");
        assertTrue(canvas.getWidth() >= 0.0);
        assertTrue(canvas.getHeight() >= 0.0);
        return canvas;
    }

    private static Object privateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static double privateDouble(Object target, String fieldName) throws Exception {
        return ((Number) privateField(target, fieldName)).doubleValue();
    }

    private static void fireScroll(Canvas canvas, double deltaY) {
        ScrollEvent event = new ScrollEvent(
                ScrollEvent.SCROLL,
                450, 325, 450, 325,
                false, false, false, false,
                false, false,
                0, deltaY,
                0, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                0,
                null
        );
        canvas.fireEvent(event);
    }

    private static void fireMouse(Canvas canvas,
                                  boolean ctrl) {
        MouseEvent event = new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                450, 325, 450, 325,
                MouseButton.PRIMARY,
                1,
                false, ctrl, false, false,
                MouseButton.PRIMARY == MouseButton.PRIMARY,
                MouseButton.PRIMARY == MouseButton.MIDDLE,
                MouseButton.PRIMARY == MouseButton.SECONDARY,
                false,
                false,
                false,
                new PickResult(canvas, 450, 325)
        );
        canvas.fireEvent(event);
    }

    @Nested
    @DisplayName("Real-data smoke and regression")
    class RealDataSmoke {
        @Test
        void realFilesLoadedForUiAndStressTests() {
            assertEquals(5000, fullSpace.size());
            assertEquals(5000, pcaSpace.size());
            assertEquals(100, fullSpace.getDimension());
            assertEquals(50, pcaSpace.getDimension());
            assertTrue(fullSpace.contains("king"));
            assertTrue(pcaSpace.contains("king"));
        }

        @Test
        @Timeout(20)
        void repeatedRealNearestNeighborSearchesStayStable() {
            NearestNeighborService service = new NearestNeighborService(new DistanceService(new CosineDistance()));
            for (WordVector query : fullSpace.getVectors().subList(0, 120)) {
                NeighborResult result = service.findNearest(query, fullSpace, 25);
                assertEquals(25, result.getNeighbors().size());
                assertTrue(result.getNeighbors().stream().allMatch(e -> Double.isFinite(e.distance())));
                assertTrue(result.getNeighbors().stream().noneMatch(e -> e.word().equals(query.getWord())));
            }
        }

        @Test
        @Timeout(20)
        void repeatedCentroidCalculationsStayFiniteAndDoNotMutateSources() {
            CentroidService service = new CentroidService();
            List<WordVector> words = fullSpace.getVectors();
            double original = words.getFirst().getVector()[0];
            for (int start = 0; start < 300; start += 5) {
                double[] centroid = service.compute(words.subList(start, start + 25)).getCentroid();
                assertEquals(100, centroid.length);
                for (double value : centroid) assertTrue(Double.isFinite(value));
            }
            assertEquals(original, words.getFirst().getVector()[0], 0.0);
        }

        @Test
        @Timeout(20)
        void projectionOfFullPcaFileIsStable() throws Exception {
            ProjectionContext service = new ProjectionContext(new PCAProjection());
            assertEquals(pcaSpace.size(), service.project(pcaSpace, 0, 1).size());
            service.useThreeDimensionalProjection(2);
            List<ProjectedPoint> points3D = service.project(pcaSpace, 0, 1);
            assertEquals(pcaSpace.size(), points3D.size());
            assertTrue(points3D.stream().allMatch(p -> Double.isFinite(p.getX()) && Double.isFinite(p.getY()) && Double.isFinite(p.getZ())));
        }
    }

    @Nested
    @DisplayName("AppState regression")
    class AppStateRegression {
        @Test
        void appStateSelectionNeighborsAndFullSpaceRemainConsistent() {
            AppState state = new AppState();
            state.setFullSpace(fullSpace);
            state.setSpace(pcaSpace);
            state.getSelectedWords().setAll("king", "queen");
            state.setNeighborMap(Map.of("prince", 0.1, "princess", 0.2));

            assertSame(fullSpace, state.getFullSpace());
            assertSame(pcaSpace, state.getSpace());
            assertEquals(List.of("king", "queen"), state.getSelectedWords());
            assertEquals(2, state.getNeighborMap().size());
            assertTrue(state.getNeighborMap().containsKey("prince"));

            state.clearNeighbors();
            assertTrue(state.getNeighborMap().isEmpty());
        }
    }

    @Nested
    @DisplayName("2D UI interaction and rendering stress")
    class WordCloud2DInteractionStress {
        @Test
        @Timeout(15)
        void zoomPanResetAndRedrawStayFinite() throws Exception {
            runFx(() -> {
                WordCloud2DView view = new WordCloud2DView();
                Canvas canvas = prepare2DCanvas(view, 900, 650);
                view.setPoints(tinyPoints());
                view.setSelected(Set.of("alpha"));
                view.setNeighbors(Map.of("beta", 0.1, "gamma", 0.2));

                for (int i = 0; i < 200; i++) fireScroll(canvas, i % 2 == 0 ? 120 : -120);
                view.redraw();
                try {
                    assertTrue(Double.isFinite(privateDouble(view, "scale")));
                    assertTrue(privateDouble(view, "scale") > 0.0);
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                view.resetView();
                try {
                    assertEquals(1.0, privateDouble(view, "scale"), EPS);
                    assertEquals(0.0, privateDouble(view, "offsetX"), EPS);
                    assertEquals(0.0, privateDouble(view, "offsetY"), EPS);
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            });
        }

        @Test
        @Timeout(15)
        void rapidMouseActionsCallSelectionHandlers() throws Exception {
            runFx(() -> {
                WordCloud2DView view = new WordCloud2DView();
                Canvas canvas = prepare2DCanvas(view, 900, 650);
                view.setPoints(tinyPoints());

                List<String> selected = new ArrayList<>();
                view.setOnWordSelected(selected::add);

                for (int i = 0; i < 100; i++) {
                    fireMouse(canvas, false);
                    fireMouse(canvas, true);
                }
                assertFalse(selected.isEmpty(), "Click should select a word");
            });
        }

        @Test
        @Timeout(20)
        void repeatedRenderingOfTenThousandPointsIsStable() throws Exception {
            runFx(() -> {
                WordCloud2DView view = new WordCloud2DView();
                prepare2DCanvas(view, 1200, 800);
                List<ProjectedPoint> many = IntStream.range(0, 10_000)
                        .mapToObj(i -> new ProjectedPoint("w" + i, Math.sin(i * 0.01), Math.cos(i * 0.01), Math.sin(i * 0.02)))
                        .toList();
                assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
                    view.setPoints(many);
                    for (int i = 0; i < 40; i++) {
                        view.setSelected(Set.of("w" + i));
                        view.setNeighbors(Map.of("w" + (i + 1), 0.1, "w" + (i + 2), 0.2));
                        view.redraw();
                    }
                });
            });
        }
    }

    @Nested
    @DisplayName("3D UI smoke and load interaction")
    class WordCloud3DInteractionStress {
        @Test
        @Timeout(15)
        void threeDViewSurvivesRepeatedSetPointsHighlightsNeighborsAndReset() throws Exception {
            runFx(() -> {
                WordCloud3DView view = new WordCloud3DView();
                Pane root = view.getRoot();
                root.resize(1000, 700);
                view.setPoints(tinyPoints());
                view.setSelected(Set.of("alpha"));
                view.setNeighbors(Map.of("beta", 0.1, "gamma", 0.2));
                view.setAxisLabels("PC1", "PC2", "PC3");
                for (int i = 0; i < 100; i++) {
                    view.resetView();
                    view.setSelected(Set.of(i % 2 == 0 ? "alpha" : "beta"));
                    view.setNeighbors(Map.of("gamma", (double) i));
                }
                assertFalse(root.getChildrenUnmodifiable().isEmpty());
            });
        }
    }
}
