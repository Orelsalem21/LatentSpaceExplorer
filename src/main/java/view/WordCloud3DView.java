package view;

import utils.RangeNormalizer;
import model.ProjectedPoint;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;

import java.util.*;
import java.util.function.Consumer;

/** JavaFX 3-D word cloud: renders projected points as spheres in a rotatable SubScene with hover labels and neighbor highlighting. */
public class WordCloud3DView implements WordCloudView {

    private static final double SCENE_SCALE = 300.0;
    private static final double SPHERE_R    = 3.5;
    private static final double AXIS_LEN    = SCENE_SCALE * 1.4;
    private static final double AXIS_THICK  = 1.2;

    private static final PhongMaterial MAT_DOT      = mat("#1971c2");
    private static final PhongMaterial MAT_SELECT   = mat("#c92a2a");
    private static final PhongMaterial MAT_NEIGHBOR = mat("#2f9e44");
    private static final PhongMaterial MAT_DISTANCE = mat("#7048e8");
    private static final PhongMaterial MAT_ARITH    = mat("#e67700");
    private static final PhongMaterial MAT_AXIS_X   = mat("#e67700");
    private static final PhongMaterial MAT_AXIS_Y   = mat("#2f9e44");
    private static final PhongMaterial MAT_AXIS_Z   = mat("#adb5bd");
    private static final PhongMaterial MAT_LINE     = mat("#343a40");

    private final StackPane root;
    private final Group     pointsGroup = new Group();
    private final Group     linesGroup  = new Group();
    private final Group     textsGroup  = new Group();
    private final SubScene  subScene;

    private final Label xLabel = new Label("X: PC1");
    private final Label yLabel = new Label("Y: PC2");
    private final Label zLabel = new Label("Z: PC3");

    private final Rotate rotateX = new Rotate(20, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(-30, Rotate.Y_AXIS);
    private double anchorX, anchorY, pressX, pressY;
    private double cameraZ = -900;

    private List<ProjectedPoint>         currentPoints  = List.of();
    private Set<String>                  selected       = Set.of();
    private Map<String, Double>          neighbors      = Map.of();
    private Set<String>                  distanceWords  = Set.of();
    private List<String>                 arithPath      = List.of();
    private final Map<String, Sphere>    sphereMap      = new HashMap<>();
    private final Map<String, double[]>  posMap         = new HashMap<>();
    private final Map<Sphere, String>    reverseMap     = new HashMap<>();
    private final Group                  arithGroup     = new Group();

    private final Label hoverLabel = new Label();

    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private boolean            showAllWords = false;
    private Consumer<String> onWordSelected = w -> {};

    public WordCloud3DView() {
        Group world = new Group(pointsGroup, linesGroup, arithGroup, textsGroup, buildAxes(), buildLights());
        world.getTransforms().addAll(rotateX, rotateY);

        subScene = new SubScene(world, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.WHITE);

        camera.setNearClip(0.1);
        camera.setFarClip(20000);
        camera.setTranslateZ(cameraZ);
        subScene.setCamera(camera);

        styleLabel(xLabel, "#e67700");
        styleLabel(yLabel, "#2f9e44");
        styleLabel(zLabel, "#212529");

        hoverLabel.setVisible(false);
        hoverLabel.setMouseTransparent(true);
        hoverLabel.setStyle("-fx-background-color:rgba(255,255,255,0.93);" +
            "-fx-border-color:#ced4da;-fx-border-radius:3;-fx-background-radius:3;" +
            "-fx-padding:2 7;-fx-font-size:11px;-fx-text-fill:#212529;");

        CheckBox showAllWordsBox = new CheckBox("Show All Words");
        showAllWordsBox.setStyle("-fx-font-size:11px;-fx-text-fill:#495057;");
        showAllWordsBox.setLayoutX(8);
        showAllWordsBox.setLayoutY(8);
        showAllWordsBox.selectedProperty().addListener((obs, o, val) -> {
            showAllWords = val;
            updateMaterials();
        });

        Pane overlay = new Pane(xLabel, yLabel, zLabel, hoverLabel, showAllWordsBox);
        overlay.setPickOnBounds(false);

        root = new StackPane(subScene, overlay);
        subScene.widthProperty().bind(root.widthProperty());
        subScene.heightProperty().bind(root.heightProperty());
        root.widthProperty().addListener((o, ov, w)  -> positionLabels(w.doubleValue(), root.getHeight()));
        root.heightProperty().addListener((o, ov, h) -> positionLabels(root.getWidth(), h.doubleValue()));

        attachMouseHandlers();
    }


    public void setPoints(List<ProjectedPoint> points) {
        this.currentPoints = points;
        rebuildPoints();
    }

    public void setSelected(Set<String> selected) {
        this.selected = selected;
        updateMaterials();
    }

    public void setNeighbors(Map<String, Double> neighbors) {
        this.neighbors = neighbors;
        updateMaterials();
    }

    public void setOnWordSelected(Consumer<String> h) { this.onWordSelected = h; }


    @Override
    public void centerOnWord(String word) {
        double[] p = posMap.get(word);
        if (p == null) return;

        rotateX.setAngle(20);
        rotateY.setAngle(-30);
        rotateX.setPivotX(p[0]); rotateX.setPivotY(p[1]); rotateX.setPivotZ(p[2]);
        rotateY.setPivotX(p[0]); rotateY.setPivotY(p[1]); rotateY.setPivotZ(p[2]);

        cameraZ = -900;
        camera.setTranslateX(0);
        camera.setTranslateY(0);
        camera.setTranslateZ(cameraZ);
    }

    @Override
    public void resetView() {
        rotateX.setAngle(20);
        rotateY.setAngle(-30);
        rotateX.setPivotX(0); rotateX.setPivotY(0); rotateX.setPivotZ(0);
        rotateY.setPivotX(0); rotateY.setPivotY(0); rotateY.setPivotZ(0);
        cameraZ = -900;
        camera.setTranslateX(0);
        camera.setTranslateY(0);
        camera.setTranslateZ(cameraZ);
    }

    @Override
    public void setAxisLabels(String x, String y, String z) {
        xLabel.setText("X: " + x);
        yLabel.setText("Y: " + y);
        zLabel.setText("Z: " + z);
    }

    public void setDistanceWords(Set<String> words) {
        this.distanceWords = words;
        updateMaterials();
    }

    public void setArithPath(List<String> path) {
        this.arithPath = path;
        updateArithPath();
        updateMaterials();
    }

    public void clearArithPath() {
        this.arithPath = List.of();
        arithGroup.getChildren().clear();
        updateMaterials();
    }


    private void rebuildPoints() {
        pointsGroup.getChildren().clear();
        sphereMap.clear();
        posMap.clear();
        reverseMap.clear();
        if (currentPoints.isEmpty()) return;

        double minX = currentPoints.stream().mapToDouble(ProjectedPoint::getX).min().orElse(-1);
        double maxX = currentPoints.stream().mapToDouble(ProjectedPoint::getX).max().orElse(1);
        double minY = currentPoints.stream().mapToDouble(ProjectedPoint::getY).min().orElse(-1);
        double maxY = currentPoints.stream().mapToDouble(ProjectedPoint::getY).max().orElse(1);
        double minZ = currentPoints.stream().mapToDouble(ProjectedPoint::getZ).min().orElse(-1);
        double maxZ = currentPoints.stream().mapToDouble(ProjectedPoint::getZ).max().orElse(1);

        double rX = maxX-minX==0?1:maxX-minX, midX=(maxX+minX)/2;
        double rY = maxY-minY==0?1:maxY-minY, midY=(maxY+minY)/2;
        double rZ = maxZ-minZ==0?1:maxZ-minZ, midZ=(maxZ+minZ)/2;
        double commonRange = Math.max(rX, Math.max(rY, rZ));

        for (ProjectedPoint p : currentPoints) {
            double sx =  ((p.getX()-midX)/commonRange)*SCENE_SCALE;
            double sy = -((p.getY()-midY)/commonRange)*SCENE_SCALE;
            double sz =  ((p.getZ()-midZ)/commonRange)*SCENE_SCALE;

            Sphere sphere = new Sphere(SPHERE_R);
            sphere.setMaterial(MAT_DOT);
            sphere.setTranslateX(sx); sphere.setTranslateY(sy); sphere.setTranslateZ(sz);

            String word = p.getWord();
            sphereMap.put(word, sphere);
            posMap.put(word, new double[]{sx, sy, sz});
            reverseMap.put(sphere, word);
            pointsGroup.getChildren().add(sphere);
        }

        updateMaterials();
    }

    private void updateMaterials() {
        boolean hasActiveState = !selected.isEmpty() || !neighbors.isEmpty()
                              || !distanceWords.isEmpty() || !arithPath.isEmpty();

        sphereMap.forEach((word, sphere) -> {
            boolean isSel  = selected.contains(word);
            boolean isNb   = neighbors.containsKey(word);
            boolean isDist = distanceWords.contains(word);

            boolean isImportant = isSel || isNb || isDist || arithPath.contains(word);
            boolean shouldShow  = showAllWords || !hasActiveState || isImportant;

            sphere.setVisible(shouldShow);
            sphere.setMouseTransparent(!shouldShow);

            if (isSel) {
                sphere.setMaterial(MAT_SELECT);
                sphere.setRadius(SPHERE_R * 1.7);
                sphere.setOpacity(1.0);
            } else if (isDist) {
                sphere.setMaterial(MAT_DISTANCE);
                sphere.setRadius(SPHERE_R * 1.5);
                sphere.setOpacity(1.0);
            } else if (isNb) {
                sphere.setMaterial(MAT_NEIGHBOR);
                sphere.setRadius(SPHERE_R);
                sphere.setOpacity(1.0);
            } else {
                sphere.setMaterial(MAT_DOT);
                sphere.setRadius(SPHERE_R);
                sphere.setOpacity(1.0);
            }
        });

        updateLabelsAndLines();
    }

    private void updateLabelsAndLines() {
        linesGroup.getChildren().clear();
        textsGroup.getChildren().clear();

        for (String word : selected)           addLabel(word, "#c92a2a");
        for (String word : neighbors.keySet()) addLabel(word, "#2f9e44");
        for (String word : distanceWords)      addLabel(word, "#7048e8");

        RangeNormalizer normalizer = new RangeNormalizer(neighbors);

        for (String s : selected) {
            double[] ps = posMap.get(s);
            if (ps == null) continue;

            for (Map.Entry<String, Double> entry : neighbors.entrySet()) {
                double[] pn = posMap.get(entry.getKey());
                if (pn == null) continue;

                double norm = normalizer.normalize(entry.getValue()); // 0=closest, 1=furthest
                double radius = 2.5 - norm * 2.0;                // closest→2.5, furthest→0.5

                linesGroup.getChildren().add(
                        createCylinderLine(ps[0], ps[1], ps[2], pn[0], pn[1], pn[2], radius)
                );
            }
        }
    }
    private void addLabel(String word, String colorHex) {
        double[] pos = posMap.get(word);
        if (pos == null) return;
        Text text = new Text(word);
        text.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        text.setFill(Color.web(colorHex));
        text.setTranslateX(pos[0] + SPHERE_R * 2);
        text.setTranslateY(pos[1]);
        text.setTranslateZ(pos[2]);
        textsGroup.getChildren().add(text);
    }

    private void updateArithPath() {
        arithGroup.getChildren().clear();
        if (arithPath.size() < 2) return;

        String[] labels = {"A", "B", "C", "Result"};
        List<double[]> pathPos = new ArrayList<>();
        for (String word : arithPath) {
            double[] pos = posMap.get(word);
            if (pos != null) pathPos.add(pos);
        }

        PhongMaterial matArithLine = mat("#e67700");
        for (int i = 0; i < pathPos.size() - 1; i++) {
            double[] from = pathPos.get(i);
            double[] to   = pathPos.get(i + 1);
            Node line = createCylinderLine(from[0], from[1], from[2], to[0], to[1], to[2], 1.5);
            if (line instanceof Cylinder cyl) cyl.setMaterial(matArithLine);
            arithGroup.getChildren().add(line);
        }

        for (int i = 0; i < Math.min(arithPath.size(), pathPos.size()); i++) {
            double[] pos = pathPos.get(i);
            Sphere s = new Sphere(SPHERE_R * 1.6);
            s.setMaterial(MAT_ARITH);
            s.setTranslateX(pos[0]); s.setTranslateY(pos[1]); s.setTranslateZ(pos[2]);
            String tag = i < labels.length ? labels[i] : "";
            Text t = new Text(arithPath.get(i) + " (" + tag + ")");
            t.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            t.setFill(Color.web("#e67700"));
            t.setTranslateX(pos[0] + SPHERE_R * 2.5);
            t.setTranslateY(pos[1]);
            t.setTranslateZ(pos[2]);
            arithGroup.getChildren().addAll(s, t);
        }
    }

    private Node createCylinderLine(double x1, double y1, double z1,
                                    double x2, double y2, double z2,
                                    double radius) {

        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;

        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (length < 0.01) {
            return new Group();
        }

        Cylinder cyl = new Cylinder(radius, length);
        cyl.setMaterial(MAT_LINE);

        cyl.setTranslateX((x1 + x2) / 2.0);
        cyl.setTranslateY((y1 + y2) / 2.0);
        cyl.setTranslateZ((z1 + z2) / 2.0);

        Point3D yAxis = new Point3D(0, 1, 0);
        Point3D target = new Point3D(dx, dy, dz).normalize();

        Point3D axis = yAxis.crossProduct(target);
        double angle = Math.toDegrees(Math.acos(yAxis.dotProduct(target)));

        if (axis.magnitude() > 0.0001) {
            cyl.getTransforms().add(new Rotate(angle, axis));
        } else if (target.getY() < 0) {
            cyl.getTransforms().add(new Rotate(180, Rotate.X_AXIS));
        }

        return cyl;
    }


    private Group buildAxes() {
        Box xAxis = new Box(AXIS_LEN, AXIS_THICK, AXIS_THICK); xAxis.setMaterial(MAT_AXIS_X);
        Box yAxis = new Box(AXIS_THICK, AXIS_LEN, AXIS_THICK); yAxis.setMaterial(MAT_AXIS_Y);
        Box zAxis = new Box(AXIS_THICK, AXIS_THICK, AXIS_LEN); zAxis.setMaterial(MAT_AXIS_Z);
        return new Group(xAxis, yAxis, zAxis);
    }

    private Group buildLights() {
        AmbientLight ambient = new AmbientLight(Color.rgb(160,160,160));
        PointLight   point   = new PointLight(Color.WHITE);
        point.setTranslateX(400); point.setTranslateY(-600); point.setTranslateZ(-800);
        return new Group(ambient, point);
    }


    private void styleLabel(Label l, String color) {
        l.setStyle("-fx-text-fill:" + color + ";-fx-font-weight:bold;-fx-font-size:12px;");
    }

    private void positionLabels(double w, double h) {
        xLabel.setLayoutX(w-70); xLabel.setLayoutY(h/2-20);
        yLabel.setLayoutX(w/2);  yLabel.setLayoutY(12);
        zLabel.setLayoutX(w-70); zLabel.setLayoutY(h-30);
    }


    private void attachMouseHandlers() {
        subScene.setOnMouseMoved(e -> {
            if (e.getTarget() instanceof Sphere s && reverseMap.containsKey(s)) {
                hoverLabel.setText(reverseMap.get(s));
                hoverLabel.setLayoutX(e.getX() + 12);
                hoverLabel.setLayoutY(Math.max(0, e.getY() - 26));
                hoverLabel.setVisible(true);
            } else {
                hoverLabel.setVisible(false);
            }
        });
        subScene.setOnMouseExited(e -> hoverLabel.setVisible(false));

        subScene.setOnMouseClicked(e -> {
            double dx = e.getSceneX() - pressX, dy = e.getSceneY() - pressY;
            if (dx*dx + dy*dy >= 36) return;
            if (!(e.getTarget() instanceof Sphere s)) return;
            String word = reverseMap.get(s);
            if (word == null) return;
            onWordSelected.accept(word);
        });

        subScene.setOnMousePressed(e -> {
            pressX  = e.getSceneX(); pressY  = e.getSceneY();
            anchorX = pressX;        anchorY = pressY;
        });
        subScene.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - anchorX;
            double dy = e.getSceneY() - anchorY;
            if (e.isPrimaryButtonDown()) {
                camera.setTranslateX(camera.getTranslateX() - dx * 0.5);
                camera.setTranslateY(camera.getTranslateY() - dy * 0.5);
            } else if (e.isSecondaryButtonDown()) {
                rotateY.setAngle(rotateY.getAngle() + dx * 0.4);
                rotateX.setAngle(rotateX.getAngle() - dy * 0.4);
            }
            anchorX = e.getSceneX(); anchorY = e.getSceneY();
        });
        subScene.setOnScroll(e -> {
            cameraZ += e.getDeltaY()*3;
            cameraZ = Math.max(-5000, Math.min(-100, cameraZ));
            camera.setTranslateZ(cameraZ);
        });
    }

    private static PhongMaterial mat(String hex) {
        PhongMaterial m = new PhongMaterial(Color.web(hex));
        m.setSpecularColor(Color.WHITE);
        m.setSpecularPower(20);
        return m;
    }

    public Pane getRoot() { return root; }
}
