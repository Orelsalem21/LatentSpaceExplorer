package service;

import exception.InvalidExpressionException;
import model.EmbeddingSpace;
import model.NeighborResult;
import model.WordVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Evaluates word-vector arithmetic expressions of the form {@code A - B + C} and returns the nearest result words. */
public class VectorArithmeticService {

    private final NearestNeighborService neighborService;

    public VectorArithmeticService(NearestNeighborService neighborService) {
        this.neighborService = neighborService;
    }

    public Optional<String> computeFromExpression(String expr, EmbeddingSpace space)
            throws InvalidExpressionException {
        String[] parts = expr.split(",");
        if (parts.length != 3)
            throw new InvalidExpressionException("Expression must have exactly 3 words: A, B, C");

        String wa = parts[0].trim().toLowerCase();
        String wb = parts[1].trim().toLowerCase();
        String wc = parts[2].trim().toLowerCase();

        List<String> missing = new ArrayList<>();
        if (wa.isEmpty()) missing.add("A");
        if (wb.isEmpty()) missing.add("B");
        if (wc.isEmpty()) missing.add("C");
        if (!missing.isEmpty())
            throw new InvalidExpressionException(
                    "⚠ Missing word" + (missing.size() > 1 ? "s" : "") + ": " + String.join(", ", missing));

        List<String> notFound = new ArrayList<>();
        var a = space.find(wa); if (a.isEmpty()) notFound.add("\"" + wa + "\"");
        var b = space.find(wb); if (b.isEmpty()) notFound.add("\"" + wb + "\"");
        var c = space.find(wc); if (c.isEmpty()) notFound.add("\"" + wc + "\"");
        if (!notFound.isEmpty())
            throw new InvalidExpressionException("⚠ Not found: " + String.join(", ", notFound));

        return compute(a.get(), b.get(), c.get(), space);
    }

    public Optional<String> compute(WordVector a, WordVector b, WordVector c, EmbeddingSpace space) {
        if (a.getDimension() != b.getDimension() || a.getDimension() != c.getDimension())
            throw new IllegalArgumentException(
                    "Dimension mismatch: A=" + a.getDimension()
                    + " B=" + b.getDimension() + " C=" + c.getDimension());
        double[] result = new double[a.getDimension()];
        double[] va = a.getVector(), vb = b.getVector(), vc = c.getVector();

        for (int i = 0; i < result.length; i++) {
            result[i] = va[i] - vb[i] + vc[i];
        }

        WordVector resultVector = new WordVector("__result__", result);
        NeighborResult nearest  = neighborService.findNearest(resultVector, space, 10);

        return nearest.getNeighbors().stream()
                .map(NeighborResult.Entry::word)
                .filter(word -> !word.equals(a.getWord()))
                .filter(word -> !word.equals(b.getWord()))
                .filter(word -> !word.equals(c.getWord()))
                .findFirst();
    }
}
