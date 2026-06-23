package command;

/**
 * Generic command implementation backed by execute and undo actions.
 * Useful for creating simple reversible commands without defining
 * dedicated command logic.
 */
public class ReversibleCommand implements Command {

    private final Runnable executeAction;
    private final Runnable undoAction;

    public ReversibleCommand(Runnable executeAction, Runnable undoAction) {
        this.executeAction = executeAction;
        this.undoAction = undoAction;
    }

    @Override
    public void execute() {
        executeAction.run();
    }

    @Override
    public void undo() {
        undoAction.run();
    }
}