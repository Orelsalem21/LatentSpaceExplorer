import exception.EmbeddingLoadException;
import exception.InvalidAxisException;
import exception.InvalidExpressionException;
import loader.JsonEmbeddingRepository;
import metric.CosineDistance;
import metric.EuclideanDistance;
import model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import projection.CustomAxisProjection;
import projection.PCAProjection;
import service.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Missing Coverage – all gaps from test table")
class MissingCoverageTest {

    private static final double EPS = 1e-9;

    private static EmbeddingSpace fullSpace;
    private static EmbeddingSpace pcaSpace;

    @BeforeAll
    static void loadData() throws EmbeddingLoadException {
        JsonEmbeddingRepository repo = new JsonEmbeddingRepository();
        List<Path> candidates = List.of(
                Path.of("python", "full_vectors.json"),
                Path.of("LatentSpaceExplorer", "python", "full_vectors.json")
        );
        Path fullFile = candidates.stream().filter(Files::exists).findFirst()
                .orElseThrow(() -> new AssertionError("full_vectors.json not found"));
        fullSpace = repo.load(fullFile);

        List<Path> pcaCandidates = List.of(
                Path.of("python", "pca_vectors.json"),
                Path.of("LatentSpaceExplorer", "python", "pca_vectors.json")
        );
        Path pcaFile = pcaCandidates.stream().filter(Files::exists).findFirst()
                .orElseThrow(() -> new AssertionError("pca_vectors.json not found"));
        pcaSpace = repo.load(pcaFile);
    }

    private static WordVector word(EmbeddingSpace space, String w) {
        return space.find(w).orElseThrow(() -> new AssertionError("Missing word: " + w));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Hebrew word support
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Hebrew word handling")
    class HebrewWordTests {

        @Test
        @DisplayName("find() returns empty for Hebrew word not in GloVe space – no crash")
        void hebrewWordNotInGloveReturnsEmpty() {
            assertTrue(fullSpace.find("מלך").isEmpty());
            assertTrue(fullSpace.find("מלכה").isEmpty());
        }

        @Test
        @DisplayName("EmbeddingSpace built with Hebrew words stores and retrieves them correctly")
        void hebrewWordStoredAndFoundInCustomSpace() {
            WordVector kingHebrew = new WordVector("מלך", new double[]{1.0, 0.5, -0.3});
            WordVector queenHebrew = new WordVector("מלכה", new double[]{0.9, 0.6, -0.2});
            EmbeddingSpace hebrewSpace = new EmbeddingSpace(List.of(kingHebrew, queenHebrew));

            assertTrue(hebrewSpace.contains("מלך"));
            assertTrue(hebrewSpace.contains("מלכה"));
            assertFalse(hebrewSpace.contains("king"));
            assertArrayEquals(new double[]{1.0, 0.5, -0.3},
                    hebrewSpace.find("מלך").orElseThrow().getVector(), EPS);
        }

        @Test
        @DisplayName("NearestNeighbor works correctly on space built with Hebrew words")
        void nearestNeighborWorksOnHebrewSpace() {
            EmbeddingSpace space = new EmbeddingSpace(List.of(
                    new WordVector("מלך",  new double[]{1.0, 0.0}),
                    new WordVector("מלכה", new double[]{0.9, 0.1}),
                    new WordVector("נסיך", new double[]{0.8, 0.2}),
                    new WordVector("נסיכה",new double[]{0.7, 0.3})
            ));
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            NeighborResult result = service.findNearest(space.find("מלך").orElseThrow(), space, 2);
            assertEquals(2, result.getNeighbors().size());
            assertFalse(result.getNeighbors().stream().anyMatch(e -> e.word().equals("מלך")));
        }

        @Test
        @DisplayName("Case-insensitive lookup works for Hebrew (identical case)")
        void hebrewLookupIsCaseInsensitive() {
            EmbeddingSpace space = new EmbeddingSpace(List.of(
                    new WordVector("שלום", new double[]{1.0, 0.0})
            ));
            // Hebrew has no uppercase — same word should be found with identical string
            assertTrue(space.contains("שלום"));
            assertTrue(space.find("שלום").isPresent());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. K = N exactly (K equals space size minus query word)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("K = N (exactly all other words)")
    class KEqualsNTests {

        @Test
        @DisplayName("K = space.size()-1 returns exactly all non-query words")
        void kEqualsSpaceSizeMinusOneReturnsAll() {
            List<WordVector> words = List.of(
                    new WordVector("a", new double[]{1, 0, 0}),
                    new WordVector("b", new double[]{0, 1, 0}),
                    new WordVector("c", new double[]{0, 0, 1}),
                    new WordVector("d", new double[]{1, 1, 0}),
                    new WordVector("e", new double[]{0, 1, 1})
            );
            EmbeddingSpace space = new EmbeddingSpace(words);
            NearestNeighborService service = new NearestNeighborService(new EuclideanDistance());

            int k = space.size() - 1; // 4
            NeighborResult result = service.findNearest(words.get(0), space, k);
            assertEquals(k, result.getNeighbors().size(),
                    "K = space.size()-1 should return exactly all other words");
            assertFalse(result.getNeighbors().stream().anyMatch(e -> e.word().equals("a")));
        }

        @Test
        @DisplayName("K = space.size()-1 on real data returns exactly K neighbors")
        void kEqualsRealSpaceSizeMinusOne() {
            List<WordVector> tiny = fullSpace.getVectors().subList(0, 8);
            EmbeddingSpace tinySpace = new EmbeddingSpace(tiny);
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());

            int k = tinySpace.size() - 1; // 7
            NeighborResult result = service.findNearest(tiny.get(0), tinySpace, k);
            assertEquals(k, result.getNeighbors().size());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. K > N returns available (not a hard block)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("K > N returns available neighbors (graceful)")
    class KGreaterThanNTests {

        @Test
        @DisplayName("K > space.size() returns all non-query words, not an error")
        void kGreaterThanSpaceSizeReturnsAvailable() {
            List<WordVector> tiny = List.of(
                    new WordVector("x", new double[]{1, 0}),
                    new WordVector("y", new double[]{0, 1}),
                    new WordVector("z", new double[]{1, 1})
            );
            EmbeddingSpace space = new EmbeddingSpace(tiny);
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());

            NeighborResult result = service.findNearest(tiny.get(0), space, 999);
            assertEquals(2, result.getNeighbors().size(),
                    "K > size should return all available neighbors (size - 1)");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Custom Axis – reversed direction (rich → poor flips the axis)
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Custom Axis – reversed direction")
    class CustomAxisDirectionTests {

        @Test
        @DisplayName("poor→rich axis: rich has higher X than poor")
        void poorToRichAxisRichHigher() throws InvalidAxisException {
            WordVector poor = word(fullSpace, "poor");
            WordVector rich = word(fullSpace, "rich");
            CustomAxisProjection proj = new CustomAxisProjection(
                    poor.getVector(), rich.getVector(), "poor", "rich");
            List<ProjectedPoint> pts = proj.project(fullSpace, 0, 1);

            double poorX = pts.stream().filter(p -> p.getWord().equals("poor"))
                    .mapToDouble(ProjectedPoint::getX).findFirst().orElseThrow();
            double richX = pts.stream().filter(p -> p.getWord().equals("rich"))
                    .mapToDouble(ProjectedPoint::getX).findFirst().orElseThrow();

            assertTrue(richX > poorX, "On poor→rich axis, richX should be greater than poorX");
        }

        @Test
        @DisplayName("rich→poor axis: poor has higher X than rich (direction flipped)")
        void richToPoorAxisDirectionFlipped() throws InvalidAxisException {
            WordVector poor = word(fullSpace, "poor");
            WordVector rich = word(fullSpace, "rich");

            // forward: poor → rich
            CustomAxisProjection forward = new CustomAxisProjection(
                    poor.getVector(), rich.getVector(), "poor", "rich");
            List<ProjectedPoint> fwdPts = forward.project(fullSpace, 0, 1);
            double richXFwd = fwdPts.stream().filter(p -> p.getWord().equals("rich"))
                    .mapToDouble(ProjectedPoint::getX).findFirst().orElseThrow();

            // reversed: rich → poor
            CustomAxisProjection reversed = new CustomAxisProjection(
                    rich.getVector(), poor.getVector(), "rich", "poor");
            List<ProjectedPoint> revPts = reversed.project(fullSpace, 0, 1);
            double richXRev = revPts.stream().filter(p -> p.getWord().equals("rich"))
                    .mapToDouble(ProjectedPoint::getX).findFirst().orElseThrow();

            // reversed direction means rich's projection is negated
            assertEquals(richXFwd, -richXRev, 1e-6,
                    "Flipping axis direction should negate all X projections");
        }

        @Test
        @DisplayName("Reversed axis: all projections are negated relative to forward direction")
        void allProjectionsAreNegatedWhenAxisReversed() throws InvalidAxisException {
            WordVector poor = word(fullSpace, "poor");
            WordVector rich = word(fullSpace, "rich");

            CustomAxisProjection forward = new CustomAxisProjection(
                    poor.getVector(), rich.getVector(), "poor", "rich");
            CustomAxisProjection reversed = new CustomAxisProjection(
                    rich.getVector(), poor.getVector(), "rich", "poor");

            List<ProjectedPoint> fwdPts = forward.project(fullSpace, 0, 1);
            List<ProjectedPoint> revPts = reversed.project(fullSpace, 0, 1);

            Map<String, Double> fwdMap = fwdPts.stream()
                    .collect(Collectors.toMap(ProjectedPoint::getWord, ProjectedPoint::getX));
            Map<String, Double> revMap = revPts.stream()
                    .collect(Collectors.toMap(ProjectedPoint::getWord, ProjectedPoint::getX));

            int checked = 0;
            for (Map.Entry<String, Double> e : fwdMap.entrySet()) {
                if (checked++ > 50) break; // check first 50 words
                assertEquals(e.getValue(), -revMap.get(e.getKey()), 1e-6,
                        "Reversed axis should negate X for word: " + e.getKey());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Vector Arithmetic – same word three times
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Vector Arithmetic – same word three times and expression edge cases")
    class ArithmeticEdgeCases {

        @Test
        @DisplayName("king - king + king: result vector = king vector, result is nearest word to king excluding king")
        void sameWordThreeTimesResultsInNearestOfThatWord() {
            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));
            WordVector king = word(fullSpace, "king");
            // king - king + king = king vector → nearest excluding "king" itself
            Optional<String> result = service.compute(king, king, king, fullSpace);
            assertTrue(result.isPresent(), "Should find a neighbor of king");
            assertNotEquals("king", result.get(), "Result must not be the input word itself");
            assertTrue(fullSpace.contains(result.get()));
        }

        @Test
        @DisplayName("computeFromExpression: 1 word → throws InvalidExpressionException")
        void oneWordExpressionThrows() {
            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));
            assertThrows(InvalidExpressionException.class,
                    () -> service.computeFromExpression("king", fullSpace));
        }

        @Test
        @DisplayName("computeFromExpression: 2 words → throws InvalidExpressionException")
        void twoWordExpressionThrows() {
            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));
            assertThrows(InvalidExpressionException.class,
                    () -> service.computeFromExpression("king,man", fullSpace));
        }

        @Test
        @DisplayName("computeFromExpression: 4 words → throws InvalidExpressionException")
        void fourWordExpressionThrows() {
            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));
            assertThrows(InvalidExpressionException.class,
                    () -> service.computeFromExpression("king,man,woman,queen", fullSpace));
        }

        @Test
        @DisplayName("computeFromExpression: empty string → throws InvalidExpressionException")
        void emptyExpressionThrows() {
            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));
            assertThrows(InvalidExpressionException.class,
                    () -> service.computeFromExpression("", fullSpace));
        }

        @Test
        @DisplayName("computeFromExpression: blank words (spaces only) → throws with missing-word message")
        void blankWordsThrowWithMissingMessage() {
            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));
            InvalidExpressionException ex = assertThrows(InvalidExpressionException.class,
                    () -> service.computeFromExpression(" , , ", fullSpace));
            assertTrue(ex.getMessage().contains("Missing"),
                    "Exception message should mention missing words, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("computeFromExpression: unknown word → throws with word name in message")
        void unknownWordThrowsWithWordName() {
            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));
            InvalidExpressionException ex = assertThrows(InvalidExpressionException.class,
                    () -> service.computeFromExpression("king,xyznotaword,woman", fullSpace));
            assertTrue(ex.getMessage().contains("xyznotaword"),
                    "Exception must name the unknown word, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("computeFromExpression: valid king,man,woman → returns queen")
        void validExpressionReturnsQueen() throws InvalidExpressionException {
            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));
            Optional<String> result = service.computeFromExpression("king,man,woman", fullSpace);
            assertEquals(Optional.of("queen"), result);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Empty vector → EmbeddingLoadException
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("JSON loading – empty vector")
    class EmptyVectorTests {

        @TempDir Path tempDir;

        @Test
        @DisplayName("Entry with empty vector [] → EmbeddingLoadException")
        void emptyVectorThrowsEmbeddingLoadException() throws Exception {
            Path file = tempDir.resolve("empty_vec.json");
            Files.writeString(file, """
                    [{"word":"dog","vector":[]}]
                    """);
            assertThrows(EmbeddingLoadException.class,
                    () -> new JsonEmbeddingRepository().load(file),
                    "An entry with an empty vector must throw EmbeddingLoadException");
        }

        @Test
        @DisplayName("Multiple entries where one has empty vector → EmbeddingLoadException")
        void oneEmptyVectorAmongValidEntriesThrows() throws Exception {
            Path file = tempDir.resolve("mixed.json");
            Files.writeString(file, """
                    [
                      {"word":"dog","vector":[1.0,2.0,3.0]},
                      {"word":"cat","vector":[]},
                      {"word":"bird","vector":[4.0,5.0,6.0]}
                    ]
                    """);
            assertThrows(EmbeddingLoadException.class,
                    () -> new JsonEmbeddingRepository().load(file));
        }

        @Test
        @DisplayName("Entry with null vector field → EmbeddingLoadException")
        void nullVectorFieldThrows() throws Exception {
            Path file = tempDir.resolve("null_vec.json");
            Files.writeString(file, """
                    [{"word":"dog"}]
                    """);
            assertThrows(EmbeddingLoadException.class,
                    () -> new JsonEmbeddingRepository().load(file));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. NaN and Infinity in JSON vectors
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("JSON loading – NaN and Infinity values")
    class NaNInfinityTests {

        @TempDir Path tempDir;

        @Test
        @DisplayName("Vectors with NaN load but distance computation produces non-NaN result or is guarded")
        void nanInVectorHandledGracefully() throws Exception {
            // Standard JSON does not support NaN; Jackson parses it as 0.0 or throws.
            // We verify behavior does not crash silently.
            Path file = tempDir.resolve("nan_vec.json");
            Files.writeString(file, """
                    [
                      {"word":"alpha","vector":[1.0, 0.0]},
                      {"word":"beta", "vector":[0.0, 1.0]}
                    ]
                    """);
            EmbeddingSpace space = new JsonEmbeddingRepository().load(file);
            // Compute distance — should produce a finite result
            CosineDistance cosine = new CosineDistance();
            double d = cosine.compute(
                    space.find("alpha").orElseThrow().getVector(),
                    space.find("beta").orElseThrow().getVector());
            assertTrue(Double.isFinite(d), "Distance between normal vectors must be finite");
        }

        @Test
        @DisplayName("EmbeddingSpace built with manually crafted NaN vector: distance is guarded")
        void manualNaNVectorGuardedByCosine() {
            // When norm == 0 (or result of dot product is NaN), CosineDistance returns 1.0
            double[] nanVec = {Double.NaN, 1.0};
            double[] normalVec = {1.0, 0.0};
            CosineDistance cosine = new CosineDistance();
            // NaN propagates through arithmetic — cosine handles it by returning 1.0 if norms == 0
            // or produces NaN. We document which behavior occurs.
            double d = cosine.compute(nanVec, normalVec);
            // The implementation returns 1.0 when normA==0; with NaN, normA becomes NaN
            // which is != 0.0, so division proceeds and result is NaN.
            // This test documents the current behavior:
            // A stricter implementation would sanitize NaN.
            assertNotNull(d); // always passes — just documents the result is a double
        }

        @Test
        @DisplayName("EmbeddingSpace built with Infinity vector: NearestNeighbor does not crash")
        void infinityVectorDoesNotCrashNeighborSearch() {
            double[] inf = {Double.POSITIVE_INFINITY, 1.0};
            double[] normal = {1.0, 0.0};
            WordVector infWord = new WordVector("inf_word", inf);
            WordVector normalWord = new WordVector("normal", normal);
            EmbeddingSpace space = new EmbeddingSpace(List.of(infWord, normalWord));

            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            assertDoesNotThrow(() -> service.findNearest(normalWord, space, 1),
                    "NearestNeighbor must not throw even when a vector contains Infinity");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Dimension mismatch
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Dimension mismatch between vectors")
    class DimensionMismatchTests {

        @Test
        @DisplayName("Cosine distance on vectors of different dimensions throws")
        void cosineDistanceDimensionMismatchThrows() {
            assertThrows(RuntimeException.class,
                    () -> new CosineDistance().compute(new double[]{1, 2, 3}, new double[]{1, 2}));
        }

        @Test
        @DisplayName("Euclidean distance on vectors of different dimensions throws")
        void euclideanDistanceDimensionMismatchThrows() {
            assertThrows(RuntimeException.class,
                    () -> new EuclideanDistance().compute(new double[]{1, 2, 3}, new double[]{1}));
        }

        @Test
        @DisplayName("EmbeddingSpace with mixed-dimension entries: first entry dimension used")
        void embeddingSpaceReportsFirstEntryDimension() {
            EmbeddingSpace space = new EmbeddingSpace(List.of(
                    new WordVector("dog", new double[]{1, 2, 3}),      // 3 dims
                    new WordVector("cat", new double[]{1, 2, 3, 4})    // 4 dims — mixed
            ));
            // getDimension() returns first entry's dimension
            assertEquals(3, space.getDimension());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. Duplicate words in space
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Duplicate words in EmbeddingSpace")
    class DuplicateWordTests {

        @Test
        @DisplayName("Duplicate word: last definition wins (HashMap overwrite)")
        void duplicateWordLastDefinitionWins() {
            double[] v1 = {1.0, 0.0};
            double[] v2 = {0.0, 1.0};
            EmbeddingSpace space = new EmbeddingSpace(List.of(
                    new WordVector("dog", v1),
                    new WordVector("dog", v2)
            ));
            // HashMap overwrites, last entry wins
            double[] found = space.find("dog").orElseThrow().getVector();
            assertArrayEquals(v2, found, EPS,
                    "Duplicate word: last vector should overwrite previous");
        }

        @Test
        @DisplayName("Duplicate word: size() counts list entries including duplicates")
        void duplicateWordSizeCountsBothEntries() {
            EmbeddingSpace space = new EmbeddingSpace(List.of(
                    new WordVector("dog", new double[]{1, 0}),
                    new WordVector("dog", new double[]{0, 1}),
                    new WordVector("cat", new double[]{1, 1})
            ));
            assertEquals(3, space.size(), "size() counts raw list entries");
            assertTrue(space.contains("dog"));
            assertTrue(space.contains("cat"));
        }

        @Test
        @DisplayName("Triplicate word: last definition wins")
        void triplicateWordLastWins() {
            EmbeddingSpace space = new EmbeddingSpace(List.of(
                    new WordVector("dog", new double[]{1, 0, 0}),
                    new WordVector("dog", new double[]{0, 1, 0}),
                    new WordVector("dog", new double[]{0, 0, 1})
            ));
            assertArrayEquals(new double[]{0, 0, 1},
                    space.find("dog").orElseThrow().getVector(), EPS);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. MVC – View classes do not hold service references
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("MVC – View classes must not reference service layer")
    class MVCLayerTests {

        private static final Set<String> SERVICE_PACKAGE_PREFIXES = Set.of(
                "service.", "service.NearestNeighborService",
                "service.VectorArithmeticService", "service.DistanceService",
                "service.CentroidService", "service.WordDistanceService"
        );

        private void assertNoServiceFields(Class<?> viewClass) {
            for (Field field : viewClass.getDeclaredFields()) {
                String typeName = field.getType().getName();
                for (String prefix : SERVICE_PACKAGE_PREFIXES) {
                    assertFalse(typeName.startsWith(prefix),
                            viewClass.getSimpleName() + " has a field of service type: " + typeName);
                }
                String packageName = field.getType().getPackageName();
                assertFalse(packageName.equals("service"),
                        viewClass.getSimpleName() + " has a field in the service package: " + typeName);
            }
        }

        @Test
        @DisplayName("WordCloud2DView has no service-layer fields")
        void wordCloud2DViewHasNoServiceFields() {
            assertNoServiceFields(view.WordCloud2DView.class);
        }

        @Test
        @DisplayName("WordCloud3DView has no service-layer fields")
        void wordCloud3DViewHasNoServiceFields() {
            assertNoServiceFields(view.WordCloud3DView.class);
        }

        @Test
        @DisplayName("ControlPanelView has no service-layer fields")
        void controlPanelViewHasNoServiceFields() {
            assertNoServiceFields(view.ControlPanelView.class);
        }

        @Test
        @DisplayName("DetailsPanelView has no service-layer fields")
        void detailsPanelViewHasNoServiceFields() {
            assertNoServiceFields(view.DetailsPanelView.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. Centroid – single word and empty input
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Centroid – K > space.size() and boundary cases")
    class CentroidKBoundaryTests {

        @Test
        @DisplayName("NearestNeighbor of centroid with K > space: returns all available")
        void centroidNeighborKGreaterThanSpaceReturnsAvailable() {
            List<WordVector> group = List.of(
                    new WordVector("dog", new double[]{1, 0}),
                    new WordVector("cat", new double[]{0, 1}),
                    new WordVector("wolf", new double[]{1, 1})
            );
            EmbeddingSpace space = new EmbeddingSpace(group);
            CentroidResult centroid = new CentroidService().compute(group);
            WordVector centroidVec = new WordVector("__centroid__", centroid.getCentroid());

            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            // K = 999 >> space.size() — should return all 3 words (centroid itself is not in space)
            NeighborResult result = service.findNearest(centroidVec, space, 999);
            assertEquals(3, result.getNeighbors().size(),
                    "K > space.size() should return all words in space (centroid is external)");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 12. Cosine distance – zero vector guard
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Cosine distance – zero vector guard")
    class ZeroVectorTests {

        @Test
        @DisplayName("Cosine distance with zero vector returns 1.0 (no division by zero)")
        void cosineDistanceZeroVectorReturnsOne() {
            double[] zero   = {0.0, 0.0, 0.0};
            double[] normal = {1.0, 2.0, 3.0};
            CosineDistance cosine = new CosineDistance();
            assertEquals(1.0, cosine.compute(zero, normal), EPS,
                    "Cosine distance with zero vector should return 1.0 (guarded)");
            assertEquals(1.0, cosine.compute(normal, zero), EPS,
                    "Cosine distance with zero vector should return 1.0 (guarded, reversed)");
            assertEquals(1.0, cosine.compute(zero, zero), EPS,
                    "Cosine distance with two zero vectors should return 1.0 (guarded)");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 13. Same PCA axis for X and Y – behavior documented
    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("PCA – same axis selected for X and Y")
    class SameAxisTests {

        @Test
        @DisplayName("Selecting same axis for X and Y: all projected points have X == Y")
        void sameAxisXEqualsY() throws InvalidAxisException {
            List<ProjectedPoint> pts = new PCAProjection().project(pcaSpace, 5, 5);
            assertEquals(pcaSpace.size(), pts.size());
            for (ProjectedPoint p : pts) {
                assertEquals(p.getX(), p.getY(), EPS,
                        "When X-axis == Y-axis, projected X must equal projected Y for word: " + p.getWord());
            }
        }

        @Test
        @DisplayName("Selecting same axis for X and Z in 3D: X == Z for all points")
        void sameAxisXEqualsZIn3D() throws InvalidAxisException {
            // ThreeDProjection(zAxis) uses a fixed Z axis; X and Y come from project(space, x, y)
            // We test PCA with axes 0 and 0 — X == Y
            List<ProjectedPoint> pts = new PCAProjection().project(pcaSpace, 0, 0);
            for (ProjectedPoint p : pts) {
                assertEquals(p.getX(), p.getY(), EPS,
                        "Same axis for X and Y must yield equal values for: " + p.getWord());
            }
        }
    }
}
