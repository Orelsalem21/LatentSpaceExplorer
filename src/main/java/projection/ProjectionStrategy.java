package projection;

import exception.InvalidAxisException;
import model.EmbeddingSpace;
import model.ProjectedPoint;

import java.util.List;

public interface ProjectionStrategy {
    List<ProjectedPoint> project(EmbeddingSpace space, int xAxis, int yAxis) throws InvalidAxisException;
    String name();
    default boolean isCustom() { return false; }

    default void validateAxis(int axis, int dimension) throws InvalidAxisException {
        if (axis < 0 || axis >= dimension)
            throw new InvalidAxisException("Invalid axis index: " + axis + " (dimension=" + dimension + ")");
    }
}
