package controller;

import utils.AlertHelper;
import utils.ErrorMessages;
import app.AppState;
import exception.InvalidExpressionException;
import service.VectorArithmeticService;
import view.DetailsPanelView;
import view.WordCloud2DView;
import view.WordCloud3DView;

import java.util.List;

public class VectorArithmeticController {

    private final AppState                appState;
    private final VectorArithmeticService arithmeticService;

    private final DetailsPanelView rightPanel;
    private final WordCloud2DView  cloud2D;
    private final WordCloud3DView  cloud3D;

    public VectorArithmeticController(
            AppState appState,
            VectorArithmeticService arithmeticService,
            DetailsPanelView rightPanel,
            WordCloud2DView cloud2D,
            WordCloud3DView cloud3D
    ) {
        this.appState          = appState;
        this.arithmeticService = arithmeticService;
        this.rightPanel        = rightPanel;
        this.cloud2D           = cloud2D;
        this.cloud3D           = cloud3D;
    }


    public void clearResult() {
        rightPanel.setArithmeticResult("—");
        clearPath();
    }

    public void onCompute(String expr) {
        if (!appState.isLoaded()) {
            rightPanel.setArithmeticResult("—");
            return;
        }

        try {
            arithmeticService.computeFromExpression(expr, appState.getFullSpace())
                    .ifPresentOrElse(
                            result -> showResult(expr, result),
                            () -> {
                                AlertHelper.showWarning(ErrorMessages.noArithmeticResult());
                                rightPanel.setArithmeticResult("—");
                                clearPath();
                            }
                    );
        } catch (InvalidExpressionException e) {
            AlertHelper.showError(e.getMessage());
            clearPath();
        }
    }


    private void showResult(String expr, String result) {
        rightPanel.setArithmeticResult("→ " + result);

        String[] parts = expr.split(",");
        List<String> path = List.of(
                parts[0].trim().toLowerCase(),
                parts[1].trim().toLowerCase(),
                parts[2].trim().toLowerCase(),
                result
        );

        cloud2D.setArithPath(path);
        cloud3D.setArithPath(path);
    }

    private void clearPath() {
        cloud2D.clearArithPath();
        cloud3D.clearArithPath();
    }
}
