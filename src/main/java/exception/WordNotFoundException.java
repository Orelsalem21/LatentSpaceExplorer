package exception;

import utils.ErrorMessages;

public class WordNotFoundException extends Exception {
    public WordNotFoundException(String word) {
        super(ErrorMessages.wordNotFound(word));
    }
}
