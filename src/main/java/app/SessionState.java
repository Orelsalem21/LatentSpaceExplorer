package app;

import java.util.List;

public class SessionState {

    private List<String> selectedWords;
    private String       metricName;
    private int[]        axes;
    private boolean      is3D;
    private Integer      dataFingerprint;

    public SessionState() {}

    public List<String> getSelectedWords()   { return selectedWords; }
    public String       getMetricName()      { return metricName; }
    public int[]        getAxes()            { return axes; }
    public boolean      isIs3D()             { return is3D; }
    public Integer      getDataFingerprint() { return dataFingerprint; }

    public void setSelectedWords(List<String> words)     { this.selectedWords    = words; }
    public void setMetricName(String metricName)         { this.metricName       = metricName; }
    public void setAxes(int[] axes)                      { this.axes             = axes; }
    public void setIs3D(boolean is3D)                    { this.is3D             = is3D; }
    public void setDataFingerprint(Integer dataFingerprint) { this.dataFingerprint = dataFingerprint; }
}