package projection;

import exception.InvalidAxisException;
import model.EmbeddingSpace;
import model.ProjectedPoint;

import java.util.List;

/** Projects word vectors onto three chosen dimension indices for 3-D visualisation. */
public class ThreeDimensionalProjection implements ProjectionStrategy {

    private final int zAxis;

    public ThreeDimensionalProjection(int zAxis) {
        this.zAxis = zAxis;
    }

    @Override
    public List<ProjectedPoint> project(EmbeddingSpace space, int xAxis, int yAxis) throws InvalidAxisException {
        var vectors = space.getVectors();
        if (!vectors.isEmpty()) {
            int dim = vectors.getFirst().getVector().length;
            validateAxis(xAxis, dim);
            validateAxis(yAxis, dim);
            validateAxis(zAxis, dim);
        }
        return vectors.stream()
            .map(wv -> new ProjectedPoint(wv.getWord(), wv.getVector()[xAxis], wv.getVector()[yAxis], wv.getVector()[zAxis]))
            .toList();
    }

    @Override
    public String name() { return "3D"; }
}
