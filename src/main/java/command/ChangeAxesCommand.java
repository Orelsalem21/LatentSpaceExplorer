package command;

import java.util.function.Consumer;

/**
 * Command that changes the currently displayed projection axes.
 * Supports undo by restoring the previously selected axes.
 */
public class ChangeAxesCommand implements Command {

    private final int[] newAxes;
    private final int[] prevAxes;
    private final Consumer<int[]> apply;

    public ChangeAxesCommand(int[] newAxes, int[] prevAxes, Consumer<int[]> apply) {
        this.newAxes = newAxes;
        this.prevAxes = prevAxes;
        this.apply = apply;
    }

    @Override
    public void execute() {
        apply.accept(newAxes);
    }

    @Override
    public void undo() {
        apply.accept(prevAxes);
    }
}