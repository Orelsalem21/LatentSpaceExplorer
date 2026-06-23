package command;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Maintains command execution history and provides
 * undo/redo functionality.
 */
public class CommandHistory {

    private final Deque<Command> history = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();

    private Runnable onChanged = () -> {};

    public void setOnChanged(Runnable r) {
        this.onChanged = r;
    }

    public void execute(Command command) {
        command.execute();
        history.push(command);
        redoStack.clear();
        onChanged.run();
    }

    public void undo() {
        if (history.isEmpty()) {
            return;
        }

        Command command = history.pop();
        command.undo();

        redoStack.push(command);
        onChanged.run();
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }

        Command command = redoStack.pop();
        command.execute();

        history.push(command);
        onChanged.run();
    }

    /**
     * Removes all commands from both history stacks.
     */
    public void clear() {
        history.clear();
        redoStack.clear();
        onChanged.run();
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
}