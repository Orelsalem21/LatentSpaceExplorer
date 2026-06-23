package view;

import utils.AlertHelper;
import utils.ButtonStyler;
import utils.ErrorMessages;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import metric.MetricFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Left-side configuration panel: data loading, axis selection, custom-axis projection, metric, K neighbours, and display mode. */
public class ControlPanelView {

    private static final String SECTION_TITLE_STYLE =
            "-fx-text-fill: #1971c2; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 4 0 2 0;";
    private static final String FIELD_STYLE =
            "-fx-background-color: #ffffff; -fx-text-fill: #212529; -fx-prompt-text-fill: #adb5bd;" +
            "-fx-border-color: #ced4da; -fx-border-radius: 4; -fx-background-radius: 4;";
    private static final String COMBO_STYLE =
            "-fx-background-color: #ffffff; -fx-text-fill: #212529; -fx-border-color: #ced4da;";
    private static final String LABEL_STYLE  = "-fx-text-fill: #212529;";
    private static final String RADIO_STYLE  = "-fx-text-fill: #212529;";

    private final VBox root;

    private Runnable                   onLoadFile         = () -> {};
    private Runnable                   onSaveAs           = () -> {};
    private Consumer<int[]>            onAxesChanged      = axes -> {};
    private Consumer<String>           onMetricChanged    = m -> {};
    private Consumer<Integer>          onKChanged         = k -> {};
    private Consumer<Boolean>          onModeChanged      = is3D -> {};
    private java.util.function.BiConsumer<String, String> onCustomProjection = (a, b) -> {};

    private boolean suppressCallbacks = false;

    private final ComboBox<String> xAxisCombo = buildAxisCombo("X Axis");
    private final ComboBox<String> yAxisCombo = buildAxisCombo("Y Axis");
    private final ComboBox<String> zAxisCombo = buildAxisCombo("Z Axis (3D)");
    private final Slider    kSlider = new Slider(0, 100, 5);
    private       int       maxK    = 4999;
    private       TextField kField;
    private final Map<String, RadioButton> metricButtons = new LinkedHashMap<>();
    private       RadioButton mode2D;
    private       RadioButton mode3D;

    private final TextField axisFrom   = new TextField();
    private final TextField axisTo     = new TextField();
    private final Label     axisResult = new Label("—");

    public ControlPanelView() {
        root = new VBox(12);
        root.setPadding(new Insets(12));
        root.setPrefWidth(220);
        root.setStyle("-fx-background-color: #f1f3f5; -fx-border-color: #dee2e6; -fx-border-width: 0 1 0 0;");

        axisFrom.setStyle(FIELD_STYLE);
        axisTo.setStyle(FIELD_STYLE);
        axisResult.setStyle("-fx-text-fill: #2f9e44; -fx-font-size: 13px;");

        root.getChildren().addAll(
            buildDataSection(),
            new Separator(),
            buildProjectionSection(),
            new Separator(),
            buildCustomAxisSection(),
            new Separator(),
            buildMetricSection(),
            new Separator(),
            buildNeighborsSection(),
            new Separator(),
            buildModeSection()
        );
    }

    public void setOnLoadFile(Runnable r)  { this.onLoadFile = r; }
    public void setOnSaveAs(Runnable r)    { this.onSaveAs   = r; }
    public void setOnAxesChanged(Consumer<int[]> c)  { this.onAxesChanged   = c; }
    public void setOnMetricChanged(Consumer<String> c){ this.onMetricChanged = c; }
    public void setOnKChanged(Consumer<Integer> c)   { this.onKChanged      = c; }
    public void setOnModeChanged(Consumer<Boolean> c) { this.onModeChanged  = c; }
    public void setOnCustomProjection(java.util.function.BiConsumer<String, String> h) { this.onCustomProjection = h; }

    public void setAxisProjectionResult(String r) { axisResult.setText(r); }

    public void resetCustomAxis() {
        axisFrom.clear();
        axisTo.clear();
        axisResult.setText("—");
    }

    private VBox buildDataSection() {
        VBox section = new VBox(6);
        Label title = new Label("Data");
        title.setStyle(SECTION_TITLE_STYLE);

        Button loadJson = new Button("Load File");
        Button saveAs   = new Button("Save As");
        loadJson.setMaxWidth(Double.MAX_VALUE);
        saveAs.setMaxWidth(Double.MAX_VALUE);
        ButtonStyler.style(loadJson);
        ButtonStyler.style(saveAs);

        loadJson.setOnAction(e -> onLoadFile.run());
        saveAs.setOnAction(e   -> onSaveAs.run());

        section.getChildren().addAll(title, loadJson, saveAs);
        return section;
    }

    private VBox buildProjectionSection() {
        VBox section = new VBox(6);
        Label title = new Label("Axis Selection");
        title.setStyle(SECTION_TITLE_STYLE);

        xAxisCombo.setStyle(COMBO_STYLE);
        yAxisCombo.setStyle(COMBO_STYLE);
        zAxisCombo.setStyle(COMBO_STYLE);

        xAxisCombo.getSelectionModel().select(0);
        yAxisCombo.getSelectionModel().select(1);
        zAxisCombo.getSelectionModel().select(2);

        xAxisCombo.setOnAction(e -> fireAxesChanged());
        yAxisCombo.setOnAction(e -> fireAxesChanged());
        zAxisCombo.setOnAction(e -> fireAxesChanged());

        HBox xRow = buildAxisRow("X =", xAxisCombo);
        HBox yRow = buildAxisRow("Y =", yAxisCombo);
        HBox zRow = buildAxisRow("Z =", zAxisCombo);

        Label hint = new Label("Axis Selection — choose PCA components for X/Y axes (PC1–PC50)");
        hint.setStyle("-fx-text-fill: #868e96; -fx-font-size: 10px;");
        hint.setWrapText(true);
        hint.setMinWidth(0);
        hint.setMaxWidth(Double.MAX_VALUE);

        section.getChildren().addAll(title, xRow, yRow, zRow, hint);
        return section;
    }

    private void fireAxesChanged() {
        if (suppressCallbacks) return;
        int x = xAxisCombo.getSelectionModel().getSelectedIndex();
        int y = yAxisCombo.getSelectionModel().getSelectedIndex();
        int z = zAxisCombo.getSelectionModel().getSelectedIndex();
        onAxesChanged.accept(new int[]{x, y, z});
    }

    public void setAxes(int[] axes) {
        suppressCallbacks = true;
        xAxisCombo.getSelectionModel().select(axes[0]);
        yAxisCombo.getSelectionModel().select(axes[1]);
        zAxisCombo.getSelectionModel().select(axes.length > 2 ? axes[2] : 2);
        suppressCallbacks = false;
    }

    public void setMetric(String name) {
        suppressCallbacks = true;
        RadioButton btn = metricButtons.get(name);
        if (btn != null) btn.setSelected(true);
        suppressCallbacks = false;
    }

    public void setMode(boolean is3D) {
        suppressCallbacks = true;
        if (is3D) mode3D.setSelected(true);
        else      mode2D.setSelected(true);
        suppressCallbacks = false;
    }

    public void setK(int k) {
        suppressCallbacks = true;
        int val = Math.max(0, Math.min(k, maxK));
        kField.setText(String.valueOf(val));
        kField.setStyle(FIELD_STYLE);
        kSlider.setValue(Math.min(val, kSlider.getMax()));
        suppressCallbacks = false;
    }

    public void resetControls(boolean is3D) {
        setAxes(new int[]{0, 1, 2});
        setMetric("Cosine");
        setMode(is3D);
        setK(5);
    }

    private HBox buildAxisRow(String labelText, ComboBox<String> combo) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-min-width: 28px;");
        label.setAlignment(Pos.CENTER_RIGHT);
        HBox row = new HBox(6, label, combo);
        row.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.layout.HBox.setHgrow(combo, javafx.scene.layout.Priority.ALWAYS);
        return row;
    }

    private ComboBox<String> buildAxisCombo(String prompt) {
        ComboBox<String> combo = new ComboBox<>();
        combo.setPromptText(prompt);
        combo.setMaxWidth(Double.MAX_VALUE);
        for (int i = 1; i <= 50; i++) combo.getItems().add("PC" + i);
        return combo;
    }

    private VBox buildMetricSection() {
        VBox section = new VBox(6);
        Label title = new Label("Metric");
        title.setStyle(SECTION_TITLE_STYLE);

        ToggleGroup group = new ToggleGroup();
        MetricFactory.names().forEach(name -> {
            RadioButton btn = new RadioButton(name);
            btn.setToggleGroup(group);
            btn.setStyle(RADIO_STYLE);
            metricButtons.put(name, btn);
        });
        metricButtons.values().stream().findFirst().ifPresent(btn -> btn.setSelected(true));

        group.selectedToggleProperty().addListener((obs, o, n) -> {
            if (suppressCallbacks) return;
            metricButtons.forEach((name, btn) -> {
                if (n == btn) onMetricChanged.accept(name);
            });
        });

        HBox row = new HBox(16);
        row.getChildren().addAll(metricButtons.values());
        row.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("Semantic Distance — Cosine Similarity / Euclidean Distance");
        hint.setStyle("-fx-text-fill: #868e96; -fx-font-size: 10px;");
        hint.setWrapText(true);
        hint.setMinWidth(0);
        hint.setMaxWidth(Double.MAX_VALUE);

        section.getChildren().addAll(title, row, hint);
        return section;
    }

    private VBox buildNeighborsSection() {
        VBox section = new VBox(6);
        Label title = new Label("Neighbors");
        title.setStyle(SECTION_TITLE_STYLE);

        kSlider.setShowTickLabels(true);
        kSlider.setMajorTickUnit(10);

        kSlider.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(() -> {
                    Node track = kSlider.lookup(".track");
                    Node thumb = kSlider.lookup(".thumb");
                    if (track != null) track.setStyle("-fx-background-color: #ced4da;");
                    if (thumb != null) thumb.setStyle("-fx-background-color: #1971c2;");
                });
            }
        });

        kField = new TextField("5");
        kField.setPrefWidth(52);
        kField.setMaxWidth(52);
        kField.setStyle(FIELD_STYLE);

        Label kLabel = new Label("K =");
        kLabel.setStyle(LABEL_STYLE);
        HBox kRow = new HBox(6, kLabel, kField);
        kRow.setAlignment(Pos.CENTER_LEFT);

        Label kHint = new Label("Nearest Neighbor Probe — K closest words in full space");
        kHint.setStyle("-fx-text-fill: #868e96; -fx-font-size: 10px;");
        kHint.setWrapText(true);
        kHint.setMinWidth(0);
        kHint.setMaxWidth(Double.MAX_VALUE);

        kSlider.valueProperty().addListener((obs, o, n) -> {
            kField.setText(String.valueOf(n.intValue()));
            kField.setStyle(FIELD_STYLE);
            if (!suppressCallbacks) onKChanged.accept(n.intValue());
        });

        Runnable applyField = () -> {
            try {
                int val = Integer.parseInt(kField.getText().trim());
                if (val < 0) val = 0;
                if (val > maxK) {
                    AlertHelper.showWarning(ErrorMessages.invalidK(maxK));
                    kField.setText(String.valueOf((int) kSlider.getValue()));
                    return;
                }
                kField.setStyle(FIELD_STYLE);
                kField.setText(String.valueOf(val));
                if (val <= (int) kSlider.getMax()) {
                    kSlider.setValue(val);
                } else {
                    kSlider.setValue(kSlider.getMax());
                    onKChanged.accept(val);
                }
            } catch (NumberFormatException ignored) {
                AlertHelper.showWarning(ErrorMessages.invalidWholeNumber());
                kField.setText(String.valueOf((int) kSlider.getValue()));
            }
        };

        kField.setOnAction(e -> applyField.run());
        kField.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) applyField.run();
        });

        section.getChildren().addAll(title, kSlider, kRow, kHint);
        return section;
    }

    private VBox buildModeSection() {
        VBox section = new VBox(6);
        Label title = new Label("Mode");
        title.setStyle(SECTION_TITLE_STYLE);

        ToggleGroup group = new ToggleGroup();
        mode2D = new RadioButton("2D");
        mode3D = new RadioButton("3D");
        mode2D.setToggleGroup(group);
        mode3D.setToggleGroup(group);
        mode2D.setSelected(true);
        mode2D.setStyle(RADIO_STYLE);
        mode3D.setStyle(RADIO_STYLE);

        group.selectedToggleProperty().addListener((obs, o, n) -> {
            if (!suppressCallbacks) onModeChanged.accept(n == mode3D);
        });

        HBox row = new HBox(16, mode2D, mode3D);
        row.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("3D Visualization — navigate the word cloud in 3D space");
        hint.setStyle("-fx-text-fill: #868e96; -fx-font-size: 10px;");
        hint.setWrapText(true);
        hint.setMinWidth(0);
        hint.setMaxWidth(Double.MAX_VALUE);

        section.getChildren().addAll(title, row, hint);
        return section;
    }

    public void setMaxK(int spaceSize) {
        this.maxK = spaceSize - 1;
        if (kField != null) kField.setStyle(FIELD_STYLE);
    }

    private VBox buildCustomAxisSection() {
        VBox s = new VBox(6);
        Label title = new Label("Custom Axis Projection");
        title.setStyle(SECTION_TITLE_STYLE);

        axisFrom.setPromptText("From (e.g. poor)");
        axisTo.setPromptText("To  (e.g. rich)");
        axisFrom.setMaxWidth(Double.MAX_VALUE);
        axisTo.setMaxWidth(Double.MAX_VALUE);

        Button project = new Button("Project →");
        axisFrom.setOnAction(e -> project.fire());
        axisTo.setOnAction(e -> project.fire());
        project.setMaxWidth(Double.MAX_VALUE);
        ButtonStyler.style(project);
        project.setOnAction(e -> {
            String f = axisFrom.getText().trim().toLowerCase();
            String t = axisTo.getText().trim().toLowerCase();
            
            // Tier 1: Check empty fields first
            if (f.isEmpty() || t.isEmpty()) {
                if (f.isEmpty() && t.isEmpty()) {
                    AlertHelper.showWarning(ErrorMessages.fillBothFromAndToFields());
                } else if (f.isEmpty()) {
                    AlertHelper.showWarning(ErrorMessages.fillFromField());
                } else {
                    AlertHelper.showWarning(ErrorMessages.fillToField());
                }
                return;
            }

            axisResult.setText(f + " → " + t);
            onCustomProjection.accept(f, t);
        });

        Label hint = new Label("Custom Projection — semantic axis between two words");
        hint.setStyle("-fx-text-fill: #868e96; -fx-font-size: 10px;");
        hint.setWrapText(true);
        hint.setMinWidth(0);
        hint.setMaxWidth(Double.MAX_VALUE);

        s.getChildren().addAll(title, axisFrom, axisTo, project, hint, axisResult);
        return s;
    }

    /** Returns the K value currently shown in the UI (field takes priority over slider). */
    public int getCurrentK() {
        try {
            return Integer.parseInt(kField.getText().trim());
        } catch (NumberFormatException e) {
            return (int) kSlider.getValue();
        }
    }


    public VBox getRoot() { return root; }
    }
