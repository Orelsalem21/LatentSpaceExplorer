package exception;

/**
 * Thrown when an embedding space cannot be loaded successfully.
 * Wraps the underlying cause while preserving a user-friendly message.
 */
public class EmbeddingLoadException extends Exception {

    /**
     * Creates a new embedding loading exception.
     *
     * @param message user-friendly error description
     * @param cause   underlying loading failure
     */
    public EmbeddingLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}