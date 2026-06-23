package model;

import java.util.Arrays;

/**
 * Represents a word and its embedding vector.
 */
public class WordVector {

    private final String word;
    private final double[] vector;

    public WordVector(String word, double[] vector) {
        this.word   = word;
        this.vector = Arrays.copyOf(vector, vector.length);
    }

    public String   getWord()      { return word; }
    public int      getDimension() { return vector.length; }

    /** Returns a defensive copy of the embedding vector. */
    public double[] getVector()    { return Arrays.copyOf(vector, vector.length); }

    /** Sum of element-wise products of two equal-length vectors. */
    public static double dot(double[] a, double[] b) {
        if (a.length != b.length)
            throw new IllegalArgumentException("Vector dimension mismatch: " + a.length + " vs " + b.length);
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    /** Euclidean norm (L2 length) of a vector. */
    public static double norm(double[] v) {
        double sum = 0.0;
        for (double x : v) sum += x * x;
        return Math.sqrt(sum);
    }

    /** Returns a normalised copy of {@code v}; returns the zero vector if norm is below epsilon. */
    public static double[] normalize(double[] v, double epsilon) {
        double n = norm(v);
        double[] result = v.clone();
        if (n > epsilon) {
            for (int i = 0; i < result.length; i++) result[i] /= n;
        }
        return result;
    }
}