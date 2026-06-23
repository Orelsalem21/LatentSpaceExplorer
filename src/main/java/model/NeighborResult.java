package model;

import java.util.List;

/**
 * Holds the nearest-neighbor result for a queried word.
 */
public class NeighborResult {

    /**
     * Represents a single neighbor and its distance.
     */
    public record Entry(String word, double distance) {}

    private final String queryWord;
    private final List<Entry> neighbors;

    public NeighborResult(String queryWord, List<Entry> neighbors) {
        this.queryWord = queryWord;
        this.neighbors = List.copyOf(neighbors);
    }

    public String      getQueryWord() { return queryWord; }
    public List<Entry> getNeighbors() { return neighbors; }
}