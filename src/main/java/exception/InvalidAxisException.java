package exception;

/**
 * Thrown when an invalid projection axis configuration is requested.
 * This may occur when the selected axes are out of bounds, duplicated,
 * or otherwise unsuitable for projection.
 */
public class InvalidAxisException extends Exception {

    /**
     * Creates a new invalid-axis exception.
     *
     * @param message description of the projection error
     */
    public InvalidAxisException(String message) {
        super(message);
    }
}