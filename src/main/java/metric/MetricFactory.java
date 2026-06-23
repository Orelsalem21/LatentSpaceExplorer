package metric;

import exception.UnknownMetricException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provides access to the application's supported distance metrics.
 */
public class MetricFactory {

    private static final Map<String, DistanceMetric> METRICS = new LinkedHashMap<>();

    static {
        METRICS.put("Cosine",    new CosineDistance());
        METRICS.put("Euclidean", new EuclideanDistance());
    }

    /**
     * Returns the metric associated with the given name.
     */
    public static DistanceMetric get(String name) throws UnknownMetricException {
        DistanceMetric metric = METRICS.get(name);

        if (metric == null) {
            throw new UnknownMetricException(name);
        }

        return metric;
    }

    /**
     * Returns the names of all supported metrics.
     */
    public static Set<String> names() {
        return METRICS.keySet();
    }
}