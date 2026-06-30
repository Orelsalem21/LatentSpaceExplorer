import app.AppState;
import command.Command;
import command.CommandHistory;
import command.ReversibleCommand;
import exception.EmbeddingLoadException;
import loader.JsonEmbeddingRepository;
import metric.CosineDistance;
import metric.DistanceMetric;
import metric.EuclideanDistance;
import model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import projection.CustomAxisProjection;
import projection.PCAProjection;
import projection.ProjectionStrategy;
import projection.ThreeDimensionalProjection;
import projection.ProjectionContext;
import service.*;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LatentSpaceExplorer - requirements and edge-case tests")
class RequirementsTest {
    private static final double EPS = 1e-9;

    private static WordVector w(String word, double... vector) {
        return new WordVector(word, vector);
    }

    private static EmbeddingSpace sampleSpace() {
        return new EmbeddingSpace(List.of(
                w("origin", 0, 0, 0),
                w("east",   1, 0, 0),
                w("north",  0, 2, 0),
                w("up",     0, 0, 3),
                w("diag",   1, 1, 1)
        ));
    }

    private static EmbeddingSpace analogySpace() {
        return new EmbeddingSpace(List.of(
                w("king",   2, 1),
                w("man",    0, 1),
                w("woman",  0, 2),
                w("queen",  2, 2),
                w("noise", -5, 7)
        ));
    }

    @Nested
    @DisplayName("Model integrity")
    class ModelTests {
        @Test
        void embeddingSpaceStoresSearchesAndProtectsList() {
            EmbeddingSpace space = sampleSpace();
            assertEquals(5, space.size());
            assertEquals(3, space.getDimension());
            assertTrue(space.contains("east"));
            assertTrue(space.find("north").isPresent());
            assertFalse(space.contains("missing"));
            assertTrue(space.find("missing").isEmpty());
            assertThrows(UnsupportedOperationException.class,
                    () -> space.getVectors().add(w("new", 1, 2, 3)));
        }

        @Test
        void emptyEmbeddingSpaceIsSafe() {
            EmbeddingSpace space = new EmbeddingSpace(List.of());
            assertEquals(0, space.size());
            assertEquals(0, space.getDimension());
            assertTrue(space.find("anything").isEmpty());
        }

        @Test
        void wordVectorAndCentroidUseDefensiveCopies() {
            double[] raw = {1, 2, 3};
            WordVector vector = new WordVector("safe", raw);
            raw[0] = 999;
            assertArrayEquals(new double[]{1, 2, 3}, vector.getVector(), EPS);

            double[] copy = vector.getVector();
            copy[1] = 999;
            assertArrayEquals(new double[]{1, 2, 3}, vector.getVector(), EPS);

            CentroidResult centroid = new CentroidResult(new double[]{4, 5}, List.of("a"));
            double[] centroidCopy = centroid.getCentroid();
            centroidCopy[0] = 999;
            assertArrayEquals(new double[]{4, 5}, centroid.getCentroid(), EPS);
        }
    }

    @Nested
    @DisplayName("Distance metrics and Strategy pattern")
    class DistanceTests {
        @Test
        void euclideanAndCosineDistancesAreCorrect() {
            assertEquals(5.0, new EuclideanDistance().compute(new double[]{1, 2}, new double[]{4, 6}), EPS);
            assertEquals(0.0, new EuclideanDistance().compute(new double[]{1, 2}, new double[]{1, 2}), EPS);

            CosineDistance cosine = new CosineDistance();
            assertEquals(0.0, cosine.compute(new double[]{1, 0}, new double[]{2, 0}), EPS);
            assertEquals(1.0, cosine.compute(new double[]{1, 0}, new double[]{0, 5}), EPS);
            assertEquals(2.0, cosine.compute(new double[]{1, 0}, new double[]{-1, 0}), EPS);
            assertEquals(1.0, cosine.compute(new double[]{0, 0}, new double[]{1, 2}), EPS);
        }

        @Test
        void distanceServiceCanSwapMetricAtRuntime() {
            DistanceService service = new DistanceService(new EuclideanDistance());
            WordVector a = w("a", 1, 0);
            WordVector b = w("b", 0, 1);
            assertEquals(Math.sqrt(2), service.compute(a, b), EPS);

            service.setMetric(new CosineDistance());
            assertEquals(1.0, service.compute(a, b), EPS);
            assertTrue(service.getMetric().name().toLowerCase().contains("cosine"));
        }

        @Test
        void distanceMetricInterfaceSupportsNewMetric() {
            assertTrue(DistanceMetric.class.isInterface());
            DistanceMetric manhattan = new DistanceMetric() {
                @Override public double compute(double[] a, double[] b) {
                    double sum = 0;
                    for (int i = 0; i < a.length; i++) sum += Math.abs(a[i] - b[i]);
                    return sum;
                }
                @Override public String name() { return "Manhattan"; }
            };
            assertEquals(7.0, new DistanceService(manhattan).compute(w("a", 1, 2), w("b", 4, 6)), EPS);
        }

        @Test
        void mismatchedDimensionsFailFastInCurrentImplementation() {
            assertThrows(RuntimeException.class,
                    () -> new EuclideanDistance().compute(new double[]{1, 2, 3}, new double[]{1}));
            assertThrows(RuntimeException.class,
                    () -> new CosineDistance().compute(new double[]{1, 2, 3}, new double[]{1}));
        }
    }

    @Nested
    @DisplayName("Projection requirements")
    class ProjectionTests {
        @Test
        void pcaAnd3DUseRequestedAxes() throws Exception {
            ProjectedPoint diag2D = new PCAProjection().project(sampleSpace(), 0, 2).stream()
                    .filter(p -> p.getWord().equals("diag"))
                    .findFirst().orElseThrow();
            assertEquals(1.0, diag2D.getX(), EPS);
            assertEquals(1.0, diag2D.getY(), EPS);
            assertEquals(0.0, diag2D.getZ(), EPS);

            ProjectedPoint up3D = new ThreeDimensionalProjection(2).project(sampleSpace(), 0, 1).stream()
                    .filter(p -> p.getWord().equals("up"))
                    .findFirst().orElseThrow();
            assertEquals(0.0, up3D.getX(), EPS);
            assertEquals(0.0, up3D.getY(), EPS);
            assertEquals(3.0, up3D.getZ(), EPS);
        }

        @Test
        void customAxisProjectionWorksAndHandlesIdenticalAxisWords() throws Exception {
            EmbeddingSpace space = new EmbeddingSpace(List.of(
                    w("poor", 0, 0),
                    w("middle", 1, 5),
                    w("rich", 2, 10)
            ));
            CustomAxisProjection projection = new CustomAxisProjection(
                    new double[]{0, 0}, new double[]{2, 0}, "poor", "rich");
            Map<String, ProjectedPoint> byWord = new HashMap<>();
            projection.project(space, 0, 1).forEach(p -> byWord.put(p.getWord(), p));
            assertEquals(0.0, byWord.get("poor").getX(), EPS);
            assertEquals(1.0, byWord.get("middle").getX(), EPS);
            assertEquals(2.0, byWord.get("rich").getX(), EPS);
            assertTrue(projection.name().contains("poor"));
            assertTrue(projection.name().contains("rich"));

            // identical axis words → zero vector → must throw InvalidAxisException
            assertThrows(exception.InvalidAxisException.class, () ->
                    new CustomAxisProjection(new double[]{1, 1}, new double[]{1, 1}, "same", "same")
                            .project(space, 0, 1));
        }

        @Test
        void projectionServiceUsesStrategyPolymorphically() throws Exception {
            assertTrue(ProjectionStrategy.class.isInterface());
            ProjectionContext service = new ProjectionContext(new PCAProjection());
            assertEquals(sampleSpace().size(), service.project(sampleSpace(), 0, 1).size());
            service.useThreeDimensionalProjection(2);
            assertEquals(3.0, service.project(sampleSpace(), 0, 1).stream()
                    .filter(p -> p.getWord().equals("up")).findFirst().orElseThrow().getZ(), EPS);
        }

        @Test
        void invalidProjectionAxesAreRejected() {
            assertThrows(Exception.class, () -> new PCAProjection().project(sampleSpace(), -1, 1));
            assertThrows(Exception.class, () -> new PCAProjection().project(sampleSpace(), 0, 99));
            assertThrows(Exception.class, () -> new ThreeDimensionalProjection(99).project(sampleSpace(), 0, 1));
        }
    }

    @Nested
    @DisplayName("Nearest neighbors, vector arithmetic and centroid")
    class SemanticServiceTests {
        @Test
        void nearestNeighborBoundariesWork() {
            NearestNeighborService service = new NearestNeighborService(new DistanceService(new EuclideanDistance()));
            NeighborResult two = service.findNearest(w("origin", 0, 0, 0), sampleSpace(), 2);
            assertEquals("origin", two.getQueryWord());
            assertEquals(2, two.getNeighbors().size());
            assertEquals("east", two.getNeighbors().getFirst().word());
            assertFalse(two.getNeighbors().stream().anyMatch(e -> e.word().equals("origin")));

            assertTrue(service.findNearest(w("origin", 0, 0, 0), sampleSpace(), 0).getNeighbors().isEmpty());
            assertEquals(sampleSpace().size() - 1,
                    service.findNearest(w("origin", 0, 0, 0), sampleSpace(), 100).getNeighbors().size());
            assertThrows(IllegalArgumentException.class,
                    () -> service.findNearest(w("origin", 0, 0, 0), sampleSpace(), -1));
        }

        @Test
        void vectorArithmeticImplementsV1MinusV2PlusV3() {
            VectorArithmeticService service = new VectorArithmeticService(
                    new NearestNeighborService(new DistanceService(new EuclideanDistance())));
            Optional<String> result = service.compute(
                    analogySpace().find("king").orElseThrow(),
                    analogySpace().find("man").orElseThrow(),
                    analogySpace().find("woman").orElseThrow(),
                    analogySpace());
            assertEquals(Optional.of("queen"), result);
        }

        @Test
        void centroidServiceComputesAverageAndRejectsEmptyInput() {
            CentroidResult result = new CentroidService().compute(List.of(
                    w("a", 0, 2, 4),
                    w("b", 2, 4, 6),
                    w("c", 4, 6, 8)
            ));
            assertArrayEquals(new double[]{2, 4, 6}, result.getCentroid(), EPS);
            assertEquals(List.of("a", "b", "c"), result.getSourceWords());
            assertThrows(IllegalArgumentException.class, () -> new CentroidService().compute(List.of()));
        }
    }

    @Nested
    @DisplayName("Repository and error handling")
    class RepositoryTests {
        @TempDir Path tempDir;

        @Test
        void jsonRepositoryLoadsValidData() throws Exception {
            Path file = tempDir.resolve("vectors.json");
            Files.writeString(file, """
                    [
                      {"word":"alpha","vector":[1.0,2.0,3.0]},
                      {"word":"beta","vector":[4.5,5.5,6.5]}
                    ]
                    """);
            EmbeddingSpace space = new JsonEmbeddingRepository().load(file);
            assertEquals(2, space.size());
            assertTrue(space.contains("alpha"));
            assertArrayEquals(new double[]{4.5, 5.5, 6.5}, space.find("beta").orElseThrow().getVector(), EPS);
        }

        @Test
        void jsonRepositoryWrapsBadInputInEmbeddingLoadException() throws Exception {
            Path malformed = tempDir.resolve("bad.json");
            Files.writeString(malformed, "not-json");
            assertThrows(EmbeddingLoadException.class, () -> new JsonEmbeddingRepository().load(malformed));

            Path empty = tempDir.resolve("empty.json");
            Files.writeString(empty, "[]");
            assertThrows(EmbeddingLoadException.class, () -> new JsonEmbeddingRepository().load(empty));

            Path missingWord = tempDir.resolve("missing-word.json");
            Files.writeString(missingWord, "[{\"vector\":[1,2,3]}]");
            assertThrows(EmbeddingLoadException.class, () -> new JsonEmbeddingRepository().load(missingWord));

            Path missingVector = tempDir.resolve("missing-vector.json");
            Files.writeString(missingVector, "[{\"word\":\"alpha\"}]");
            assertThrows(EmbeddingLoadException.class, () -> new JsonEmbeddingRepository().load(missingVector));
        }
    }

    @Nested
    @DisplayName("AppState and Command pattern")
    class StateAndCommandTests {
        @Test
        void appStateSeparatesVisualAndFullSemanticSpaces() {
            AppState state = new AppState();
            EmbeddingSpace visual = new EmbeddingSpace(List.of(w("king", 1, 2)));
            EmbeddingSpace full = new EmbeddingSpace(List.of(w("king", 1, 2, 3, 4)));
            state.setSpace(visual);
            state.setFullSpace(full);
            assertTrue(state.isLoaded());
            assertSame(visual, state.getSpace());
            assertSame(full, state.getFullSpace());
            state.getSelectedWords().setAll("king", "queen");
            assertEquals(List.of("king", "queen"), state.getSelectedWords());
        }

        @Test
        void commandHistorySupportsUndoRedoAndClearsRedoAfterNewCommand() {
            CommandHistory history = new CommandHistory();
            AtomicInteger value = new AtomicInteger(0);
            Command plusFive = new Command() {
                @Override public void execute() { value.addAndGet(5); }
                @Override public void undo() { value.addAndGet(-5); }
            };
            Command plusTen = new Command() {
                @Override public void execute() { value.addAndGet(10); }
                @Override public void undo() { value.addAndGet(-10); }
            };

            history.execute(plusFive);
            assertEquals(5, value.get());
            history.undo();
            assertEquals(0, value.get());
            assertTrue(history.canRedo());
            history.execute(plusTen);
            assertFalse(history.canRedo());
            assertEquals(10, value.get());
        }

        @Test
        void axisChangeCommandAppliesNewAndPreviousAxes() {
            AtomicInteger x = new AtomicInteger(0);
            AtomicInteger y = new AtomicInteger(1);
            Command command = new ReversibleCommand(
                    () -> { x.set(2); y.set(7); },
                    () -> { x.set(0); y.set(1); }
            );
            command.execute();
            assertEquals(2, x.get());
            assertEquals(7, y.get());
            command.undo();
            assertEquals(0, x.get());
            assertEquals(1, y.get());
        }
    }

    @Nested
    @DisplayName("Encapsulation")
    class EncapsulationTests {
        @Test
        void coreModelFieldsArePrivate() {
            for (Class<?> clazz : List.of(WordVector.class, EmbeddingSpace.class, ProjectedPoint.class,
                    NeighborResult.class, CentroidResult.class)) {
                Arrays.stream(clazz.getDeclaredFields()).forEach(field ->
                        assertTrue(Modifier.isPrivate(field.getModifiers()),
                                clazz.getSimpleName() + "." + field.getName() + " should be private"));
            }
        }
    }
}
