package metric;

import model.WordVector;

/**
 * Computes cosine distance between two vectors.
 */
public class CosineDistance implements DistanceMetric {

    /** Returns 1 − cosine_similarity; clamps to 1.0 when either vector is zero. */
    @Override
    public double compute(double[] a, double[] b) {
        double normA = WordVector.norm(a);
        double normB = WordVector.norm(b);
        if (normA == 0.0 || normB == 0.0) return 1.0;
        return 1.0 - (WordVector.dot(a, b) / (normA * normB));
    }

    @Override
    public String name() { return "Cosine Distance"; }
}