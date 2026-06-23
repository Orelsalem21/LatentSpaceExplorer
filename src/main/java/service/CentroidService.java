package service;

import model.CentroidResult;
import model.WordVector;

import java.util.List;

/** Computes the geometric centroid (mean vector) of a set of word vectors. */
public class CentroidService {

    public CentroidResult compute(List<WordVector> words) {
        if (words.isEmpty()) throw new IllegalArgumentException("Cannot compute centroid of empty list");

        int dim = words.get(0).getDimension();
        for (WordVector wv : words) {
            if (wv.getDimension() != dim)
                throw new IllegalArgumentException(
                        "Dimension mismatch: expected " + dim + ", got " + wv.getDimension()
                        + " for word \"" + wv.getWord() + "\"");
        }

        double[] centroid = new double[dim];
        for (WordVector wv : words) {
            double[] v = wv.getVector();
            for (int i = 0; i < dim; i++) centroid[i] += v[i];
        }
        for (int i = 0; i < dim; i++) centroid[i] /= words.size();

        List<String> sourceWords = words.stream().map(WordVector::getWord).toList();
        return new CentroidResult(centroid, sourceWords);
    }
}
