package projection;

import exception.InvalidAxisException;
import model.EmbeddingSpace;
import model.ProjectedPoint;
import model.WordVector;

import java.util.List;

/**
 * Projects every word onto a semantic axis defined by two words.
 * X = dot product of the word vector with the normalized (to → from) direction.
 * Y = the chosen PCA component (yAxis index).
 */
public class CustomAxisProjection implements ProjectionStrategy {

    private final double[] axisVector; // normalized direction: from → to
    private final String   fromWord;
    private final String   toWord;

    private static final double ZERO_THRESHOLD = 1e-10;

    public CustomAxisProjection(double[] fromVector, double[] toVector,
                                 String fromWord, String toWord) {
        this.fromWord = fromWord;
        this.toWord   = toWord;

        double[] diff = new double[fromVector.length];
        for (int i = 0; i < diff.length; i++) diff[i] = toVector[i] - fromVector[i];
        this.axisVector = WordVector.normalize(diff, ZERO_THRESHOLD);
    }

    @Override
    public List<ProjectedPoint> project(EmbeddingSpace space, int xAxis, int yAxis) throws InvalidAxisException {
        if (WordVector.norm(axisVector) < ZERO_THRESHOLD)
            throw new InvalidAxisException(
                    "Axis vector is zero: \"" + fromWord + "\" and \"" + toWord + "\" are identical or too similar to define an axis");

        return space.getVectors().stream()
            .map(wv -> {
                double[] v  = wv.getVector();
                int      len = Math.min(v.length, axisVector.length);
                double   x  = WordVector.dot(java.util.Arrays.copyOf(v, len),
                                              java.util.Arrays.copyOf(axisVector, len));
                double   y  = yAxis < v.length ? v[yAxis] : 0;
                return new ProjectedPoint(wv.getWord(), x, y);
            })
            .toList();
    }

    @Override
    public boolean isCustom() { return true; }

    @Override
    public String name() { return "Custom: " + fromWord + " → " + toWord; }
}
