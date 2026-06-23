package command;

import java.util.function.Consumer;

/**
 * Command that executes a vector arithmetic expression
 * and supports undo by clearing the computed result.
 */
public class VectorArithmeticCommand implements Command {

    private final String expr;
    private final Consumer<String> onCompute;
    private final Runnable onClear;

    public VectorArithmeticCommand(
            String expr,
            Consumer<String> onCompute,
            Runnable onClear
    ) {
        this.expr = expr;
        this.onCompute = onCompute;
        this.onClear = onClear;
    }

    @Override
    public void execute() {
        onCompute.accept(expr);
    }

    @Override
    public void undo() {
        onClear.run();
    }
}