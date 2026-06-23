package model;

import java.util.*;

/**
 * Represents a collection of word vectors with fast word lookup support.
 */
public class EmbeddingSpace {

    private final List<WordVector> vectors;
    private final Map<String, WordVector> index;

    public EmbeddingSpace(List<WordVector> vectors) {
        if (vectors.size() > 1) {
            int dim = vectors.get(0).getDimension();
            for (int i = 1; i < vectors.size(); i++) {
                int d = vectors.get(i).getDimension();
                if (d != dim)
                    throw new IllegalArgumentException(
                            "Dimension mismatch at index " + i + ": expected " + dim + ", got " + d);
            }
        }
        this.vectors = List.copyOf(vectors);
        this.index = new HashMap<>();
        for (WordVector wv : vectors) {
            index.put(wv.getWord().toLowerCase(), wv);
        }
    }

    public List<WordVector> getVectors() { return vectors; }

    public int size() { return vectors.size(); }

    public int getDimension() { return vectors.isEmpty() ? 0 : vectors.getFirst().getDimension(); }

    public Optional<WordVector> find(String word) {
        return Optional.ofNullable(index.get(word.toLowerCase()));
    }

    public boolean contains(String word) {
        return index.containsKey(word.toLowerCase());
    }
}