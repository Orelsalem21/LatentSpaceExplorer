package view;

import model.ProjectedPoint;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public interface WordCloudView {
    void setPoints(List<ProjectedPoint> points);
    void setAxisLabels(String x, String y, String z);
    void setSelected(Set<String> words);
    void setNeighbors(Map<String, Double> neighbors);
    void setDistanceWords(Set<String> words);
    void setArithPath(List<String> path);
    void clearArithPath();
    void centerOnWord(String word);
    void resetView();
    void setOnWordSelected(Consumer<String> handler);
}
