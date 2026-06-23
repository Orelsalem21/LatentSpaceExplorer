package exception;

import utils.ErrorMessages;

/**
 * Thrown when a requested distance metric is not recognized
 * by the metric factory or metric registry.
 */
public class UnknownMetricException extends Exception {

    /**
     * Creates a new unknown-metric exception.
     *
     * @param metricName the unsupported metric name
     */
    public UnknownMetricException(String metricName) {
        super(ErrorMessages.unknownMetric(metricName));
    }
}