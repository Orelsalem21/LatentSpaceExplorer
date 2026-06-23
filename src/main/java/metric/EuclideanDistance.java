package metric;

/**
 * Computes Euclidean distance between two vectors.
 */
public class EuclideanDistance implements DistanceMetric {

    @Override
    public double compute(double[] a, double[] b) {
        if (a.length != b.length)
            throw new IllegalArgumentException(
                    "Vector dimension mismatch: " + a.length + " vs " + b.length);
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    @Override
    public String name() { return "Euclidean"; }
}