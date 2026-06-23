package service;

import exception.WordNotFoundException;
import model.EmbeddingSpace;
import model.WordVector;

import java.util.ArrayList;
import java.util.List;

/** Computes distances between word pairs and returns formatted result lines. */
public class WordDistanceService {

    private final DistanceService distanceService;

    public WordDistanceService(DistanceService distanceService) {
        this.distanceService = distanceService;
    }

    /**
     * Validates that all words exist in the embedding space.
     * Throws WordNotFoundException if any word is not found.
     */
    public void validateWords(List<String> words, EmbeddingSpace space) throws WordNotFoundException {
        for (String word : words) {
            if (space.find(word).isEmpty()) {
                throw new WordNotFoundException(word);
            }
        }
    }

    /**
     * Computes all-pairs distances for the given word list.
     * Validation order: word count should be checked before calling this method.
     * Returns one formatted line per pair: "wa  ↔  wb :  0.1234  [Cosine Distance]"
     * Throws WordNotFoundException if any word is not found in the vocabulary.
     */
    public List<String> compute(List<String> words, EmbeddingSpace space) throws WordNotFoundException {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            for (int j = i + 1; j < words.size(); j++) {
                String wa = words.get(i);
                String wb = words.get(j);
                WordVector va = space.find(wa).orElse(null);
                WordVector vb = space.find(wb).orElse(null);
                if (va != null && vb != null) {
                    double dist = distanceService.compute(va, vb);
                    lines.add(String.format("%s  ↔  %s :  %.4f  [%s]",
                            wa, wb, dist, distanceService.getMetric().name()));
                } else {
                    String missing = va == null ? wa : wb;
                    throw new WordNotFoundException(missing);
                }
            }
        }
        return lines;
    }
}
