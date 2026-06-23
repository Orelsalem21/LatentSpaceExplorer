package app;

import metric.CosineDistance;
import metric.DistanceMetric;
import projection.PCAProjection;
import projection.ProjectionStrategy;
import projection.ProjectionContext;
import service.*;

public class AppConfig {

    public static final String PYTHON_DIR        = "python";
    public static final String PCA_FILE          = "pca_vectors.json";
    public static final String FULL_VECTORS_FILE = "full_vectors.json";
    public static final String EMBEDDER_SCRIPT   = "embedder.py";
    public static final String SESSION_EXTENSION = ".session.json";
    public static final String PYTHON_CMD        = "python";

    private final DistanceMetric          defaultMetric      = new CosineDistance();
    private final DistanceService         distanceService    = new DistanceService(defaultMetric);
    private final NearestNeighborService  neighborService    = new NearestNeighborService(distanceService);
    private final ProjectionStrategy      defaultProjection  = new PCAProjection();

    public DistanceService distanceService()   { return distanceService; }

    public ProjectionContext projectionService()  { return new ProjectionContext(defaultProjection);}

    public CentroidService  centroidService() { return new CentroidService(); }

    public NearestNeighborService  neighborService()   { return neighborService; }

    // Wired with neighborService so metric changes propagate automatically
    public VectorArithmeticService arithmeticService() { return new VectorArithmeticService(neighborService); }
}