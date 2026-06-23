package view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** Root layout of the application: assembles the left panel, 2D/3D clouds, right panel, and status bar. */
public class MainView {

    private final BorderPane root;
    private final HBox       toolbar;
    private final WordCloud2DView cloud2D;
    private final WordCloud3DView cloud3D;

    public MainView(ControlPanelView left,
                    WordCloud2DView  cloud2D,
                    WordCloud3DView  cloud3D,
                    DetailsPanelView right) {
        this.cloud2D = cloud2D;
        this.cloud3D = cloud3D;

        toolbar = new HBox(8);
        toolbar.setStyle("-fx-background-color: #e9ecef; -fx-padding: 6 12 6 12; -fx-min-height: 40px; -fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

        root = new BorderPane();
        root.setStyle("-fx-background-color: #f8f9fa; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px;");
        root.setTop(toolbar);
        root.setLeft(left.getRoot());
        root.setCenter(cloud2D.getRoot());
        root.setRight(right.getRoot());
    }

    public void addToToolbar(Node... nodes) {
        toolbar.getChildren().addAll(nodes);
    }

    public void addToToolbarRight(Node... nodes) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolbar.getChildren().add(spacer);
        toolbar.getChildren().addAll(nodes);
        toolbar.setAlignment(Pos.CENTER_LEFT);
    }

    public void show2D() { root.setCenter(cloud2D.getRoot()); }
    public void show3D() { root.setCenter(cloud3D.getRoot()); }

    public BorderPane getRoot() { return root; }
}
