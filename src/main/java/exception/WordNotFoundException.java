package exception;

public class WordNotFoundException extends Exception {
    public WordNotFoundException(String word) {
        super("Word not found in vocabulary: \"" + word + "\"");
    }
}
