package model;

import java.util.List;

/**
 * Holds a computed centroid vector and its source words.
 */
public class CentroidResult {

    private final double[] centroid;
    private final List<String> sourceWords;

    public CentroidResult(double[] centroid, List<String> sourceWords) {
        this.centroid    = centroid.clone();
        this.sourceWords = List.copyOf(sourceWords);
    }

    /** Returns a defensive copy of the centroid vector. */
    public double[]     getCentroid()   { return centroid.clone(); }
    public List<String> getSourceWords() { return sourceWords; }
}