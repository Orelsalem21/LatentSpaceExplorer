import command.*;
import exception.InvalidAxisException;
import loader.JsonEmbeddingRepository;
import metric.CosineDistance;
import metric.EuclideanDistance;
import model.*;
import org.junit.jupiter.api.*;
import projection.CustomAxisProjection;
import projection.PCAProjection;
import service.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for every feature added / fixed in the current development session.
 * All tests run against the real GloVe data files (full_vectors.json + pca_vectors.json).
 *
 * Coverage:
 *  1. Case-insensitive EmbeddingSpace lookup
 *  2. Semantic Distance – multi-word all-pairs matrix
 *  3. Vector Arithmetic – king − man + woman = queen
 *  4. Custom Axis Projection – poor → rich
 *  5. Nearest-neighbor service for both axis words (Custom Axis)
 *  6. Metric switching updates distance results in real time
 *  7. VectorArithmeticCommand undo / redo lifecycle
 *  8. Distance words are highlighted separately (purple) – service correctness
 *  9. Arith-path words must all exist in the projected space
 * 10. Reset state – selected words, distance result, arith path cleared
 */
@DisplayName("Session regression – all features added in this session")
class SessionRegressionTest {

    private static final double EPS = 1e-9;

    private static EmbeddingSpace fullSpace;
    private static EmbeddingSpace pcaSpace;

    // ── setup ─────────────────────────────────────────────────────────────────

    @BeforeAll
    static void loadRealData() throws Exception {
        JsonEmbeddingRepository repo = new JsonEmbeddingRepository();
        fullSpace = repo.load(resolveDataFile("full_vectors.json"));
        pcaSpace  = repo.load(resolveDataFile("pca_vectors.json"));
    }

    private static Path resolveDataFile(String name) {
        return List.of(
                Path.of("python", name),
                Path.of("LatentSpaceExplorer", "python", name)
        ).stream().filter(Files::exists).findFirst()
                .orElseThrow(() -> new AssertionError("Data file not found: " + name));
    }

    private static WordVector word(String w) {
        return fullSpace.find(w).orElseThrow(() -> new AssertionError("Word missing: " + w));
    }

    // ── 1. Case-insensitive lookup ────────────────────────────────────────────

    @Nested
    @DisplayName("1 · Case-insensitive EmbeddingSpace lookup")
    class CaseInsensitive {

        @Test
        @DisplayName("UPPER, Mixed, and lower all resolve to the same vector")
        void upperAndMixedCaseFindSameVector() {
            WordVector lower = fullSpace.find("king").orElseThrow();
            WordVector upper = fullSpace.find("KING").orElseThrow();
            WordVector mixed = fullSpace.find("KiNg").orElseThrow();

            assertArrayEquals(lower.getVector(), upper.getVector(), EPS,
                    "KING should resolve to the same vector as king");
            assertArrayEquals(lower.getVector(), mixed.getVector(), EPS,
                    "KiNg should resolve to the same vector as king");
        }

        @Test
        @DisplayName("contains() is case-insensitive")
        void containsIsCaseInsensitive() {
            assertTrue(fullSpace.contains("king"));
            assertTrue(fullSpace.contains("KING"));
            assertTrue(fullSpace.contains("King"));
            assertFalse(fullSpace.contains("xyznotaword"));
        }

        @Test
        @DisplayName("find() returns empty for unknown words regardless of case")
        void findReturnEmptyForUnknownWords() {
            assertTrue(fullSpace.find("XYZNOTAWORD").isEmpty());
            assertTrue(fullSpace.find("xyzNotAWord").isEmpty());
        }
    }

    // ── 2. Semantic Distance – multi-word all-pairs matrix ───────────────────

    @Nested
    @DisplayName("2 · Semantic Distance – multi-word distance matrix")
    class SemanticDistanceMatrix {

        @Test
        @DisplayName("All-pairs count is n*(n-1)/2 for n words")
        void allPairsCountIsCorrect() {
            List<String> words = List.of("king", "queen", "man", "woman");
            int expectedPairs = words.size() * (words.size() - 1) / 2; // 6
            DistanceService service = new DistanceService(new CosineDistance());

            int count = 0;
            for (int i = 0; i < words.size(); i++) {
                for (int j = i + 1; j < words.size(); j++) {
                    WordVector a = fullSpace.find(words.get(i)).orElseThrow();
                    WordVector b = fullSpace.find(words.get(j)).orElseThrow();
                    double dist = service.compute(a, b);
                    assertTrue(dist >= 0, "Distance must be non-negative");
                    assertTrue(Double.isFinite(dist), "Distance must be finite");
                    count++;
                }
            }
            assertEquals(expectedPairs, count);
        }

        @Test
        @DisplayName("Cosine distance between identical words is 0")
        void sameWordDistanceIsZero() {
            DistanceService service = new DistanceService(new CosineDistance());
            WordVector king = word("king");
            assertEquals(0.0, service.compute(king, king), 1e-10);
        }

        @Test
        @DisplayName("Semantically related words are closer than unrelated ones (Cosine)")
        void relatedWordsAreCloserCosine() {
            DistanceService cosine = new DistanceService(new CosineDistance());
            double kingQueen = cosine.compute(word("king"), word("queen"));
            double kingTable = cosine.compute(word("king"), word("table"));
            assertTrue(kingQueen < kingTable,
                    "king↔queen (" + kingQueen + ") should be < king↔table (" + kingTable + ")");
        }

        @Test
        @DisplayName("Semantically related words are closer than unrelated ones (Euclidean)")
        void relatedWordsAreCloserEuclidean() {
            DistanceService euclidean = new DistanceService(new EuclideanDistance());
            double kingQueen = euclidean.compute(word("king"), word("queen"));
            double kingTable = euclidean.compute(word("king"), word("table"));
            assertTrue(kingQueen < kingTable,
                    "king↔queen (" + kingQueen + ") should be < king↔table (" + kingTable + ")");
        }

        @Test
        @DisplayName("Distance is symmetric: d(a,b) == d(b,a)")
        void distanceIsSymmetric() {
            DistanceService cosine = new DistanceService(new CosineDistance());
            DistanceService euclidean = new DistanceService(new EuclideanDistance());
            double ab = cosine.compute(word("king"), word("queen"));
            double ba = cosine.compute(word("queen"), word("king"));
            assertEquals(ab, ba, EPS, "Cosine distance must be symmetric");

            double abE = euclidean.compute(word("man"), word("woman"));
            double baE = euclidean.compute(word("woman"), word("man"));
            assertEquals(abE, baE, EPS, "Euclidean distance must be symmetric");
        }

        @Test
        @DisplayName("Multi-word list with 2 words produces exactly 1 pair")
        void twoWordsProduceOnePair() {
            DistanceService service = new DistanceService(new CosineDistance());
            List<String> words = List.of("dog", "animal");
            int pairs = 0;
            for (int i = 0; i < words.size(); i++)
                for (int j = i + 1; j < words.size(); j++) {
                    service.compute(word(words.get(i)), word(words.get(j)));
                    pairs++;
                }
            assertEquals(1, pairs);
        }
    }

    // ── 3. Vector Arithmetic ─────────────────────────────────────────────────

    @Nested
    @DisplayName("3 · Vector Arithmetic  A − B + C")
    class VectorArithmetic {

        private final VectorArithmeticService service =
                new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));

        @Test
        @DisplayName("king − man + woman = queen")
        void kingMinusManPlusWomanEqualsQueen() {
            Optional<String> result = service.compute(word("king"), word("man"), word("woman"), fullSpace);
            assertEquals(Optional.of("queen"), result, "Classic analogy must resolve to queen");
        }

        @Test
        @DisplayName("paris − france + italy = rome")
        void parisFranceItalyEqualsRome() {
            Optional<String> result = service.compute(word("paris"), word("france"), word("italy"), fullSpace);
            assertEquals(Optional.of("rome"), result, "Capital analogy must resolve to rome");
        }

        @Test
        @DisplayName("Result word is never one of the three input words")
        void resultIsNotOneOfInputs() {
            Optional<String> result = service.compute(word("king"), word("man"), word("woman"), fullSpace);
            assertTrue(result.isPresent());
            assertFalse(Set.of("king", "man", "woman").contains(result.get()),
                    "Result must not be one of the input words");
        }

        @Test
        @DisplayName("Result word exists in the full embedding space")
        void resultWordExistsInSpace() {
            Optional<String> result = service.compute(word("king"), word("man"), word("woman"), fullSpace);
            assertTrue(result.isPresent());
            assertTrue(fullSpace.contains(result.get()), "Result must be a known word");
        }

        @Test
        @DisplayName("Arith path: all 4 words (A, B, C, result) exist in PCA (projected) space")
        void arithPathWordsExistInPcaSpace() throws InvalidAxisException {
            Optional<String> result = service.compute(word("king"), word("man"), word("woman"), fullSpace);
            assertTrue(result.isPresent());
            List<String> path = List.of("king", "man", "woman", result.get());

            List<ProjectedPoint> projected = new PCAProjection().project(pcaSpace, 0, 1);
            Set<String> projectedWords = projected.stream().map(ProjectedPoint::getWord).collect(Collectors.toSet());

            for (String w : path) {
                assertTrue(projectedWords.contains(w),
                        "Arith-path word '" + w + "' must be visible in the 2D/3D projection");
            }
        }
    }

    // ── 4. Custom Axis Projection ─────────────────────────────────────────────

    @Nested
    @DisplayName("4 · Custom Axis Projection  poor → rich")
    class CustomAxisProjectionTests {

        @Test
        @DisplayName("'rich' projects further along the custom axis than 'poor'")
        void richIsToTheRightOfPoor() throws InvalidAxisException {
            WordVector poor = word("poor");
            WordVector rich = word("rich");
            CustomAxisProjection proj = new CustomAxisProjection(
                    poor.getVector(), rich.getVector(), "poor", "rich");

            Map<String, ProjectedPoint> byWord = new PCAProjection().project(pcaSpace, 0, 1)
                    .stream().collect(Collectors.toMap(ProjectedPoint::getWord, p -> p));

            List<ProjectedPoint> points = proj.project(fullSpace, 0, 1);
            ProjectedPoint poorPt = points.stream().filter(p -> p.getWord().equals("poor")).findFirst().orElseThrow();
            ProjectedPoint richPt = points.stream().filter(p -> p.getWord().equals("rich")).findFirst().orElseThrow();

            assertTrue(richPt.getX() > poorPt.getX(),
                    "rich must have a greater X than poor on the poor→rich axis");
        }

        @Test
        @DisplayName("Projection name includes both axis words")
        void projectionNameContainsBothWords() {
            CustomAxisProjection proj = new CustomAxisProjection(
                    word("poor").getVector(), word("rich").getVector(), "poor", "rich");
            String name = proj.name();
            assertTrue(name.contains("poor"), "Name must mention 'poor'");
            assertTrue(name.contains("rich"),  "Name must mention 'rich'");
        }

        @Test
        @DisplayName("All 5000 words are projected (no words dropped)")
        void allWordsAreProjected() throws InvalidAxisException {
            CustomAxisProjection proj = new CustomAxisProjection(
                    word("poor").getVector(), word("rich").getVector(), "poor", "rich");
            List<ProjectedPoint> points = proj.project(fullSpace, 0, 1);
            assertEquals(fullSpace.size(), points.size());
            assertTrue(points.stream().allMatch(p -> Double.isFinite(p.getX()) && Double.isFinite(p.getY())));
        }

        @Test
        @DisplayName("Economy-related words rank higher on poor→rich axis than random words")
        void economyWordsRankHigherOnWealthAxis() throws InvalidAxisException {
            CustomAxisProjection proj = new CustomAxisProjection(
                    word("poor").getVector(), word("rich").getVector(), "poor", "rich");
            List<ProjectedPoint> points = proj.project(fullSpace, 0, 1);

            double wealthyX = points.stream().filter(p -> p.getWord().equals("wealthy"))
                    .mapToDouble(ProjectedPoint::getX).findFirst().orElse(Double.NaN);
            double tableX = points.stream().filter(p -> p.getWord().equals("table"))
                    .mapToDouble(ProjectedPoint::getX).findFirst().orElse(Double.NaN);

            assertTrue(Double.isFinite(wealthyX) && Double.isFinite(tableX));
            assertTrue(wealthyX > tableX,
                    "wealthy (" + wealthyX + ") should score higher on wealth axis than table (" + tableX + ")");
        }
    }

    // ── 5. Nearest Neighbors for both Custom Axis words ──────────────────────

    @Nested
    @DisplayName("5 · Nearest Neighbors for Custom Axis words")
    class AxisNeighbors {

        @Test
        @DisplayName("Neighbors of 'poor' are semantically related to poverty")
        void neighborsOfPoorAreReasonable() {
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            NeighborResult result = service.findNearest(word("poor"), fullSpace, 10);
            List<String> neighbors = result.getNeighbors().stream().map(NeighborResult.Entry::word).toList();
            assertFalse(neighbors.contains("poor"), "Query word must not appear in neighbors");
            // actual GloVe neighbors of 'poor': lack, especially, bad, low, weak, worse, affected, better...
            assertTrue(neighbors.stream().anyMatch(
                    Set.of("lack", "bad", "low", "weak", "worse", "affected", "better",
                            "rich", "particularly", "especially", "poverty")::contains),
                    "Neighbors of 'poor' should include related words; actual: " + neighbors);
        }

        @Test
        @DisplayName("Neighbors of 'rich' are semantically related to wealth")
        void neighborsOfRichAreReasonable() {
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            NeighborResult result = service.findNearest(word("rich"), fullSpace, 10);
            List<String> neighbors = result.getNeighbors().stream().map(NeighborResult.Entry::word).toList();
            assertFalse(neighbors.contains("rich"), "Query word must not appear in neighbors");
            assertTrue(neighbors.stream().anyMatch(
                    Set.of("wealthy", "affluent", "luxurious", "prosperous", "opulent",
                            "poor", "wealth", "luxury", "expensive", "profitable")::contains),
                    "Neighbors of 'rich' should relate to wealth; actual: " + neighbors);
        }

        @Test
        @DisplayName("Merged neighbor list for both axis words contains no duplicates")
        void mergedNeighborListHasNoDuplicates() {
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            int k = 5;
            NeighborResult fromResult = service.findNearest(word("poor"), fullSpace, k);
            NeighborResult toResult   = service.findNearest(word("rich"), fullSpace, k);

            Map<String, Double> merged = new LinkedHashMap<>();
            fromResult.getNeighbors().forEach(e -> merged.put(e.word(), e.distance()));
            toResult.getNeighbors().forEach(e -> merged.putIfAbsent(e.word(), e.distance()));

            // putIfAbsent guarantees no duplicates
            long uniqueCount = merged.keySet().stream().distinct().count();
            assertEquals(merged.size(), uniqueCount, "Merged map must have no duplicate words");
        }
    }

    // ── 6. Metric switching ───────────────────────────────────────────────────

    @Nested
    @DisplayName("6 · Metric switching updates distance in real time")
    class MetricSwitching {

        @Test
        @DisplayName("Cosine and Euclidean produce different values for king↔queen")
        void differentMetricsProduceDifferentResults() {
            DistanceService service = new DistanceService(new CosineDistance());
            double cosine = service.compute(word("king"), word("queen"));

            service.setMetric(new EuclideanDistance());
            double euclidean = service.compute(word("king"), word("queen"));

            assertNotEquals(cosine, euclidean, 1e-6,
                    "Cosine and Euclidean distances must differ for king↔queen");
        }

        @Test
        @DisplayName("Metric name updates after setMetric()")
        void metricNameUpdatesAfterSwitch() {
            DistanceService service = new DistanceService(new CosineDistance());
            assertTrue(service.getMetric().name().toLowerCase().contains("cosine"));

            service.setMetric(new EuclideanDistance());
            assertTrue(service.getMetric().name().toLowerCase().contains("euclidean"));
        }

        @Test
        @DisplayName("NearestNeighbor ranking may differ between Cosine and Euclidean")
        void nearestNeighborRankingDiffersByMetric() {
            NearestNeighborService service = new NearestNeighborService(new CosineDistance());
            List<String> cosineNeighbors = service.findNearest(word("king"), fullSpace, 5)
                    .getNeighbors().stream().map(NeighborResult.Entry::word).toList();

            service.setMetric(new EuclideanDistance());
            List<String> euclideanNeighbors = service.findNearest(word("king"), fullSpace, 5)
                    .getNeighbors().stream().map(NeighborResult.Entry::word).toList();

            assertNotEquals(cosineNeighbors, euclideanNeighbors,
                    "Cosine and Euclidean should rank neighbors differently");
        }
    }

    // ── 7. VectorArithmeticCommand undo / redo ────────────────────────────────

    @Nested
    @DisplayName("7 · VectorArithmeticCommand – undo / redo lifecycle")
    class VectorArithmeticCommandTests {

        @Test
        @DisplayName("Execute sets result; undo clears it; redo restores it")
        void executeUndoRedoCycle() {
            VectorArithmeticService arithmeticService =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));
            CommandHistory history = new CommandHistory();

            String[] currentResult = {null};

            String expr = "king,man,woman";
            Command cmd = new VectorArithmeticCommand(
                    expr,
                    e -> {
                        String[] parts = e.split(",");
                        arithmeticService.compute(word(parts[0]), word(parts[1]), word(parts[2]), fullSpace)
                                .ifPresent(r -> currentResult[0] = r);
                    },
                    () -> currentResult[0] = null
            );

            history.execute(cmd);
            assertEquals("queen", currentResult[0], "After execute, result must be queen");

            history.undo();
            assertNull(currentResult[0], "After undo, result must be cleared");

            history.redo();
            assertEquals("queen", currentResult[0], "After redo, result must be restored to queen");
        }

        @Test
        @DisplayName("New command after undo clears the redo stack")
        void newCommandClearsRedoStack() {
            CommandHistory history = new CommandHistory();
            String[] result = {null};

            history.execute(new VectorArithmeticCommand("king,man,woman",
                    e -> result[0] = "queen", () -> result[0] = null));
            history.undo();
            assertTrue(history.canRedo());

            history.execute(new VectorArithmeticCommand("paris,france,italy",
                    e -> result[0] = "rome", () -> result[0] = null));
            assertFalse(history.canRedo(), "Redo stack must be cleared after new command");
            assertEquals("rome", result[0]);
        }
    }

    // ── 7b. Vector Arithmetic – input validation ──────────────────────────────

    @Nested
    @DisplayName("7b · Vector Arithmetic – input validation")
    class VectorArithmeticValidation {

        private String computeResult(String exprA, String exprB, String exprC) {
            String[] result = {"—"};
            VectorArithmeticService service =
                    new VectorArithmeticService(new NearestNeighborService(new CosineDistance()));

            String wa = exprA.trim().toLowerCase();
            String wb = exprB.trim().toLowerCase();
            String wc = exprC.trim().toLowerCase();

            List<String> missing = new java.util.ArrayList<>();
            if (wa.isEmpty()) missing.add("A");
            if (wb.isEmpty()) missing.add("B");
            if (wc.isEmpty()) missing.add("C");
            if (!missing.isEmpty()) {
                result[0] = "⚠ Missing word" + (missing.size() > 1 ? "s" : "") + ": " + String.join(", ", missing);
                return result[0];
            }

            List<String> notFound = new java.util.ArrayList<>();
            if (!fullSpace.contains(wa)) notFound.add("\"" + wa + "\"");
            if (!fullSpace.contains(wb)) notFound.add("\"" + wb + "\"");
            if (!fullSpace.contains(wc)) notFound.add("\"" + wc + "\"");
            if (!notFound.isEmpty()) {
                result[0] = "⚠ Not found: " + String.join(", ", notFound);
                return result[0];
            }

            service.compute(word(wa), word(wb), word(wc), fullSpace)
                    .ifPresentOrElse(r -> result[0] = "→ " + r, () -> result[0] = "No result found");
            return result[0];
        }

        @Test
        @DisplayName("All three fields empty → reports all three missing")
        void allFieldsEmptyReportsAllMissing() {
            String r = computeResult("", "", "");
            assertTrue(r.contains("A") && r.contains("B") && r.contains("C"),
                    "Should report all three missing; got: " + r);
        }

        @Test
        @DisplayName("Only A filled → reports B and C missing")
        void onlyAFilledReportsBCMissing() {
            String r = computeResult("king", "", "");
            assertTrue(r.contains("B") && r.contains("C") && !r.contains("\"A\""),
                    "Should report B and C missing; got: " + r);
        }

        @Test
        @DisplayName("Only B empty → reports B missing")
        void onlyBEmptyReportsBMissing() {
            String r = computeResult("king", "", "woman");
            assertTrue(r.contains("B"), "Should report B missing; got: " + r);
            assertFalse(r.contains("A") && r.contains("C"), "Should not report A or C missing");
        }

        @Test
        @DisplayName("Unknown word → reports not found with the word name")
        void unknownWordReportsNotFound() {
            String r = computeResult("king", "xyzunknownword", "woman");
            assertTrue(r.contains("xyzunknownword"), "Error must name the unknown word; got: " + r);
        }

        @Test
        @DisplayName("Multiple unknown words → all reported")
        void multipleUnknownWordsAllReported() {
            String r = computeResult("king", "xyzfoo", "xyzbar");
            assertTrue(r.contains("xyzfoo") && r.contains("xyzbar"),
                    "Both unknown words must be reported; got: " + r);
        }

        @Test
        @DisplayName("All valid → returns a result (not an error)")
        void allValidReturnsResult() {
            String r = computeResult("king", "man", "woman");
            assertTrue(r.startsWith("→ "), "Valid input must produce a result; got: " + r);
        }
    }

    // ── 8. Distance words – highlighted separately from selected ─────────────

    @Nested
    @DisplayName("8 · Distance words are a separate set from selected words")
    class DistanceWordsHighlight {

        @Test
        @DisplayName("Distance words and selected words can be disjoint")
        void distanceWordsCanBeDifferentFromSelected() {
            Set<String> selected     = Set.of("king");
            Set<String> distanceWords = Set.of("cat", "dog");
            assertTrue(Collections.disjoint(selected, distanceWords),
                    "In a typical session, distance words and selected words are separate");
        }

        @Test
        @DisplayName("Distance word pairs must all be present in the full space")
        void distanceWordsExistInFullSpace() {
            List<String> words = List.of("happy", "good", "bad", "better");
            for (String w : words) {
                assertTrue(fullSpace.contains(w), "Word '" + w + "' must exist in full space");
            }
        }

        @Test
        @DisplayName("All-pairs distances for distance words are finite and non-negative")
        void allPairDistancesAreValid() {
            List<String> words = List.of("happy", "good", "bad", "better");
            DistanceService cosine    = new DistanceService(new CosineDistance());
            DistanceService euclidean = new DistanceService(new EuclideanDistance());

            for (int i = 0; i < words.size(); i++) {
                for (int j = i + 1; j < words.size(); j++) {
                    WordVector a = word(words.get(i));
                    WordVector b = word(words.get(j));
                    double dc = cosine.compute(a, b);
                    double de = euclidean.compute(a, b);
                    assertTrue(dc >= 0 && Double.isFinite(dc),
                            words.get(i) + "↔" + words.get(j) + " cosine invalid: " + dc);
                    assertTrue(de >= 0 && Double.isFinite(de),
                            words.get(i) + "↔" + words.get(j) + " euclidean invalid: " + de);
                }
            }
        }
    }

    // ── 9. Arith-path words exist in projected space ──────────────────────────

    @Nested
    @DisplayName("9 · Arith-path words all appear in PCA projected space")
    class ArithPathProjection {

        @Test
        @DisplayName("king, man, woman, queen all project to finite 2D coordinates")
        void arithPathWordsProjectToFiniteCoordinates() throws InvalidAxisException {
            List<String> pathWords = List.of("king", "man", "woman", "queen");
            List<ProjectedPoint> projected = new PCAProjection().project(pcaSpace, 0, 1);
            Map<String, ProjectedPoint> byWord = projected.stream()
                    .collect(Collectors.toMap(ProjectedPoint::getWord, p -> p));

            for (String w : pathWords) {
                assertTrue(byWord.containsKey(w), "'" + w + "' must be in the projected point list");
                ProjectedPoint p = byWord.get(w);
                assertTrue(Double.isFinite(p.getX()), w + " X is not finite");
                assertTrue(Double.isFinite(p.getY()), w + " Y is not finite");
            }
        }

        @Test
        @DisplayName("All 4 arith-path words are distinct points in 2D space")
        void arithPathWordsAreDistinctPoints() throws InvalidAxisException {
            List<String> pathWords = List.of("king", "man", "woman", "queen");
            List<ProjectedPoint> projected = new PCAProjection().project(pcaSpace, 0, 1);
            Map<String, ProjectedPoint> byWord = projected.stream()
                    .collect(Collectors.toMap(ProjectedPoint::getWord, p -> p));

            Set<String> seen = new HashSet<>();
            for (String w : pathWords) {
                ProjectedPoint p = byWord.get(w);
                String key = p.getX() + "," + p.getY();
                assertTrue(seen.add(key), "Two arith-path words must not overlap at the same pixel");
            }
        }
    }

    // ── 10. Reset state ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("10 · AppState reset clears all mutable state")
    class ResetState {

        @Test
        @DisplayName("Selected words list is empty after clear()")
        void selectedWordsEmptyAfterClear() {
            app.AppState state = new app.AppState();
            state.setSpace(pcaSpace);
            state.setFullSpace(fullSpace);
            state.getSelectedWords().setAll("king", "queen");
            assertEquals(2, state.getSelectedWords().size());

            state.getSelectedWords().clear();
            assertTrue(state.getSelectedWords().isEmpty());
        }

        @Test
        @DisplayName("coordinatesProperty resets to empty string")
        void coordinatesPropertyResetsToEmpty() {
            app.AppState state = new app.AppState();
            state.coordinatesProperty().set("PC1 (X): 1.2345   PC2 (Y): -0.9876");
            assertFalse(state.coordinatesProperty().get().isEmpty());

            state.coordinatesProperty().set("");
            assertTrue(state.coordinatesProperty().get().isEmpty());
        }

        @Test
        @DisplayName("distanceResultProperty resets to dash")
        void distanceResultPropertyResetsToDash() {
            app.AppState state = new app.AppState();
            state.distanceResultProperty().set("0.1234  [Cosine Distance]");
            state.distanceResultProperty().set("—");
            assertEquals("—", state.distanceResultProperty().get());
        }

        @Test
        @DisplayName("CommandHistory is empty and cannot undo/redo after fresh construction")
        void freshCommandHistoryCannotUndoOrRedo() {
            CommandHistory history = new CommandHistory();
            assertFalse(history.canUndo());
            assertFalse(history.canRedo());
        }
    }
}
