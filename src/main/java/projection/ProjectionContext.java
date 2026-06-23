package projection;

import exception.InvalidAxisException;
import model.EmbeddingSpace;
import model.ProjectedPoint;

import java.util.List;

/** Manages the active {@link ProjectionStrategy} and delegates projection calls to it. */
public class ProjectionContext {

    private static final ProjectionStrategy PCA = new PCAProjection();

    private ProjectionStrategy strategy;

    public ProjectionContext(ProjectionStrategy strategy) {
        this.strategy = strategy;
    }

    public List<ProjectedPoint> project(EmbeddingSpace space, int xAxis, int yAxis)
            throws InvalidAxisException {
        return strategy.project(space, xAxis, yAxis);
    }

    public void usePCAProjection() {
        this.strategy = PCA;
    }

    public void useThreeDimensionalProjection(int zAxis) {
        this.strategy = new ThreeDimensionalProjection(zAxis);
    }

    public void useCustomAxisProjection(
            double[] fromVector,
            double[] toVector,
            String fromWord,
            String toWord
    ) {
        this.strategy = new CustomAxisProjection(fromVector, toVector, fromWord, toWord);
    }

    public boolean isCustomProjection() {
        return strategy.isCustom();
    }

    public void setStrategy(ProjectionStrategy strategy) {
        this.strategy = strategy;
    }

    public ProjectionStrategy getStrategy() {
        return strategy;
    }
}
