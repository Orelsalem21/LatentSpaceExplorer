package command;

/**
 * Represents an executable user action that can be reversed.
 * Used by the command history to support undo and redo operations.
 */
public interface Command {

    void execute();

    void undo();
}