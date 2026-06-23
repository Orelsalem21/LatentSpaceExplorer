package app;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.EmbeddingSpace;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppState {

    private EmbeddingSpace space = new EmbeddingSpace(List.of());
    private EmbeddingSpace fullSpace = null;
    private final BooleanProperty loaded = new SimpleBooleanProperty(false);

    private final ObservableList<String> selectedWords = FXCollections.observableArrayList();
    private final StringProperty distanceResult = new SimpleStringProperty("—");
    private final StringProperty coordinates = new SimpleStringProperty("");
    private final StringProperty metricName = new SimpleStringProperty("Cosine");

    private String xAxisLabel = "PC1";
    private String yAxisLabel = "PC2";

    private final ObservableList<String> neighbors = FXCollections.observableArrayList();
    private final Map<String, Double> neighborMap = new HashMap<>();

    public EmbeddingSpace getSpace() {
        return space;
    }

    public EmbeddingSpace getFullSpace() {
        return fullSpace != null ? fullSpace : space;
    }

    public boolean isLoaded() {
        return loaded.get();
    }

    public BooleanProperty loadedProperty() {
        return loaded;
    }

    public void setSpace(EmbeddingSpace space) {
        this.space = space;
        loaded.set(!space.getVectors().isEmpty());
    }

    public void setFullSpace(EmbeddingSpace fullSpace) {
        this.fullSpace = fullSpace;
    }

    public ObservableList<String> getSelectedWords() {
        return selectedWords;
    }

    public StringProperty distanceResultProperty() {
        return distanceResult;
    }

    public StringProperty coordinatesProperty() {
        return coordinates;
    }

    public StringProperty metricNameProperty() {
        return metricName;
    }

    public void setMetricName(String name) {
        metricName.set(name);
    }

    public String getXAxisLabel() {
        return xAxisLabel;
    }

    public String getYAxisLabel() {
        return yAxisLabel;
    }

    public void setAxisLabels(String x, String y) {
        xAxisLabel = x;
        yAxisLabel = y;
    }

    public ObservableList<String> getNeighbors() {
        return neighbors;
    }

    public Map<String, Double> getNeighborMap() {
        return neighborMap;
    }

    public Set<String> getNeighborWords() {
        return neighborMap.keySet();
    }

    public void setNeighborMap(Map<String, Double> map) {
        neighborMap.clear();
        neighborMap.putAll(map);
    }

    public void clearNeighbors() {
        neighbors.clear();
        neighborMap.clear();
    }
}