package service;

import metric.DistanceMetric;
import model.WordVector;

/** Computes semantic distance between pairs of word vectors using the active {@link metric.DistanceMetric}. */
public class DistanceService {

    private DistanceMetric metric;

    public DistanceService(DistanceMetric metric) {
        this.metric = metric;
    }

    public double compute(WordVector a, WordVector b) {
        return metric.compute(a.getVector(), b.getVector());
    }

    public double compute(double[] a, double[] b) {
        return metric.compute(a, b);
    }

    public void setMetric(DistanceMetric metric) {
        this.metric = metric;
    }

    public DistanceMetric getMetric() { return metric; }
}
