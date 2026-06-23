import app.AppState;
import command.ChangeAxesCommand;
import command.CommandHistory;
import exception.EmbeddingLoadException;
import loader.JsonEmbeddingRepository;
import metric.CosineDistance;
import metric.EuclideanDistance;
import model.*;
import org.junit.jupiter.api.*;
import projection.CustomAxisProjection;
import projection.PCAProjection;
import projection.ThreeDimensionalProjection;
import projection.ProjectionContext;
import service.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LatentSpaceExplorer - real-data integration and regression tests")
class IntegrationTest {
    private static final double EPS = 1e-9;
    private static EmbeddingSpace fullSpace;
    private static EmbeddingSpace pcaSpace;

    @BeforeAll
    static void loadRealFiles() throws EmbeddingLoadException {
        JsonEmbeddingRepository repository = new JsonEmbeddingRepository();
        fullSpace = repository.load(resolveDataFile("full_vectors.json"));
        pcaSpace = repository.load(resolveDataFile("pca_vectors.json"));
    }

    private static Path resolveDataFile(String fileName) {
        List<Path> candidates = List.of(
                Path.of("python", fileName),
                Path.of("LatentSpaceExplorer", "python", fileName),
                Path.of("src", "main", "resources", "loader", fileName),
                Path.of("target", "classes", "loader", fileName),
                Path.of("src", "main", "resources", "repository", fileName),
                Path.of("target", "classes", "repository", fileName),
                Path.of("LatentSpaceExplorer", "src", "main", "resources", "loader", fileName),
                Path.of("LatentSpaceExplorer", "src", "main", "resources", "repository", fileName)
        );
        return candidates.stream().filter(Files::exists).findFirst()
                .orElseThrow(() -> new AssertionError("Real data file was not found: " + fileName));
    }

    private static WordVector word(EmbeddingSpace space, String value) {
        return space.find(value).orElseThrow(() -> new AssertionError("Missing expected word: " + value));
    }

    private static double[] formula(String a, String b, String c) {
        double[] va = word(fullSpace, a).getVector();
        double[] vb = word(fullSpace, b).getVector();
        double[] vc = word(fullSpace, c).getVector();
        double[] result = new double[va.length];
        for (int i = 0; i < result.length; i++) result[i] = va[i] - vb[i] + vc[i];
        return result;
    }

    private static List<String> nearestWords(double[] target, int k, String... excludedWords) {
        Set<String> excluded = Set.of(excludedWords);
        EmbeddingSpace filtered = new EmbeddingSpace(fullSpace.getVectors().stream()
                .filter(wv -> !excluded.contains(wv.getWord()))
                .collect(Collectors.toList()));
        return new NearestNeighborService(new DistanceService(new CosineDistance()))
                .findNearest(new WordVector("__target__", target), filtered, k)
                .getNeighbors().stream()
                .map(NeighborResult.Entry::word)
                .toList();
    }

    @Nested
    @DisplayName("Real JSON data integrity")
    class RealDataIntegrity {
        @Test
        void fullAndPcaFilesLoadExpectedShape() {
            assertEquals(5000, fullSpace.size());
            assertEquals(5000, pcaSpace.size());
            assertEquals(100, fullSpace.getDimension());
            assertEquals(50, pcaSpace.getDimension());
        }

        @Test
        void fullAndPcaVocabularyIsAlignedAndUnique() {
            Set<String> fullWords = fullSpace.getVectors().stream().map(WordVector::getWord).collect(Collectors.toSet());
            Set<String> pcaWords = pcaSpace.getVectors().stream().map(WordVector::getWord).collect(Collectors.toSet());
            assertEquals(fullSpace.size(), fullWords.size(), "full_vectors contains duplicate words");
            assertEquals(pcaSpace.size(), pcaWords.size(), "pca_vectors contains duplicate words");
            assertEquals(fullWords, pcaWords, "full and PCA files must share the same vocabulary");
        }

        @Test
        void vectorsAreFiniteAndNonZero() {
            for (EmbeddingSpace space : List.of(fullSpace, pcaSpace)) {
                for (WordVector vector : space.getVectors()) {
                    assertEquals(space.getDimension(), vector.getDimension(), vector.getWord());
                    double norm = 0.0;
                    for (double value : vector.getVector()) {
                        assertTrue(Double.isFinite(value), vector.getWord() + " has non-finite value");
                        norm += value * value;
                    }
                    assertTrue(norm > 0.0, vector.getWord() + " is a zero vector");
                }
            }
        }
    }

    @Nested
    @DisplayName("Semantic computations on real data")
    class SemanticComputations {
        @Test
        void cosineDistanceRanksRelatedWordsCloserThanUnrelatedWords() {
            CosineDistance cosine = new CosineDistance();
            double same = cosine.compute(word(fullSpace, "king").getVector(), word(fullSpace, "king").getVector());
            double kingQueen = cosine.compute(word(fullSpace, "king").getVector(), word(fullSpace, "queen").getVector());
            double kingCar = cosine.compute(word(fullSpace, "king").getVector(), word(fullSpace, "car").getVector());
            assertEquals(0.0, same, 1e-12);
            assertTrue(kingQueen < kingCar, "king should be closer to queen than to car");
        }

        @Test
        void nearestNeighborsOfDogAreSemanticallyReasonable() {
            NeighborResult result = new NearestNeighborService(new DistanceService(new CosineDistance()))
                    .findNearest(word(fullSpace, "dog"), fullSpace, 10);
            List<String> neighbors = result.getNeighbors().stream().map(NeighborResult.Entry::word).toList();
            assertEquals(10, neighbors.size());
            assertFalse(neighbors.contains("dog"));
            assertTrue(neighbors.stream().anyMatch(Set.of("dogs", "animal", "animals", "horse", "bird")::contains),
                    "Actual neighbors: " + neighbors);
        }

        @Test
        void vectorArithmeticFindsClassicAnalogies() {
            assertEquals("queen", nearestWords(formula("king", "man", "woman"), 5, "king", "man", "woman").getFirst());
            assertEquals("rome", nearestWords(formula("paris", "france", "italy"), 5, "paris", "france", "italy").getFirst());

            VectorArithmeticService service = new VectorArithmeticService(new NearestNeighborService(new DistanceService(new CosineDistance())));
            assertEquals(Optional.of("queen"), service.compute(
                    word(fullSpace, "king"), word(fullSpace, "man"), word(fullSpace, "woman"), fullSpace));
        }

        @Test
        void centroidOfRoyaltyWordsFindsRelatedConcepts() {
            List<WordVector> royalty = List.of(
                    word(fullSpace, "king"), word(fullSpace, "queen"), word(fullSpace, "prince"), word(fullSpace, "princess"));
            CentroidResult centroid = new CentroidService().compute(royalty);
            assertEquals(List.of("king", "queen", "prince", "princess"), centroid.getSourceWords());
            List<String> nearest = nearestWords(centroid.getCentroid(), 12, "king", "queen", "prince", "princess");
            assertTrue(nearest.stream().anyMatch(Set.of("royal", "crown", "duke", "emperor", "kingdom", "elizabeth")::contains),
                    "Actual centroid neighbors: " + nearest);
        }
    }

    @Nested
    @DisplayName("Projection integration")
    class ProjectionIntegration {
        @Test
        void pcaProjectionUsesPcaSpaceAndSelectedAxes() throws Exception {
            ProjectedPoint king = new PCAProjection().project(pcaSpace, 2, 7).stream()
                    .filter(p -> p.getWord().equals("king")).findFirst().orElseThrow();
            double[] vector = word(pcaSpace, "king").getVector();
            assertEquals(vector[2], king.getX(), EPS);
            assertEquals(vector[7], king.getY(), EPS);
            assertEquals(0.0, king.getZ(), EPS);
        }

        @Test
        void threeDProjectionAddsSelectedZAxis() throws Exception {
            ProjectedPoint queen = new ThreeDimensionalProjection(2).project(pcaSpace, 0, 1).stream()
                    .filter(p -> p.getWord().equals("queen")).findFirst().orElseThrow();
            double[] vector = word(pcaSpace, "queen").getVector();
            assertEquals(vector[0], queen.getX(), EPS);
            assertEquals(vector[1], queen.getY(), EPS);
            assertEquals(vector[2], queen.getZ(), EPS);
        }

        @Test
        void customAxisProjectionUsesFullSemanticVectors() throws Exception {
            WordVector poor = word(fullSpace, "poor");
            WordVector rich = word(fullSpace, "rich");
            List<ProjectedPoint> points = new CustomAxisProjection(
                    poor.getVector(), rich.getVector(), "poor", "rich")
                    .project(fullSpace, 0, 1);
            ProjectedPoint poorPoint = points.stream().filter(p -> p.getWord().equals("poor")).findFirst().orElseThrow();
            ProjectedPoint richPoint = points.stream().filter(p -> p.getWord().equals("rich")).findFirst().orElseThrow();
            assertTrue(richPoint.getX() > poorPoint.getX());
        }
    }

    @Nested
    @DisplayName("Regression for state separation and commands")
    class RegressionTests {
        @Test
        void appStateKeepsFullSpaceSeparateFromPcaVisualSpace() {
            AppState state = new AppState();
            state.setFullSpace(fullSpace);
            state.setSpace(pcaSpace);
            assertSame(fullSpace, state.getFullSpace());
            assertSame(pcaSpace, state.getSpace());
            assertEquals(100, state.getFullSpace().getDimension());
            assertEquals(50, state.getSpace().getDimension());
        }

        @Test
        void metricChangeAffectsNearestNeighborService() {
            DistanceService distSvc = new DistanceService(new CosineDistance());
            NearestNeighborService service = new NearestNeighborService(distSvc);
            NeighborResult cosine = service.findNearest(word(fullSpace, "king"), fullSpace, 5);
            distSvc.setMetric(new EuclideanDistance());
            NeighborResult euclidean = service.findNearest(word(fullSpace, "king"), fullSpace, 5);
            assertEquals(5, cosine.getNeighbors().size());
            assertEquals(5, euclidean.getNeighbors().size());
            assertTrue(euclidean.getNeighbors().stream().allMatch(e -> Double.isFinite(e.distance())));
        }

        @Test
        void changeAxesCommandCanBeUsedWithProjectionContext() {
            ProjectionContext projectionService = new ProjectionContext(new PCAProjection());
            CommandHistory history = new CommandHistory();
            int[] axes = {0, 1};
            history.execute(new ChangeAxesCommand(new int[]{2, 7}, new int[]{0, 1}, next -> {
                axes[0] = next[0];
                axes[1] = next[1];
                try {
                    assertEquals(pcaSpace.size(), projectionService.project(pcaSpace, axes[0], axes[1]).size());
                } catch (exception.InvalidAxisException e) { throw new RuntimeException(e); }
            }));
            assertArrayEquals(new int[]{2, 7}, axes);
            history.undo();
            assertArrayEquals(new int[]{0, 1}, axes);
            history.redo();
            assertArrayEquals(new int[]{2, 7}, axes);
        }
    }
}
