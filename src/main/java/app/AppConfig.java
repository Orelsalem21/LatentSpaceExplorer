package app;

import metric.CosineDistance;
import metric.DistanceMetric;
import projection.PCAProjection;
import projection.ProjectionStrategy;
import projection.ProjectionContext;
import service.*;

public class AppConfig {

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