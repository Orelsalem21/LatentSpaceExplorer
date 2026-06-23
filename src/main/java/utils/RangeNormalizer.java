package utils;

import java.util.Map;

public class RangeNormalizer {

    private final double min;
    private final double range;

    public RangeNormalizer(Map<String, Double> distanceMap) {
        this.min = distanceMap.values().stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = distanceMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        this.range = (max - min < 1e-9) ? 1.0 : max - min;
    }

    public double normalize(double value) {
        return (value - min) / range;
    }
}
