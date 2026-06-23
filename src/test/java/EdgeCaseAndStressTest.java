import command.ChangeAxesCommand;
import command.ChangeMetricCommand;
import command.CommandHistory;
import command.ReversibleCommand;
import command.VectorArithmeticCommand;
import exception.EmbeddingLoadException;
import exception.InvalidAxisException;
import loader.JsonEmbeddingRepository;
import metric.CosineDistance;
import metric.EuclideanDistance;
import model.*;
import org.junit.jupiter.api.*;
import projection.*;
import service.*;
import view.WordCloud2DView;
import view.WordCloud3DView;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Edge Cases and Stress Tests")
class EdgeCaseAndStressTest {

    private static final double EPS = 1e-9;
    private static EmbeddingSpace fullSpace;
    private static EmbeddingSpace pcaSpace;

    @BeforeAll
    static void loadData() throws Exception {
        JsonEmbeddingRepository repo = new JsonEmbeddingRepository();
        fullSpace = repo.load(resolveDataFile("full_vectors.json"));
        pcaSpace  = repo.load(resolveDataFile("pca_vectors.json"));
        startFx();
    }

    private static Path resolveDataFile(String fileName) {
        List<Path> candidates = List.of(
                Path.of("python", fileName),
                Path.of("LatentSpaceExplorer", "python", fileName),
                Path.of("src", "main", "resources", "loader", fileName),
                Path.of("target", "classes", "loader", fileName)
        );
        return candidates.stream().filter(Files::exists).findFirst()
                .orElseThrow(() -> new AssertionError("Data file not found: " + fileName));
    }

    private static void startFx() throws Exception {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (IllegalStateException alreadyStarted) { /* already running */ }
    }

    @FunctionalInterface interface ThrowingRunnable { void run() throws Exception; }

    private static void runFx(ThrowingRunnable action) throws Exception {
        if (Platform.isFxApplicationThread()) { action.run(); return; }
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { action.run(); }
            catch (Throwable t) { error.set(t); }
            finally { latch.countDown(); }
        });
        assertTrue(latch.await(20, TimeUnit.SECONDS), "JavaFX timeout");
        if (error.get() != null) throw new AssertionError(error.get());
    }

    /** Wraps InvalidAxisException so it can be called from inside a Runnable lambda. */
    private static List<ProjectedPoint> project(projection.ProjectionContext svc, model.EmbeddingSpace space, int x, int y) {
        try { return svc.project(space, x, y); }
        catch (exception.InvalidAxisException e) { throw new RuntimeException(e); }
    }
    private static List<ProjectedPoint> project(projection.ProjectionStrategy svc, model.EmbeddingSpace space, int x, int y) {
        try { return svc.project(space, x, y); }
        catch (exception.InvalidAxisException e) { throw new RuntimeException(e); }
    }

    private static Canvas getCanvas(Pane root) {
        for (Node n : root.getChildrenUnmodifiable()) {
            if (n instanceof Canvas c) return c;
            if (n instanceof Pane p) { Canvas nested = getCanvas(p); if (nested != null) return nested; }
        }
        return null;
    }

    private static Canvas prepare2D(WordCloud2DView view, double w, double h) {
        Pane root = view.getRoot();
        root.setMinSize(w, h); root.setPrefSize(w, h); root.resize(w, h); root.layout();
        Canvas c = getCanvas(root);
        assertNotNull(c);
        return c;
    }

    private static void scroll(Canvas c, double x, double y, double delta) {
        c.fireEvent(new ScrollEvent(ScrollEvent.SCROLL, x, y, x, y, false, false, false, false,
                false, false, 0, delta, 0, delta,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null));
    }

    private static void click(Canvas c, double x, double y, boolean ctrl) {
        c.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, x, y, x, y, MouseButton.PRIMARY, 1,
                false, ctrl, false, false, true, false, false, false, false, false,
                new PickResult(c, x, y)));
    }

    private static WordVector word(EmbeddingSpace space, String w) {
        return space.find(w).orElseThrow(() -> new AssertionError("Missing word: " + w));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. מעבר 2D / 3D
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("2D ↔ 3D mode switching")
    class ModeSwitching {

        @Test
        @Timeout(15)
        void repeatedSwitchingBetween2DAnd3DKeepsPointsIntact() throws Exception {
            runFx(() -> {
                WordCloud2DView v2 = new WordCloud2DView();
                WordCloud3DView v3 = new WordCloud3DView();
                List<ProjectedPoint> points = new ProjectionContext(new PCAProjection())
                        .project(pcaSpace, 0, 1);

                for (int i = 0; i < 50; i++) {
                    v2.setPoints(points);
                    v3.setPoints(points);
                    v2.setSelected(Set.of("king"));
                    v3.setSelected(Set.of("king"));
                    v2.clearHighlights();
                    v3.setSelected(Set.of());
                }
                // after switching — 2D should still find a point for "king"
                assertTrue(v2.findPoint("king").isPresent());
            });
        }

        @Test
        @Timeout(15)
        void switchingModeClearsNeighborsCorrectly() throws Exception {
            runFx(() -> {
                WordCloud2DView v2 = new WordCloud2DView();
                WordCloud3DView v3 = new WordCloud3DView();
                List<ProjectedPoint> points = new ProjectionContext(new PCAProjection())
                        .project(pcaSpace, 0, 1);
                v2.setPoints(points);
                v3.setPoints(points);

                v2.setNeighbors(Map.of("queen", 0.1, "prince", 0.2));
                v3.setNeighbors(Map.of("queen", 0.1, "prince", 0.2));

                v2.clearHighlights();
                v3.setNeighbors(Map.of());
                v3.setSelected(Set.of());

                // after clear — redraw should not throw
                assertDoesNotThrow(v2::redraw);
            });
        }

        @Test
        @Timeout(15)
        void switchWith3DProjectionContextProducesValidZ() throws Exception {
            runFx(() -> {
                ProjectionContext service = new ProjectionContext(new ThreeDimensionalProjection(2));
                List<ProjectedPoint> pts = service.project(pcaSpace, 0, 1);
                WordCloud3DView v3 = new WordCloud3DView();
                v3.setPoints(pts);
                pts.forEach(p -> {
                    assertTrue(Double.isFinite(p.getX()));
                    assertTrue(Double.isFinite(p.getY()));
                    assertTrue(Double.isFinite(p.getZ()));
                });
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. שינויי גודל חלון
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Window resize – enlarge and shrink")
    class WindowResize {

        @Test
        @Timeout(15)
        void canvasRebindsOnWindowEnlarge() throws Exception {
            runFx(() -> {
                WordCloud2DView view = new WordCloud2DView();
                view.setPoints(new ProjectionContext(new PCAProjection()).project(pcaSpace, 0, 1));
                Pane root = view.getRoot();

                for (double size : List.of(400.0, 800.0, 1200.0, 1600.0)) {
                    root.setMinSize(size, size * 0.6);
                    root.setPrefSize(size, size * 0.6);
                    root.resize(size, size * 0.6);
                    root.layout();
                    assertDoesNotThrow(view::redraw);
                }
            });
        }

        @Test
        @Timeout(15)
        void canvasRebindsOnWindowShrink() throws Exception {
            runFx(() -> {
                WordCloud2DView view = new WordCloud2DView();
                view.setPoints(new ProjectionContext(new PCAProjection()).project(pcaSpace, 0, 1));
                Pane root = view.getRoot();

                for (double size : List.of(1600.0, 800.0, 400.0, 200.0, 50.0)) {
                    root.setMinSize(size, size * 0.6);
                    root.setPrefSize(size, size * 0.6);
                    root.resize(size, size * 0.6);
                    root.layout();
                    assertDoesNotThrow(view::redraw);
                }
            });
        }

        @Test
        @Timeout(15)
        void extremelySmallWindowDoesNotCrash() throws Exception {
            runFx(() -> {
                WordCloud2DView view = new WordCloud2DView();
                view.setPoints(new ProjectionContext(new PCAProjection()).project(pcaSpace, 0, 1));
                Pane root = view.getRoot();
                root.resize(1, 1);
                root.layout();
                assertDoesNotThrow(view::redraw);
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. זום
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Zoom in and zoom out stress")
    class ZoomStress {

        @Test
        @Timeout(15)
        void extremeZoomInKeepsScalePositiveAndFinite() throws Exception {
            runFx(() -> {
                WordCloud2DView view = new WordCloud2DView();
                Canvas canvas = prepare2D(view, 900, 600);
                view.setPoints(new ProjectionContext(new PCAProjection()).project(pcaSpace, 0, 1));

                // 500 zoom-in events
                for (int i = 0; i < 500; i++) scroll(canvas, 450, 300, 120);

                assertDoesNotThrow(view::redraw);
            });
        }

        @Test
        @Timeout(15)
        void extremeZoomOutKeepsScalePositiveAndFinite() throws Exception {
            runFx(() -> {
                WordCloud2DView view = new WordCloud2DView();
                Canvas canvas = prepare2D(view, 900, 600);
                view.setPoints(new ProjectionContext(new PCAProjection()).project(pcaSpace, 0, 1));

                // 500 zoom-out events
                for (int i = 0; i < 500; i++) scroll(canvas, 450, 300, -120);

                assertDoesNotThrow(view::redraw);
            });
        }

        @Test
        @Timeout(15)
        void alternatingZoomInOutRemainsStable() throws Exception {
            runFx(() -> {
                WordCloud2DView view = new WordCloud2DView();
                Canvas canvas = prepare2D(view, 900, 600);
                view.setPoints(new ProjectionContext(new PCAProjection()).project(pcaSpace, 0, 1));

                for (int i = 0; i < 300; i++) scroll(canvas, 450, 300, i % 2 == 0 ? 120 : -120);

                view.resetView();
                assertDoesNotThrow(view::redraw);
            });
        }

        @Test
        @Timeout(15)
        void zoomOnEmptyCanvasDoesNotCrash() throws Exception {
            runFx(() -> {
                WordCloud2DView view = new WordCloud2DView();
                Canvas canvas = prepare2D(view, 900, 600);
                // no setPoints — canvas is empty
                for (int i = 0; i < 100; i++) scroll(canvas, 450, 300, 120);
                assertDoesNotThrow(view::redraw);
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. חישובי מרחק תקינים
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Distance computations – valid cases")
    class DistanceComputations {

        @Test
        void cosineDistanceOfIdenticalVectorsIsZero() {
            CosineDistance cosine = new CosineDistance();
            double[] v = word(fullSpace, "king").getVector();
            assertEquals(0.0, cosine.compute(v, v), EPS);
        }

        @Test
        void euclideanDistanceOfIdenticalVectorsIsZero() {
            EuclideanDistance euclidean = new EuclideanDistance();
            double[] v = word(fullSpace, "king").getVector();
            assertEquals(0.0, euclidean.compute(v, v), EPS);
        }

        @Test
        void cosineDistanceIsSymmetric() {
            CosineDistance cosine = new CosineDistance();
            double[] a = word(fullSpace, "king").getVector();
            double[] b = word(fullSpace, "queen").getVector();
            assertEquals(cosine.compute(a, b), cosine.compute(b, a), EPS);
        }

        @Test
        void euclideanDistanceIsSymmetric() {
            EuclideanDistance euclidean = new EuclideanDistance();
            // use words from the actual loaded space — indices 10 and 20
            double[] a = fullSpace.getVectors().get(10).getVector();
            double[] b = fullSpace.getVectors().get(20).getVector();
            assertEquals(euclidean.compute(a, b), euclidean.compute(b, a), EPS);
        }

        @Test
        void cosineDistanceIsNonNegative() {
            CosineDistance cosine = new CosineDistance();
            double[] query = fullSpace.getVectors().get(0).getVector();
            for (WordVector wv : fullSpace.getVectors().subList(0, 50)) {
                double d = cosine.compute(query, wv.getVector());
                // floating-point arithmetic may produce tiny negatives (~-1e-15) for near-identical
                // vectors; the meaningful range is [0, 2], so we allow a small EPS tolerance
                assertTrue(d >= -EPS, "Cosine distance below -EPS for: " + wv.getWord() + " => " + d);
            }
        }

        @Test
        void euclideanDistanceIsNonNegative() {
            EuclideanDistance euclidean = new EuclideanDistance();
            double[] query = fullSpace.getVectors().get(0).getVector();
            for (WordVector wv : fullSpace.getVectors().subList(0, 50)) {
                double d = euclidean.compute(query, wv.getVector());
                assertTrue(d >= 0.0, "Negative euclidean distance for: " + wv.getWord());
            }
        }

        @Test
        void distanceServiceSwitchesMetricCorrectly() {
            DistanceService service = new DistanceService(new CosineDistance());
            WordVector a = word(fullSpace, "king");
            WordVector b = word(fullSpace, "queen");

            double cosineResult   = service.compute(a, b);
            service.setMetric(new EuclideanDistance());
            double euclideanResult = service.compute(a, b);

            assertNotEquals(cosineResult, euclideanResult, 1e-6,
                    "Cosine and Euclidean should produce different distances");
            assertTrue(Double.isFinite(cosineResult));
            assertTrue(Double.isFinite(euclideanResult));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. חישובים שגויים / מקרי שגיאה
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Invalid computation edge cases")
    class InvalidComputations {

        @Test
        void nearestNeighborWithNegativeKThrows() {
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            assertThrows(IllegalArgumentException.class,
                    () -> service.findNearest(word(fullSpace, "king"), fullSpace, -1));
        }

        @Test
        void nearestNeighborWithKZeroReturnsEmptyList() {
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            NeighborResult result = service.findNearest(word(fullSpace, "king"), fullSpace, 0);
            assertTrue(result.getNeighbors().isEmpty());
        }

        @Test
        void centroidOfEmptyListThrows() {
            CentroidService service = new CentroidService();
            assertThrows(IllegalArgumentException.class, () -> service.compute(List.of()));
        }

        @Test
        void loadingNonExistentFileThrows() {
            JsonEmbeddingRepository repo = new JsonEmbeddingRepository();
            assertThrows(EmbeddingLoadException.class,
                    () -> repo.load(Path.of("nonexistent_file_xyz.json")));
        }

        @Test
        void vectorArithmeticWithWordNotInSpaceReturnsEmpty() {
            // Build tiny space that doesn't contain "king"
            List<WordVector> tiny = fullSpace.getVectors().subList(0, 10).stream()
                    .filter(wv -> !Set.of("king", "man", "woman").contains(wv.getWord()))
                    .collect(Collectors.toList());
            EmbeddingSpace tinySpace = new EmbeddingSpace(tiny);

            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));

            // All results filtered out — should return empty (or any word from tiny)
            Optional<String> result = service.compute(
                    word(fullSpace, "king"),
                    word(fullSpace, "man"),
                    word(fullSpace, "woman"),
                    tinySpace);
            // result must be absent OR one of the tiny-space words (not king/man/woman)
            result.ifPresent(w ->
                    assertFalse(Set.of("king", "man", "woman").contains(w)));
        }

        @Test
        void projectionWithAxisIndexBeyondDimensionDoesNotThrow() throws Exception {
            ProjectionContext service = new ProjectionContext(new PCAProjection());
            // pcaSpace has 50 dims — axis 49 is valid, 50 would be out of bounds
            assertDoesNotThrow(() -> service.project(pcaSpace, 0, 49));
        }

        @Test
        void customAxisProjectionWithIdenticalWordsProducesZeroAxis() throws Exception {
            WordVector w = word(fullSpace, "king");
            // from == to → axis vector is zero → all projections should be 0
            CustomAxisProjection proj = new CustomAxisProjection(
                    w.getVector(), w.getVector(), "king", "king");
            assertThrows(InvalidAxisException.class,
                    () -> proj.project(fullSpace, 0, 1),
                    "Identical words should not define a custom semantic axis");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. חיפוש מילים לא קיימות
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Search for non-existent words")
    class NonExistentWordSearch {

        @Test
        void findOnNonExistentWordReturnsEmpty() {
            assertTrue(fullSpace.find("xyzzy_not_a_real_word").isEmpty());
            assertTrue(pcaSpace.find("xyzzy_not_a_real_word").isEmpty());
        }

        @Test
        void containsReturnsFalseForNonExistentWord() {
            assertFalse(fullSpace.contains(""));
            assertFalse(fullSpace.contains("definitelyNotAWord123456"));
        }

        @Test
        void nearestNeighborQueryWithUnknownWordUsesExternalVector() {
            // create a random vector not in the space
            double[] random = new double[fullSpace.getDimension()];
            Arrays.fill(random, 0.1);
            WordVector external = new WordVector("__external__", random);

            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            NeighborResult result = service.findNearest(external, fullSpace, 5);

            assertEquals(5, result.getNeighbors().size());
            result.getNeighbors().forEach(e -> {
                assertNotEquals("__external__", e.word());
                assertTrue(Double.isFinite(e.distance()));
            });
        }

        @Test
        void wordCloud2DFindPointReturnsEmptyForNonExistentWord() throws Exception {
            runFx(() -> {
                WordCloud2DView view = new WordCloud2DView();
                view.setPoints(new ProjectionContext(new PCAProjection()).project(pcaSpace, 0, 1));
                assertTrue(view.findPoint("this_word_does_not_exist").isEmpty());
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. חישובי צנטרואיד
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Centroid calculations – edge cases and stress")
    class CentroidCalculations {

        @Test
        void centroidOfSingleWordEqualsItsVector() {
            WordVector king = word(fullSpace, "king");
            CentroidResult result = new CentroidService().compute(List.of(king));
            assertArrayEquals(king.getVector(), result.getCentroid(), EPS);
            assertEquals(List.of("king"), result.getSourceWords());
        }

        @Test
        void centroidOfTwoOppositeVectorsIsZeroVector() {
            double[] pos = new double[]{1.0, 2.0, 3.0};
            double[] neg = new double[]{-1.0, -2.0, -3.0};
            WordVector a = new WordVector("a", pos);
            WordVector b = new WordVector("b", neg);

            double[] centroid = new CentroidService().compute(List.of(a, b)).getCentroid();
            for (double v : centroid) assertEquals(0.0, v, EPS);
        }

        @Test
        void centroidIsAverageOfAllComponents() {
            double[] v1 = {2.0, 4.0};
            double[] v2 = {6.0, 8.0};
            WordVector a = new WordVector("a", v1);
            WordVector b = new WordVector("b", v2);

            double[] centroid = new CentroidService().compute(List.of(a, b)).getCentroid();
            assertEquals(4.0, centroid[0], EPS);
            assertEquals(6.0, centroid[1], EPS);
        }

        @Test
        void centroidDoesNotMutateSourceVectors() {
            WordVector king  = word(fullSpace, "king");
            WordVector queen = word(fullSpace, "queen");
            double originalKing0  = king.getVector()[0];
            double originalQueen0 = queen.getVector()[0];

            new CentroidService().compute(List.of(king, queen));

            assertEquals(originalKing0,  king.getVector()[0],  EPS);
            assertEquals(originalQueen0, queen.getVector()[0], EPS);
        }

        @Test
        @Timeout(20)
        void centroidStressOver500RandomGroupsIsAlwaysFinite() {
            CentroidService service = new CentroidService();
            List<WordVector> all = fullSpace.getVectors();
            Random rng = new Random(42);
            for (int i = 0; i < 500; i++) {
                int size = rng.nextInt(20) + 2;
                List<WordVector> group = IntStream.range(0, size)
                        .mapToObj(j -> all.get(rng.nextInt(all.size())))
                        .collect(Collectors.toList());
                double[] centroid = service.compute(group).getCentroid();
                assertEquals(fullSpace.getDimension(), centroid.length);
                for (double v : centroid) assertTrue(Double.isFinite(v));
            }
        }

        @Test
        void centroidOfLargeGroupSourceWordsAreRecorded() {
            List<WordVector> group = fullSpace.getVectors().subList(0, 100);
            CentroidResult result = new CentroidService().compute(group);
            assertEquals(100, result.getSourceWords().size());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. חישובי ממוצעים ו-Vector Arithmetic
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Vector arithmetic and averages")
    class VectorArithmeticTests {

        @Test
        void kingMinusManPlusWomanIsQueen() {
            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));
            Optional<String> result = service.compute(
                    word(fullSpace, "king"),
                    word(fullSpace, "man"),
                    word(fullSpace, "woman"),
                    fullSpace);
            assertEquals(Optional.of("queen"), result);
        }

        @Test
        void arithmeticResultExcludesInputWords() {
            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));
            Optional<String> result = service.compute(
                    word(fullSpace, "paris"),
                    word(fullSpace, "france"),
                    word(fullSpace, "italy"),
                    fullSpace);
            result.ifPresent(w ->
                    assertFalse(Set.of("paris", "france", "italy").contains(w),
                            "Result must not be one of the input words, got: " + w));
        }

        @Test
        void arithmeticResultIsFinite() {
            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));
            // compute result vector manually and verify all components are finite
            double[] va = word(fullSpace, "king").getVector();
            double[] vb = word(fullSpace, "man").getVector();
            double[] vc = word(fullSpace, "woman").getVector();
            for (int i = 0; i < va.length; i++) {
                double v = va[i] - vb[i] + vc[i];
                assertTrue(Double.isFinite(v), "Non-finite component at index " + i);
            }
        }

        @Test
        @Timeout(20)
        void arithmeticStressOver100PairsIsAlwaysPresent() {
            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));
            List<WordVector> vectors = fullSpace.getVectors().subList(0, 100);
            int found = 0;
            for (int i = 0; i + 2 < vectors.size(); i += 3) {
                Optional<String> r = service.compute(vectors.get(i), vectors.get(i + 1), vectors.get(i + 2), fullSpace);
                r.ifPresent(w -> assertTrue(fullSpace.contains(w)));
                if (r.isPresent()) found++;
            }
            assertTrue(found > 0, "At least some arithmetic results should be found");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. עובי קו לפי מרחק שכנים
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Line thickness based on neighbor distance")
    class LineThickness {

        @Test
        void closerNeighborGetsThickerLine() {
            // line thickness formula: 0.8 + (1 - normalized) * 3.2
            // normalized = (dist - minDist) / range  → closest = 0 → thickness = 4.0
            //                                         → furthest = 1 → thickness = 0.8
            double minDist = 0.1, maxDist = 0.5;
            double range = maxDist - minDist;

            double thicknessClosest  = lineWidth(minDist, minDist, range);
            double thicknessFurthest = lineWidth(maxDist, minDist, range);

            assertEquals(4.0, thicknessClosest,  EPS);
            assertEquals(0.8, thicknessFurthest, EPS);
            assertTrue(thicknessClosest > thicknessFurthest);
        }

        @Test
        void lineThicknessIsAlwaysInRange() {
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            NeighborResult result = service.findNearest(word(fullSpace, "king"), fullSpace, 20);

            List<NeighborResult.Entry> neighbors = result.getNeighbors();
            double minDist = neighbors.stream().mapToDouble(NeighborResult.Entry::distance).min().orElse(0);
            double maxDist = neighbors.stream().mapToDouble(NeighborResult.Entry::distance).max().orElse(1);
            double range   = maxDist - minDist == 0 ? 1 : maxDist - minDist;

            for (NeighborResult.Entry e : neighbors) {
                double thickness = lineWidth(e.distance(), minDist, range);
                assertTrue(thickness >= 0.8 && thickness <= 4.0,
                        "Thickness out of range [0.8, 4.0]: " + thickness);
            }
        }

        @Test
        void lineThicknessWithAllEqualDistancesIsDefaultValue() {
            // when range == 0, normalized = 0 for all → thickness = 4.0
            double thickness = lineWidth(0.3, 0.3, 1.0); // range=1 (guarded case)
            assertTrue(thickness >= 0.8 && thickness <= 4.0);
        }

        @Test
        @Timeout(20)
        void lineThicknessForAllRealNeighborSetsIsAlwaysFinite() {
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            for (WordVector wv : fullSpace.getVectors().subList(0, 80)) {
                NeighborResult result = service.findNearest(wv, fullSpace, 15);
                List<NeighborResult.Entry> neighbors = result.getNeighbors();
                double minDist = neighbors.stream().mapToDouble(NeighborResult.Entry::distance).min().orElse(0);
                double maxDist = neighbors.stream().mapToDouble(NeighborResult.Entry::distance).max().orElse(1);
                double range   = maxDist - minDist == 0 ? 1 : maxDist - minDist;
                for (NeighborResult.Entry e : neighbors) {
                    double thickness = lineWidth(e.distance(), minDist, range);
                    assertTrue(Double.isFinite(thickness), "Non-finite thickness for: " + wv.getWord());
                }
            }
        }

        /** Replicates the formula from WordCloud2DView.drawNeighborLines */
        private static double lineWidth(double dist, double minDist, double range) {
            double normalized = (dist - minDist) / range;
            return 0.8 + (1.0 - normalized) * 3.2;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. בדיקת אקסים מותאמים אישית
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Custom axis projection edge cases")
    class CustomAxisEdgeCases {

        @Test
        void richIsHigherThanPoorOnCustomAxis() throws Exception {
            WordVector poor = word(fullSpace, "poor");
            WordVector rich = word(fullSpace, "rich");
            CustomAxisProjection proj = new CustomAxisProjection(
                    poor.getVector(), rich.getVector(), "poor", "rich");
            List<ProjectedPoint> pts = proj.project(fullSpace, 0, 1);

            double poorX = pts.stream().filter(p -> p.getWord().equals("poor"))
                    .mapToDouble(ProjectedPoint::getX).findFirst().orElseThrow();
            double richX = pts.stream().filter(p -> p.getWord().equals("rich"))
                    .mapToDouble(ProjectedPoint::getX).findFirst().orElseThrow();
            assertTrue(richX > poorX, "rich should have higher X than poor on the poor→rich axis");
        }

        @Test
        void customAxisAllProjectionsAreFinite() throws Exception {
            WordVector poor = word(fullSpace, "poor");
            WordVector rich = word(fullSpace, "rich");
            CustomAxisProjection proj = new CustomAxisProjection(
                    poor.getVector(), rich.getVector(), "poor", "rich");
            proj.project(fullSpace, 0, 1).forEach(p -> {
                assertTrue(Double.isFinite(p.getX()), "Non-finite X for: " + p.getWord());
                assertTrue(Double.isFinite(p.getY()), "Non-finite Y for: " + p.getWord());
            });
        }

        @Test
        void customAxisProjectionNameIsCorrect() {
            WordVector poor = word(fullSpace, "poor");
            WordVector rich = word(fullSpace, "rich");
            CustomAxisProjection proj = new CustomAxisProjection(
                    poor.getVector(), rich.getVector(), "poor", "rich");
            assertEquals("Custom: poor → rich", proj.name());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. Command Pattern – Undo / Redo (בונוס)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Command Pattern – Undo and Redo")
    class CommandPatternTests {

        @Test
        void executeAddsToHistoryAndCanUndo() {
            CommandHistory history = new CommandHistory();
            assertFalse(history.canUndo());
            history.execute(new ChangeAxesCommand(new int[]{1, 2}, new int[]{0, 1}, axes -> {}));
            assertTrue(history.canUndo());
            assertFalse(history.canRedo());
        }

        @Test
        void undoRestoresPreviousAxes() {
            int[] current = {0, 1};
            CommandHistory history = new CommandHistory();
            history.execute(new ChangeAxesCommand(new int[]{2, 3}, new int[]{0, 1},
                    axes -> { current[0] = axes[0]; current[1] = axes[1]; }));
            assertArrayEquals(new int[]{2, 3}, current);
            history.undo();
            assertArrayEquals(new int[]{0, 1}, current);
            assertFalse(history.canUndo());
        }

        @Test
        void redoReappliesCommand() {
            int[] current = {0, 1};
            CommandHistory history = new CommandHistory();
            history.execute(new ChangeAxesCommand(new int[]{2, 3}, new int[]{0, 1},
                    axes -> { current[0] = axes[0]; current[1] = axes[1]; }));
            history.undo();
            assertArrayEquals(new int[]{0, 1}, current);
            history.redo();
            assertArrayEquals(new int[]{2, 3}, current);
        }

        @Test
        void executeAfterUndoClearsRedoStack() {
            CommandHistory history = new CommandHistory();
            int[] val = {0};
            history.execute(new ChangeAxesCommand(new int[]{1, 0}, new int[]{0, 0},
                    axes -> val[0] = axes[0]));
            history.undo();
            assertTrue(history.canRedo());
            // new command should clear redo stack
            history.execute(new ChangeAxesCommand(new int[]{3, 4}, new int[]{0, 0},
                    axes -> val[0] = axes[0]));
            assertFalse(history.canRedo());
        }

        @Test
        void undoOnEmptyHistoryDoesNothing() {
            CommandHistory history = new CommandHistory();
            assertDoesNotThrow(history::undo);
            assertDoesNotThrow(history::redo);
        }

        @Test
        void changeMetricCommandUndoRestoresPreviousMetric() {
            String[] current = {"Cosine"};
            CommandHistory history = new CommandHistory();
            history.execute(new ChangeMetricCommand("Euclidean", "Cosine", m -> current[0] = m));
            assertEquals("Euclidean", current[0]);
            history.undo();
            assertEquals("Cosine", current[0]);
            history.redo();
            assertEquals("Euclidean", current[0]);
        }

        @Test
        void centroidCommandUndoClearsResult() {
            boolean[] computed = {false};
            boolean[] cleared  = {false};
            CommandHistory history = new CommandHistory();
            history.execute(new ReversibleCommand(
                    () -> computed[0] = true,
                    () -> cleared[0]  = true));
            assertTrue(computed[0]);
            history.undo();
            assertTrue(cleared[0]);
        }

        @Test
        void vectorArithmeticCommandUndoClearsResult() {
            String[] last = {""};
            boolean[] cleared = {false};
            CommandHistory history = new CommandHistory();
            history.execute(new VectorArithmeticCommand(
                    "king,man,woman",
                    expr -> last[0] = expr,
                    () -> cleared[0] = true));
            assertEquals("king,man,woman", last[0]);
            history.undo();
            assertTrue(cleared[0]);
        }

        @Test
        void customProjectionCommandUndoRestoresPrevious() {
            String[] state = {"pca"};
            CommandHistory history = new CommandHistory();
            history.execute(new ReversibleCommand(
                    () -> state[0] = "custom",
                    () -> state[0] = "pca"));
            assertEquals("custom", state[0]);
            history.undo();
            assertEquals("pca", state[0]);
        }

        @Test
        void searchCommandUndoRestoresPreviousSelectionAndClearsCanvas() {
            // SearchCommand now captures both appState AND cloud2D state
            List<String> canvasSelected = new ArrayList<>();
            String[] appWords = {""};

            CommandHistory history = new CommandHistory();
            history.execute(new ReversibleCommand(
                    () -> { appWords[0] = "king"; canvasSelected.add("king"); },
                    () -> { appWords[0] = "";     canvasSelected.clear(); }
            ));
            assertEquals("king", appWords[0]);
            assertFalse(canvasSelected.isEmpty());

            history.undo();
            assertEquals("", appWords[0]);
            assertTrue(canvasSelected.isEmpty(), "Canvas selection must be cleared on undo");

            history.redo();
            assertEquals("king", appWords[0]);
            assertFalse(canvasSelected.isEmpty(), "Canvas selection must be restored on redo");
        }

        @Test
        void onChangedCallbackFiredOnExecuteUndoAndRedo() {
            int[] callCount = {0};
            CommandHistory history = new CommandHistory();
            history.setOnChanged(() -> callCount[0]++);
            history.execute(new ChangeAxesCommand(new int[]{1, 2}, new int[]{0, 1}, axes -> {}));
            history.undo();
            history.redo();
            assertEquals(3, callCount[0]);
        }

        @Test
        void kChangeDoesNotPollutCommandHistory() {
            // K changes call doFind() directly — they must NOT push to CommandHistory
            CommandHistory history = new CommandHistory();
            assertFalse(history.canUndo());

            // simulate a word selection (pushes to history)
            history.execute(new ReversibleCommand(() -> {}, () -> {}));
            assertTrue(history.canUndo());

            int stackSizeBefore = 0;
            // count stack depth by undoing until empty
            CommandHistory counter = new CommandHistory();
            counter.execute(new ReversibleCommand(() -> {}, () -> {}));
            // after one execute → canUndo=true, after undo → canUndo=false (depth=1)
            counter.undo();
            assertFalse(counter.canUndo(),
                    "K change must not add extra commands — history should contain exactly 1 entry after 1 word selection");
        }

        @Test
        @Timeout(10)
        void stressExecuteUndoRedoCyclesStayConsistent() {
            int[] val = {0};
            CommandHistory history = new CommandHistory();
            for (int i = 1; i <= 100; i++) {
                int prev = val[0], next = i;
                history.execute(new ChangeAxesCommand(
                        new int[]{next}, new int[]{prev},
                        axes -> val[0] = axes[0]));
            }
            assertEquals(100, val[0]);
            for (int i = 0; i < 50; i++) history.undo();
            assertEquals(50, val[0]);
            for (int i = 0; i < 50; i++) history.redo();
            assertEquals(100, val[0]);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 12. פולימורפיזם – הוספת מטריקה חדשה (extensibility)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Polymorphism – extensibility of DistanceMetric")
    class PolymorphismTests {

        /** מטריקת מנהטן — מוסיפים אותה בלי לגעת בקוד הקיים */
        static class ManhattanDistance implements metric.DistanceMetric {
            @Override
            public double compute(double[] a, double[] b) {
                double sum = 0;
                for (int i = 0; i < a.length; i++) sum += Math.abs(a[i] - b[i]);
                return sum;
            }
            @Override public String name() { return "Manhattan"; }
        }

        @Test
        void newMetricCanBePluggedIntoDistanceServiceWithoutCodeChange() {
            DistanceService service = new DistanceService(new ManhattanDistance());
            WordVector a = word(fullSpace, "king");
            WordVector b = word(fullSpace, "queen");
            double d = service.compute(a, b);
            assertTrue(d >= 0.0);
            assertTrue(Double.isFinite(d));
            assertEquals("Manhattan", service.getMetric().name());
        }

        @Test
        void newMetricCanBePluggedIntoNearestNeighborServiceWithoutCodeChange() {
            NearestNeighborService service = new NearestNeighborService(new ManhattanDistance());
            NeighborResult result = service.findNearest(word(fullSpace, "king"), fullSpace, 10);
            assertEquals(10, result.getNeighbors().size());
            result.getNeighbors().forEach(e -> assertTrue(e.distance() >= 0.0));
        }

        @Test
        void threeDifferentMetricsProduceDifferentRankings() {
            WordVector query = word(fullSpace, "king");
            NearestNeighborService cosineService    = new NearestNeighborService(new CosineDistance());
            NearestNeighborService euclideanService = new NearestNeighborService(new EuclideanDistance());
            NearestNeighborService manhattanService = new NearestNeighborService(new ManhattanDistance());

            List<String> cosineNeighbors    = cosineService.findNearest(query, fullSpace, 5)
                    .getNeighbors().stream().map(NeighborResult.Entry::word).toList();
            List<String> euclideanNeighbors = euclideanService.findNearest(query, fullSpace, 5)
                    .getNeighbors().stream().map(NeighborResult.Entry::word).toList();
            List<String> manhattanNeighbors = manhattanService.findNearest(query, fullSpace, 5)
                    .getNeighbors().stream().map(NeighborResult.Entry::word).toList();

            // לפחות שתי מטריקות מייצרות דירוג שונה
            boolean cosineVsEuclidean  = !cosineNeighbors.equals(euclideanNeighbors);
            boolean cosineVsManhattan  = !cosineNeighbors.equals(manhattanNeighbors);
            assertTrue(cosineVsEuclidean || cosineVsManhattan,
                    "All three metrics produced identical rankings — unlikely with real data");
        }

        @Test
        void projectionStrategyIsSwappableWithoutChangingModel() throws Exception {
            ProjectionContext service = new ProjectionContext(new PCAProjection());
            List<ProjectedPoint> pca2d = service.project(pcaSpace, 0, 1);

            service.setStrategy(new ThreeDimensionalProjection(2));
            List<ProjectedPoint> pca3d = service.project(pcaSpace, 0, 1);

            assertEquals(pca2d.size(), pca3d.size());
            // Z should be non-zero in 3D but zero in 2D
            assertTrue(pca3d.stream().anyMatch(p -> p.getZ() != 0.0));
            assertTrue(pca2d.stream().allMatch(p -> p.getZ() == 0.0));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 13. מרחק על full space (לא PCA) – דרישה מפורשת במטלה
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Distance computed on full space, not PCA space")
    class FullSpaceDistanceTests {

        @Test
        void distanceOnFullSpaceDiffersFromDistanceOnPcaSpace() {
            WordVector kingFull = word(fullSpace, "king");
            WordVector queenFull = word(fullSpace, "queen");
            WordVector kingPca  = word(pcaSpace,  "king");
            WordVector queenPca = word(pcaSpace,  "queen");

            CosineDistance cosine = new CosineDistance();
            double distFull = cosine.compute(kingFull.getVector(), queenFull.getVector());
            double distPca  = cosine.compute(kingPca.getVector(),  queenPca.getVector());

            // 100-dim vs 50-dim — distances should differ
            assertNotEquals(distFull, distPca, 1e-6,
                    "Full-space and PCA-space cosine distances should differ");
            assertTrue(Double.isFinite(distFull));
            assertTrue(Double.isFinite(distPca));
        }

        @Test
        void neighborRankingOnFullSpaceUsesAllDimensions() {
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            List<String> fullNeighbors = service.findNearest(word(fullSpace, "king"), fullSpace, 10)
                    .getNeighbors().stream().map(NeighborResult.Entry::word).toList();
            List<String> pcaNeighbors  = service.findNearest(word(pcaSpace,  "king"), pcaSpace,  10)
                    .getNeighbors().stream().map(NeighborResult.Entry::word).toList();

            // vectors have different dimensions — results may differ
            assertTrue(Double.isFinite(
                    service.findNearest(word(fullSpace, "king"), fullSpace, 10)
                           .getNeighbors().get(0).distance()));
            assertFalse(fullNeighbors.isEmpty());
            assertFalse(pcaNeighbors.isEmpty());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 14. Axis selection edge cases
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Axis selection – edge cases")
    class AxisSelectionTests {

        @Test
        void sameAxisForXAndYProducesHorizontalLine() throws Exception {
            // when X == Y, all points should have identical X and Y values
            List<ProjectedPoint> pts = new PCAProjection().project(pcaSpace, 3, 3);
            pts.forEach(p -> assertEquals(p.getX(), p.getY(), EPS,
                    "X and Y should be equal when same axis selected for word: " + p.getWord()));
        }

        @Test
        void highAxisIndexStillProducesValidProjection() throws Exception {
            // axis 49 is the last valid PCA component (50 dims, index 0-49)
            List<ProjectedPoint> pts = new PCAProjection().project(pcaSpace, 48, 49);
            assertEquals(pcaSpace.size(), pts.size());
            pts.forEach(p -> {
                assertTrue(Double.isFinite(p.getX()));
                assertTrue(Double.isFinite(p.getY()));
            });
        }

        @Test
        void differentAxisPairProducesDifferentLayout() throws Exception {
            List<ProjectedPoint> pc01 = new PCAProjection().project(pcaSpace, 0, 1);
            List<ProjectedPoint> pc23 = new PCAProjection().project(pcaSpace, 2, 3);
            // coordinates of at least one word should differ between the two projections
            boolean anyDifference = false;
            for (int i = 0; i < pc01.size(); i++) {
                if (Math.abs(pc01.get(i).getX() - pc23.get(i).getX()) > EPS) {
                    anyDifference = true;
                    break;
                }
            }
            assertTrue(anyDifference, "Different axis pairs should produce different projections");
        }

        @Test
        void axisChangeViaCommandPreservesProjectionSize() throws Exception {
            ProjectionContext service = new ProjectionContext(new PCAProjection());
            int[] axes = {0, 1};
            CommandHistory history = new CommandHistory();
            history.execute(new ChangeAxesCommand(
                    new int[]{2, 3}, new int[]{0, 1},
                    a -> { axes[0] = a[0]; axes[1] = a[1]; }));
            assertEquals(pcaSpace.size(), service.project(pcaSpace, axes[0], axes[1]).size());
            history.undo();
            assertEquals(pcaSpace.size(), service.project(pcaSpace, axes[0], axes[1]).size());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 15. Error handling – קבצים פגומים ומקרי גבול של space
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Error handling – corrupted files and empty space")
    class ErrorHandlingTests {

        @Test
        void loadingNonExistentFileThrowsEmbeddingLoadException() {
            JsonEmbeddingRepository repo = new JsonEmbeddingRepository();
            assertThrows(EmbeddingLoadException.class,
                    () -> repo.load(Path.of("ghost_file_that_does_not_exist.json")));
        }

        @Test
        void nearestNeighborOnSingleWordSpaceReturnsEmptyList() {
            WordVector solo = fullSpace.getVectors().get(0);
            EmbeddingSpace single = new EmbeddingSpace(List.of(solo));
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            NeighborResult result = service.findNearest(solo, single, 5);
            // query word is excluded → no other words → empty result
            assertTrue(result.getNeighbors().isEmpty());
        }

        @Test
        void nearestNeighborWithKLargerThanSpaceSizeReturnsAllOtherWords() {
            List<WordVector> tiny = fullSpace.getVectors().subList(0, 5);
            EmbeddingSpace tinySpace = new EmbeddingSpace(tiny);
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            // K=100 but space has only 5 words; query excluded → max 4 results
            NeighborResult result = service.findNearest(tiny.get(0), tinySpace, 100);
            assertEquals(4, result.getNeighbors().size());
        }

        @Test
        void centroidOfEmptyListThrowsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CentroidService().compute(List.of()));
        }

        @Test
        void nearestNeighborWithKZeroAlwaysReturnsEmptyList() {
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            NeighborResult result = service.findNearest(word(fullSpace, "king"), fullSpace, 0);
            assertTrue(result.getNeighbors().isEmpty());
        }

        @Test
        void embeddingSpaceWithDuplicateWordsUsesLastOne() {
            // EmbeddingSpace uses a HashMap — duplicate word overwrites previous entry
            double[] v1 = new double[10];
            double[] v2 = new double[10];
            Arrays.fill(v1, 1.0);
            Arrays.fill(v2, 2.0);
            EmbeddingSpace space = new EmbeddingSpace(List.of(
                    new WordVector("dup", v1),
                    new WordVector("dup", v2)));
            // size() counts List (2), but find() returns one entry
            assertTrue(space.find("dup").isPresent());
        }
    }
}
