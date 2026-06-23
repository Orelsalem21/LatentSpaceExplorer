package service;

import model.EmbeddingSpace;
import model.NeighborResult;
import model.WordVector;

import java.util.Comparator;
import java.util.List;

/** Finds the K nearest neighbors of a word or virtual vector inside an {@link model.EmbeddingSpace}. */
public class NearestNeighborService {

    private final DistanceService distanceService;

    public NearestNeighborService(DistanceService distanceService) {
        this.distanceService = distanceService;
    }

    /**
     * Finds the K nearest neighbors to a raw virtual vector (e.g. a computed centroid).
     * Unlike {@link #findNearest}, no word is filtered out since the query has no
     * corresponding entry in the space.
     */
    public List<NeighborResult.Entry> findNearestToVector(double[] targetVector, EmbeddingSpace space, int k) {
        if (k < 0) throw new IllegalArgumentException("k must be non-negative, got: " + k);
        return space.getVectors().stream()
                .map(wv -> new NeighborResult.Entry(wv.getWord(), distanceService.compute(targetVector, wv.getVector())))
                .sorted(Comparator.comparingDouble(NeighborResult.Entry::distance))
                .limit(k)
                .toList();
    }

    public NeighborResult findNearest(WordVector query, EmbeddingSpace space, int k) {
        if (k < 0) throw new IllegalArgumentException("k must be non-negative, got: " + k);
        List<NeighborResult.Entry> entries = space.getVectors().stream()
            .filter(wv -> !wv.getWord().equals(query.getWord()))
            .map(wv -> new NeighborResult.Entry(wv.getWord(), distanceService.compute(query.getVector(), wv.getVector())))
            .sorted(Comparator.comparingDouble(NeighborResult.Entry::distance))
            .limit(k)
            .toList();
        return new NeighborResult(query.getWord(), entries);
    }
}
