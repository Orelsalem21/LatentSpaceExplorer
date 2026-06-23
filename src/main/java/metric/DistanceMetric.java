package metric;

/**
 * Defines a distance metric for comparing vectors.
 */
public interface DistanceMetric {

    /**
     * Computes the distance between two vectors.
     */
    double compute(double[] a, double[] b);

    /**
     * Returns the metric display name.
     */
    String name();
}