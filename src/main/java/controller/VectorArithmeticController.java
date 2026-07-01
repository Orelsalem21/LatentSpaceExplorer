package controller;

import utils.AlertHelper;
import utils.ErrorMessages;
import app.AppState;
import exception.InvalidExpressionException;
import service.VectorArithmeticService;
import view.DetailsPanelView;
import view.WordCloud2DView;
import view.WordCloud3DView;

import java.util.ArrayList;
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
            List<String> words = arithmeticService.parseWords(expr);
            arithmeticService.computeFromExpression(expr, appState.getFullSpace())
                    .ifPresentOrElse(
                            result -> showResult(words, result),
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

    private void showResult(List<String> words, String result) {
        rightPanel.setArithmeticResult("→ " + result);

        List<String> path = new ArrayList<>(words);
        path.add(result);

        cloud2D.setArithPath(path);
        cloud3D.setArithPath(path);
    }

    private void clearPath() {
        cloud2D.clearArithPath();
        cloud3D.clearArithPath();
    }
}
