package view;

import utils.AlertHelper;
import utils.ButtonStyler;
import utils.ErrorMessages;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;


public class DetailsPanelView {

    private static final String SECTION_TITLE_STYLE =
            "-fx-text-fill: #1971c2; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 4 0 2 0;";
    private static final String RESULT_VALUE_STYLE =
            "-fx-text-fill: #2f9e44; -fx-font-size: 13px;";
    private static final String FIELD_STYLE =
            "-fx-background-color: #ffffff; -fx-text-fill: #212529; -fx-prompt-text-fill: #adb5bd;" +
            "-fx-border-color: #ced4da; -fx-border-radius: 4; -fx-background-radius: 4;";
    private static final String LIST_STYLE =
            "-fx-background-color: #ffffff; -fx-border-color: #ced4da; -fx-border-radius: 4;";
    private static final String LABEL_STYLE = "-fx-text-fill: #212529;";

    private final VBox root;

    private final TextField searchField = new TextField();

    private final ListView<String> selectedList = new ListView<>();

    private final Label              distanceLabel    = new Label("—");
    private final VBox               distanceWordRows = new VBox(4);
    private final List<TextField>    distanceFields   = new ArrayList<>();
    private final VBox               distanceResults  = new VBox(3);

    private final ListView<String> neighborList = new ListView<>();

    private final Label coordsLabel = new Label("");
    private final Label metricLabel = new Label("");

    private final Label centroidLabel = new Label("—");
    private final VBox            centroidWordRows = new VBox(4);
    private final List<TextField> centroidFields   = new ArrayList<>();

    private final TextField wordA = new TextField();
    private final TextField wordB = new TextField();
    private final TextField wordC = new TextField();
    private final Label     arithResult = new Label("—");

    private Consumer<String>           onSearch           = q -> {};
    private Consumer<String>           onCenterWord       = q -> {};
    private Consumer<String>           onNeighborSelected = q -> {};
    private Consumer<List<String>>     onComputeCentroid  = words -> {};
    private Consumer<List<String>>     onArithmetic           = words -> {};
    private Consumer<List<String>>     onComputeDistanceMatrix  = words -> {};
    private Consumer<Set<String>>      onDistanceWordsChanged   = words -> {};
    private List<String>               lastDistanceWords        = List.of();

    public DetailsPanelView() {
        selectedList.setPrefHeight(70);
        selectedList.setPlaceholder(new Label("No words selected"));
        selectedList.setStyle(LIST_STYLE);
        selectedList.setOnMouseClicked(e -> {
            String word = selectedList.getSelectionModel().getSelectedItem();
            if (word != null) onCenterWord.accept(word);
        });

        neighborList.setPrefHeight(130);
        neighborList.setPlaceholder(new Label("Select a word"));
        neighborList.setStyle(LIST_STYLE);
        neighborList.setOnMouseClicked(e -> {
            String row = neighborList.getSelectionModel().getSelectedItem();
            if (row == null) return;
            String word = row.split("\\s+\\(", 2)[0].trim();
            if (!word.isEmpty()) onNeighborSelected.accept(word);
        });

        distanceLabel.setStyle(RESULT_VALUE_STYLE);
        arithResult.setStyle(RESULT_VALUE_STYLE);
        centroidLabel.setStyle(RESULT_VALUE_STYLE);
        centroidLabel.setWrapText(true);

        searchField.setStyle(FIELD_STYLE);
        wordA.setStyle(FIELD_STYLE);
        wordB.setStyle(FIELD_STYLE);
        wordC.setStyle(FIELD_STYLE);

        VBox content = new VBox(12,
            buildSearchSection(),
            new Separator(),
            buildSelectedSection(),
            new Separator(),
            buildDistanceSection(),
            new Separator(),
            buildNeighborsSection(),
            new Separator(),
            buildArithmeticSection(),
            new Separator(),
            buildCentroidSection()
        );
        content.setPadding(new Insets(12));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        root = new VBox(scroll);
        root.setPrefWidth(260);
        root.setStyle("-fx-background-color: #f1f3f5; -fx-border-color: #dee2e6; -fx-border-width: 0 0 0 1;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
    }


    public void setOnSearch(Consumer<String> h)                    { this.onSearch           = h; }
    public void setOnCenterWord(Consumer<String> h)                { this.onCenterWord         = h; }
    public void setOnNeighborSelected(Consumer<String> h)          { this.onNeighborSelected   = h; }
    public void setOnComputeCentroid(Consumer<List<String>> h)     { this.onComputeCentroid  = h; }
    public void setOnArithmetic(Consumer<List<String>> h)          { this.onArithmetic             = h; }
    public void setOnComputeDistanceMatrix(Consumer<List<String>> h){ this.onComputeDistanceMatrix  = h; }
    public void setOnDistanceWordsChanged(Consumer<Set<String>> h)  { this.onDistanceWordsChanged   = h; }


    public void setArithmeticResult(String r) { arithResult.setText(r); }
    public void setCentroidStatus(String r)   { centroidLabel.setText(r); }

    public void setSelectedWordsItems(ObservableList<String> items) { selectedList.setItems(items); }
    public void setNeighborsItems(ObservableList<String> items)     { neighborList.setItems(items); }
    public void setDistanceResultText(String text)                  { distanceLabel.setText(text); }
    public void setCoordinatesText(String text)                     { coordsLabel.setText(text); }
    public void setMetricActiveText(String metricName)              { metricLabel.setText(metricName + " metric active"); }

    public void resetAll() {
        searchField.clear();
        wordA.clear();
        wordB.clear();
        wordC.clear();

        while (distanceFields.size() > 2) {
            TextField field = distanceFields.removeLast();
            distanceWordRows.getChildren().remove(field.getParent());
        }
        distanceFields.forEach(TextInputControl::clear);
        lastDistanceWords = List.of();
        distanceResults.getChildren().clear();

        while (centroidFields.size() > 2) {
            TextField field = centroidFields.removeLast();
            centroidWordRows.getChildren().remove(field.getParent());
        }
        centroidFields.forEach(TextInputControl::clear);

        arithResult.setText("-");
        centroidLabel.setText("—");
        fireDistanceWordsChanged();
    }

    public void setDistanceMatrixResult(List<String> lines) {
        distanceResults.getChildren().clear();
        for (String line : lines) {
            Label l = new Label(line);
            l.setStyle(RESULT_VALUE_STYLE + " -fx-font-size: 11px;");
            distanceResults.getChildren().add(l);
        }
    }


    private VBox buildSearchSection() {
        VBox s = new VBox(6);
        s.getChildren().add(title("Search"));

        searchField.setPromptText("Type a word…");
        searchField.setOnAction(e -> onSearch.accept(searchField.getText().trim()));

        Button searchBtn = new Button("Search");
        ButtonStyler.style(searchBtn);
        searchBtn.setMaxWidth(Double.MAX_VALUE);
        searchBtn.setOnAction(e -> onSearch.accept(searchField.getText().trim()));

        Label hint = new Label("Space Management — Search centers the view on the word");
        hint.setStyle("-fx-text-fill: #868e96; -fx-font-size: 10px;");
        hint.setWrapText(true);
        hint.setMinWidth(0);
        hint.setMaxWidth(Double.MAX_VALUE);

        s.getChildren().addAll(searchField, searchBtn, hint);
        return s;
    }

    private VBox buildSelectedSection() {
        VBox s = new VBox(6);
        Label t = title("Selected Words");
        HBox header = new HBox(8, t);

        coordsLabel.setStyle("-fx-text-fill: #1971c2; -fx-font-size: 11px;");
        coordsLabel.setWrapText(true);
        coordsLabel.setMaxWidth(Double.MAX_VALUE);
        metricLabel.setStyle("-fx-text-fill: #495057; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label hint = new Label("Shows the selected word's position in the current projection");
        hint.setStyle("-fx-text-fill: #868e96; -fx-font-size: 10px;");
        hint.setWrapText(true);
        hint.setMinWidth(0);
        hint.setMaxWidth(Double.MAX_VALUE);

        s.getChildren().addAll(header, selectedList, coordsLabel, distanceLabel, metricLabel, hint);
        return s;
    }

    private VBox buildDistanceSection() {
        VBox s = new VBox(6);
        s.getChildren().add(title("Semantic Distance"));

        addDistanceField();
        addDistanceField();

        Button addWordBtn = new Button("+ Add Word");
        ButtonStyler.style(addWordBtn);
        addWordBtn.setMaxWidth(Double.MAX_VALUE);
        addWordBtn.setOnAction(e -> addDistanceField());

        Button calcBtn = new Button("Calculate");
        ButtonStyler.style(calcBtn);
        calcBtn.setMaxWidth(Double.MAX_VALUE);
        calcBtn.setOnAction(e -> fireDistanceCalculation());

        Label hint = new Label("Semantic Distance — type 2+ words and click Calculate to compare them (Cosine / Euclidean, full 100D vectors)");
        hint.setStyle("-fx-text-fill: #868e96; -fx-font-size: 10px;");
        hint.setWrapText(true);
        hint.setMinWidth(0);
        hint.setMaxWidth(Double.MAX_VALUE);

        s.getChildren().addAll(distanceWordRows, addWordBtn, calcBtn, hint, distanceResults);
        return s;
    }

    private void fireDistanceCalculation() {
        List<String> words = new ArrayList<>();
        for (TextField f : distanceFields) {
            String w = f.getText().trim().toLowerCase();
            if (!w.isEmpty()) words.add(w);
        }
        
        // Validation order (CRITICAL for correct error messages):
        // 1. Empty field check
        if (words.isEmpty() || words.size() < 2) {
            AlertHelper.showWarning(ErrorMessages.needAtLeastTwoWords());
            return;
        }

        lastDistanceWords = new ArrayList<>(words);
        onComputeDistanceMatrix.accept(words);
    }

    public void recalculateDistance() {
        if (lastDistanceWords.size() >= 2)
            onComputeDistanceMatrix.accept(lastDistanceWords);
    }

    private void fireDistanceWordsChanged() {
        Set<String> words = new java.util.LinkedHashSet<>();
        for (TextField f : distanceFields) {
            String w = f.getText().trim().toLowerCase();
            if (!w.isEmpty()) words.add(w);
        }
        onDistanceWordsChanged.accept(words);
    }

    private void addDistanceField() {
        TextField field = new TextField();
        field.setStyle(FIELD_STYLE);
        field.setPromptText("Word " + (distanceFields.size() + 1) + "…");
        field.textProperty().addListener((obs, o, n) -> fireDistanceWordsChanged());
        field.setOnAction(e -> fireDistanceCalculation());

        Button removeBtn = new Button("✕");
        removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #868e96; -fx-cursor: hand; -fx-padding: 0 4;");
        removeBtn.setOnAction(e -> {
            if (distanceFields.size() > 2) {
                distanceWordRows.getChildren().remove(removeBtn.getParent());
                distanceFields.remove(field);
                fireDistanceWordsChanged();
            }
        });

        HBox row = new HBox(4, field, removeBtn);
        HBox.setHgrow(field, Priority.ALWAYS);
        distanceFields.add(field);
        distanceWordRows.getChildren().add(row);
    }

    private VBox buildNeighborsSection() {
        VBox s = new VBox(6);
        s.getChildren().add(title("Nearest Neighbors"));
        Label hint = new Label("Nearest Neighbor Probe — click a word in the cloud or search one to see its K closest words (full 100D vectors)");
        hint.setStyle("-fx-text-fill: #868e96; -fx-font-size: 10px;");
        hint.setWrapText(true);
        hint.setMinWidth(0);
        hint.setMaxWidth(Double.MAX_VALUE);
        s.getChildren().addAll(neighborList, hint);
        return s;
    }

    private VBox buildCentroidSection() {
        VBox s = new VBox(6);
        s.getChildren().add(title("Subspace Centroid"));

        addCentroidField();
        addCentroidField();

        Button addWordBtn = new Button("+ Add Word");
        ButtonStyler.style(addWordBtn);
        addWordBtn.setMaxWidth(Double.MAX_VALUE);
        addWordBtn.setOnAction(e -> addCentroidField());

        Button compute = new Button("Compute Centroid");
        compute.setMaxWidth(Double.MAX_VALUE);
        ButtonStyler.style(compute);
        compute.setOnAction(e -> fireComputeCentroid());

        Label hint = new Label("Subspace Grouping — type >= 2 words then click Compute");
        hint.setStyle("-fx-text-fill: #868e96; -fx-font-size: 10px;");
        hint.setWrapText(true);
        hint.setMinWidth(0);
        hint.setMaxWidth(Double.MAX_VALUE);

        s.getChildren().addAll(centroidWordRows, addWordBtn, compute, hint, centroidLabel);
        return s;
    }

    private void fireComputeCentroid() {
        List<String> words = new ArrayList<>();
        for (TextField f : centroidFields) {
            String w = f.getText().trim().toLowerCase();
            if (!w.isEmpty()) words.add(w);
        }

        if (words.size() < 2) {
            AlertHelper.showWarning(ErrorMessages.needAtLeastTwoWords());
            return;
        }

        onComputeCentroid.accept(words);
    }

    private void addCentroidField() {
        TextField field = new TextField();
        field.setStyle(FIELD_STYLE);
        field.setPromptText("Word " + (centroidFields.size() + 1) + "…");
        field.setOnAction(e -> fireComputeCentroid());

        Button removeBtn = new Button("✕");
        removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #868e96; -fx-cursor: hand; -fx-padding: 0 4;");
        removeBtn.setOnAction(e -> {
            if (centroidFields.size() > 2) {
                centroidWordRows.getChildren().remove(removeBtn.getParent());
                centroidFields.remove(field);
            }
        });

        HBox row = new HBox(4, field, removeBtn);
        HBox.setHgrow(field, Priority.ALWAYS);
        centroidFields.add(field);
        centroidWordRows.getChildren().add(row);
    }

    private VBox buildArithmeticSection() {
        VBox s = new VBox(6);
        s.getChildren().add(title("Vector Arithmetic  A - B + C"));

        wordA.setPromptText("A"); wordA.setPrefWidth(60);
        wordB.setPromptText("B"); wordB.setPrefWidth(60);
        wordC.setPromptText("C"); wordC.setPrefWidth(60);
        Button compute = new Button("=");
        ButtonStyler.style(compute);
        compute.setOnAction(e ->
                onArithmetic.accept(List.of(wordA.getText().trim(), wordB.getText().trim(), wordC.getText().trim()))
        );
        wordA.setOnAction(e -> compute.fire());
        wordB.setOnAction(e -> compute.fire());
        wordC.setOnAction(e -> compute.fire());

        Label dashLabel = new Label("-");
        Label plusLabel = new Label("+");
        dashLabel.setStyle(LABEL_STYLE);
        plusLabel.setStyle(LABEL_STYLE);
        HBox row = new HBox(4, wordA, dashLabel, wordB, plusLabel, wordC, compute);

        Label hint = new Label("Vector Arithmetic Lab — e.g. king - man + woman → queen");
        hint.setStyle("-fx-text-fill: #868e96; -fx-font-size: 10px;");
        hint.setWrapText(true);
        hint.setMinWidth(0);
        hint.setMaxWidth(Double.MAX_VALUE);

        s.getChildren().addAll(row, arithResult, hint);
        return s;
    }

    private Label title(String text) {
        Label l = new Label(text);
        l.setStyle(SECTION_TITLE_STYLE);
        return l;
    }


    public VBox getRoot() { return root; }
}
