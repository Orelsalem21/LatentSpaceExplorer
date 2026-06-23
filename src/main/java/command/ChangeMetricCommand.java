package command;

import java.util.function.Consumer;

/**
 * Command that changes the active distance metric.
 * Supports undo by restoring the previously selected metric.
 */
public class ChangeMetricCommand implements Command {

    private final String newMetric;
    private final String prevMetric;
    private final Consumer<String> apply;

    public ChangeMetricCommand(String newMetric, String prevMetric, Consumer<String> apply) {
        this.newMetric = newMetric;
        this.prevMetric = prevMetric;
        this.apply = apply;
    }

    @Override
    public void execute() {
        apply.accept(newMetric);
    }

    @Override
    public void undo() {
        apply.accept(prevMetric);
    }
}