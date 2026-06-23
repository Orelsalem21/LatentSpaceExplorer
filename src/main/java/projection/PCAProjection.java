package projection;

import exception.InvalidAxisException;
import model.EmbeddingSpace;
import model.ProjectedPoint;

import java.util.List;

/** Projects word vectors onto two chosen principal-component axes (dimension indices). */
public class PCAProjection implements ProjectionStrategy {

    @Override
    public List<ProjectedPoint> project(EmbeddingSpace space, int xAxis, int yAxis) throws InvalidAxisException {
        var vectors = space.getVectors();
        if (!vectors.isEmpty()) {
            int dim = vectors.getFirst().getVector().length;
            validateAxis(xAxis, dim);
            validateAxis(yAxis, dim);
        }
        return vectors.stream()
            .map(wv -> new ProjectedPoint(wv.getWord(), wv.getVector()[xAxis], wv.getVector()[yAxis]))
            .toList();
    }

    @Override
    public String name() { return "PCA"; }
}
