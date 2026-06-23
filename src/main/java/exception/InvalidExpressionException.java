package exception;

/**
 * Thrown when a vector arithmetic expression is invalid.
 * This may occur when the expression format is incorrect,
 * contains unknown words, or cannot be evaluated safely.
 */
public class InvalidExpressionException extends Exception {

    /**
     * Creates a new invalid-expression exception.
     *
     * @param message description of the expression error
     */
    public InvalidExpressionException(String message) {
        super(message);
    }
}