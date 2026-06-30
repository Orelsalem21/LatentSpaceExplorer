package view;

import utils.RangeNormalizer;
import model.ProjectedPoint;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;
import java.util.function.Consumer;

/** JavaFX Canvas-based 2-D word cloud: renders projected points, selection highlights, neighbor lines, and arithmetic paths. */
public class WordCloud2DView implements WordCloudView {

    private static final double POINT_RADIUS   = 5.0;
    private static final double PADDING        = 60.0;
    private static final Color  COLOR_DOT      = Color.web("#1971c2");
    private static final Color  COLOR_SELECT   = Color.web("#c92a2a");
    private static final Color  COLOR_NEIGHBOR = Color.web("#2f9e44");
    private static final Color  COLOR_DISTANCE = Color.web("#7048e8");
    private static final Color  COLOR_LINE     = Color.web("#343a40");
    private static final Color  COLOR_AXIS_X   = Color.web("#e67700");
    private static final Color  COLOR_AXIS_Y   = Color.web("#2f9e44");

    private final BorderPane outerRoot;
    private final Canvas    canvas;
    private final Label     arithBar = new Label();

    private List<ProjectedPoint> points        = new ArrayList<>();
    private Set<String>          selected      = Set.of();
    private Map<String, Double>  neighbors     = Map.of();
    private Set<String>          distanceWords = Set.of();
    private List<String>         arithPath     = List.of(); // [A, B, C, result]

    private String xLabel = "PC1";
    private String yLabel = "PC2";
    private String statusMessage = "Load a file to display the word cloud";

    private double minX, maxX, minY, maxY;
    private double offsetX = 0, offsetY = 0;
    private double scale   = 1.0;
    private double dragStartX, dragStartY;

    private Consumer<String> onWordSelected = w -> {};

    private final javafx.scene.control.Label hoverLabel = new javafx.scene.control.Label();

    public WordCloud2DView() {
        StackPane root = new StackPane();
        canvas = new Canvas();
        root.setStyle("-fx-background-color: #ffffff;");

        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());
        canvas.widthProperty().addListener(e -> redraw());
        canvas.heightProperty().addListener(e -> redraw());

        hoverLabel.setVisible(false);
        hoverLabel.setMouseTransparent(true);
        hoverLabel.setStyle("-fx-background-color: rgba(255,255,255,0.93);" +
            "-fx-border-color:#ced4da;-fx-border-radius:3;-fx-background-radius:3;" +
            "-fx-padding:2 7;-fx-font-size:11px;-fx-text-fill:#212529;");
        Pane overlay = new Pane();
        overlay.getChildren().add(hoverLabel);
        overlay.setPickOnBounds(false);
        overlay.setMouseTransparent(true);

        attachPanHandler();
        attachZoomHandler();
        attachClickHandler();
        attachHoverHandler();

        root.getChildren().addAll(canvas, overlay);

        arithBar.setVisible(false);
        arithBar.setMaxWidth(Double.MAX_VALUE);
        arithBar.setStyle(
            "-fx-background-color: #fff3bf;" +
            "-fx-border-color: #e67700;" +
            "-fx-border-width: 1 0 0 0;" +
            "-fx-padding: 5 12;" +
            "-fx-font-size: 12px;" +
            "-fx-text-fill: #7c4a00;" +
            "-fx-font-weight: bold;");

        outerRoot = new BorderPane();
        outerRoot.setCenter(root);
        outerRoot.setBottom(arithBar);
    }


    public void setPoints(List<ProjectedPoint> points) {
        this.points = new ArrayList<>(points);
        resetBounds();
        redraw();
    }

    public void clearHighlights() {
        this.selected  = Set.of();
        this.neighbors = Map.of();
        redraw();
    }

    public void resetView() {
        offsetX = 0; offsetY = 0; scale = 1.0;
        redraw();
    }

    @Override
    public void setAxisLabels(String x, String y, String z) {
        setAxisLabels(x, y);
    }

    public void setAxisLabels(String xLabel, String yLabel) {
        this.xLabel = xLabel;
        this.yLabel = yLabel;
        redraw();
    }

    public void setSelected(Set<String> selected)           { this.selected      = selected;      redraw(); }
    public void setNeighbors(Map<String, Double> neighbors) { this.neighbors     = neighbors;     redraw(); }
    public void setDistanceWords(Set<String> words)         { this.distanceWords = words;         redraw(); }
    public void setArithPath(List<String> path) {
        this.arithPath = path;
        updateArithBar();
        redraw();
    }
    public void clearArithPath() {
        this.arithPath = List.of();
        updateArithBar();
        redraw();
    }

    private void updateArithBar() {
        if (arithPath.size() < 4) {
            arithBar.setVisible(false);
            return;
        }
        String[] labels = {"A", "B", "C", "Result"};
        StringBuilder sb = new StringBuilder("Vector Arithmetic:  ");
        for (int i = 0; i < arithPath.size(); i++) {
            if (i > 0) sb.append("  →  ");
            sb.append(arithPath.get(i));
            sb.append(" (").append(i < labels.length ? labels[i] : "").append(")");
        }
        arithBar.setText(sb.toString());
        arithBar.setVisible(true);
    }

    public void setOnWordSelected(Consumer<String> h) { this.onWordSelected = h; }
    public void setStatusMessage(String msg)          { this.statusMessage  = msg; redraw(); }

    /** Pan the canvas so that the given word appears at the center. */
    public void centerOnWord(String word) {
        if (canvas.getWidth() == 0 || points.isEmpty()) return;
        points.stream()
            .filter(p -> p.getWord().equals(word))
            .findFirst()
            .ifPresent(p -> {
                double px = toCanvasX(p.getX());
                double py = toCanvasY(p.getY());
                offsetX += canvas.getWidth()  / 2 - px;
                offsetY += canvas.getHeight() / 2 - py;
                redraw();
            });
    }


    private void resetBounds() {
        if (points.isEmpty()) return;
        minX = points.stream().mapToDouble(ProjectedPoint::getX).min().orElse(-1);
        maxX = points.stream().mapToDouble(ProjectedPoint::getX).max().orElse(1);
        minY = points.stream().mapToDouble(ProjectedPoint::getY).min().orElse(-1);
        maxY = points.stream().mapToDouble(ProjectedPoint::getY).max().orElse(1);
        offsetX = 0;
        offsetY = 0;
        scale   = 1.0;
    }

    private double toCanvasX(double px) {
        double range = maxX - minX == 0 ? 1 : maxX - minX;
        return offsetX + PADDING + ((px - minX) / range) * (canvas.getWidth() - 2 * PADDING) * scale;
    }

    private double toCanvasY(double py) {
        double range = maxY - minY == 0 ? 1 : maxY - minY;
        return offsetY + PADDING + (1.0 - (py - minY) / range) * (canvas.getHeight() - 2 * PADDING) * scale;
    }


    public void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (points.isEmpty()) {
            gc.setFill(Color.web("#adb5bd"));
            gc.setFont(Font.font(14));
            gc.fillText(statusMessage, 60, canvas.getHeight() / 2);
            return;
        }

        drawAxes(gc);
        drawRegularDots(gc);
        drawNeighborLines(gc);  // over regular dots
        drawArithPath(gc);       // arithmetic path on top
        drawHighlightedDots(gc); // selected + neighbors on top
    }

    private void drawAxes(GraphicsContext gc) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        double axisY = toCanvasY(0);
        gc.setStroke(COLOR_AXIS_X);
        gc.setLineWidth(1.2);
        gc.setLineDashes(6, 4);
        gc.strokeLine(0, axisY, w, axisY);
        gc.setLineDashes(0);
        gc.setFill(COLOR_AXIS_X);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        gc.fillText(xLabel + " →", w - 55, axisY - 6);

        double axisX = toCanvasX(0);
        gc.setStroke(COLOR_AXIS_Y);
        gc.setLineWidth(1.2);
        gc.setLineDashes(6, 4);
        gc.strokeLine(axisX, 0, axisX, h);
        gc.setLineDashes(0);
        gc.setFill(COLOR_AXIS_Y);
        gc.fillText("↑ " + yLabel, axisX + 5, 18);
    }

    private void drawRegularDots(GraphicsContext gc) {
        boolean hasActiveState = !selected.isEmpty() || !neighbors.isEmpty()
                              || !distanceWords.isEmpty() || !arithPath.isEmpty();
        Color dotColor = hasActiveState
            ? Color.rgb(160, 160, 160, 0.55)
            : COLOR_DOT;
        gc.setFill(dotColor);
        for (ProjectedPoint p : points) {
            String word = p.getWord();
            if (selected.contains(word) || neighbors.containsKey(word)
                    || distanceWords.contains(word) || arithPath.contains(word)) continue;
            double cx = toCanvasX(p.getX());
            double cy = toCanvasY(p.getY());
            gc.fillOval(cx - POINT_RADIUS, cy - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
        }
    }

    private void drawNeighborLines(GraphicsContext gc) {
        if (selected.isEmpty() || neighbors.isEmpty()) return;

        RangeNormalizer normalizer = new RangeNormalizer(neighbors);

        points.stream().filter(p -> selected.contains(p.getWord())).forEach(sel ->
            points.stream().filter(p -> neighbors.containsKey(p.getWord())).forEach(nb -> {
                double dist       = neighbors.get(nb.getWord());
                double normalized = normalizer.normalize(dist);          // 0 = closest, 1 = furthest
                double lineWidth  = 0.8 + (1.0 - normalized) * 3.2;   // 4.0 (closest) → 0.8 (furthest)
                gc.setStroke(COLOR_LINE);
                gc.setLineWidth(lineWidth);
                gc.strokeLine(toCanvasX(sel.getX()), toCanvasY(sel.getY()),
                              toCanvasX(nb.getX()),  toCanvasY(nb.getY()));
            }));
    }

    private void drawHighlightedDots(GraphicsContext gc) {
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));

        for (ProjectedPoint p : points) {
            if (!neighbors.containsKey(p.getWord())) continue;
            double cx = toCanvasX(p.getX());
            double cy = toCanvasY(p.getY());
            gc.setFill(COLOR_NEIGHBOR);
            gc.fillOval(cx - POINT_RADIUS, cy - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
            gc.fillText(p.getWord(), cx + POINT_RADIUS + 3, cy + 4);
        }

        for (ProjectedPoint p : points) {
            if (!distanceWords.contains(p.getWord())) continue;
            double r  = POINT_RADIUS * 1.4;
            double cx = toCanvasX(p.getX());
            double cy = toCanvasY(p.getY());
            gc.setFill(COLOR_DISTANCE);
            gc.fillOval(cx - r, cy - r, r * 2, r * 2);
            gc.fillText(p.getWord(), cx + r + 3, cy + 4);
        }

        for (ProjectedPoint p : points) {
            if (!selected.contains(p.getWord())) continue;
            double r  = POINT_RADIUS * 1.6;
            double cx = toCanvasX(p.getX());
            double cy = toCanvasY(p.getY());
            gc.setFill(COLOR_SELECT);
            gc.fillOval(cx - r, cy - r, r * 2, r * 2);
            gc.fillText(p.getWord(), cx + r + 3, cy + 4);
        }
    }

    private void drawArithPath(GraphicsContext gc) {
        if (arithPath.size() < 2) return;
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        Color pathColor = Color.web("#e67700");
        gc.setStroke(pathColor);
        gc.setLineWidth(2.0);
        gc.setLineDashes(8, 4);

        List<ProjectedPoint> pathPoints = arithPath.stream()
                .map(w -> points.stream().filter(p -> p.getWord().equals(w)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .toList();

        for (int i = 0; i < pathPoints.size() - 1; i++) {
            ProjectedPoint from = pathPoints.get(i);
            ProjectedPoint to   = pathPoints.get(i + 1);
            double x1 = toCanvasX(from.getX()), y1 = toCanvasY(from.getY());
            double x2 = toCanvasX(to.getX()),   y2 = toCanvasY(to.getY());
            gc.strokeLine(x1, y1, x2, y2);
            double angle = Math.atan2(y2 - y1, x2 - x1);
            double arrowSize = 10;
            gc.strokeLine(x2, y2,
                x2 - arrowSize * Math.cos(angle - 0.4),
                y2 - arrowSize * Math.sin(angle - 0.4));
            gc.strokeLine(x2, y2,
                x2 - arrowSize * Math.cos(angle + 0.4),
                y2 - arrowSize * Math.sin(angle + 0.4));
        }
        gc.setLineDashes(0);

        gc.setFill(pathColor);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.5);
        gc.setLineDashes(0);
        String[] labels = {"A", "B", "C", "Result"};
        for (int i = 0; i < pathPoints.size(); i++) {
            ProjectedPoint p = pathPoints.get(i);
            double cx = toCanvasX(p.getX()), cy = toCanvasY(p.getY());
            double r = i == pathPoints.size() - 1 ? POINT_RADIUS * 1.8 : POINT_RADIUS * 1.4;
            gc.fillOval(cx - r, cy - r, r * 2, r * 2);
            gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
            String tag = i < labels.length ? labels[i] : "";
            String text = p.getWord() + " (" + tag + ")";
            double tx = cx + r + 3, ty = cy + 4;
            gc.setFill(Color.rgb(255, 255, 255, 0.85));
            gc.fillRoundRect(tx - 2, ty - 11, text.length() * 6.5 + 4, 14, 3, 3);
            gc.setFill(pathColor);
            gc.fillText(text, tx, ty);
        }
    }

    public java.util.Optional<ProjectedPoint> findPoint(String word) {
        return points.stream().filter(p -> p.getWord().equals(word)).findFirst();
    }


    private void attachPanHandler() {
        canvas.setOnMousePressed(e -> {
            if (e.isSecondaryButtonDown()) {
                dragStartX = e.getX() - offsetX;
                dragStartY = e.getY() - offsetY;
            }
        });
        canvas.setOnMouseDragged(e -> {
            if (e.isSecondaryButtonDown()) {
                offsetX = e.getX() - dragStartX;
                offsetY = e.getY() - dragStartY;
                redraw();
            }
        });
    }

    private void attachZoomHandler() {
        canvas.setOnScroll(e -> {
            double factor = e.getDeltaY() > 0 ? 1.15 : 0.87;
            offsetX = e.getX() - (e.getX() - offsetX) * factor;
            offsetY = e.getY() - (e.getY() - offsetY) * factor;
            scale  *= factor;
            redraw();
        });
    }

    private void attachClickHandler() {
        canvas.setOnMouseClicked(e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            if (points.isEmpty()) return;
            String word = findNearestWord(e.getX(), e.getY());
            if (word == null) return;
            onWordSelected.accept(word);
        });
    }

    private void attachHoverHandler() {
        canvas.setOnMouseMoved(e -> {
            if (points.isEmpty()) { hoverLabel.setVisible(false); return; }
            String word = findNearestWordWithin(e.getX(), e.getY(), 400);
            if (word != null) {
                hoverLabel.setText(word);
                hoverLabel.setLayoutX(e.getX() + 12);
                hoverLabel.setLayoutY(Math.max(0, e.getY() - 26));
                hoverLabel.setVisible(true);
            } else {
                hoverLabel.setVisible(false);
            }
        });
        canvas.setOnMouseExited(e -> hoverLabel.setVisible(false));
    }

    private String findNearestWord(double mx, double my) {
        return findNearestWordWithin(mx, my, 900);
    }

    private String findNearestWordWithin(double mx, double my, double maxDistSq) {
        double bestDist = maxDistSq;
        String bestWord = null;
        for (ProjectedPoint p : points) {
            double dx = toCanvasX(p.getX()) - mx;
            double dy = toCanvasY(p.getY()) - my;
            double d  = dx * dx + dy * dy;
            if (d < bestDist) { bestDist = d; bestWord = p.getWord(); }
        }
        return bestWord;
    }

    public Pane getRoot() { return outerRoot; }
}
