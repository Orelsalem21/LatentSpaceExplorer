package utils;

public final class ErrorMessages {

    private ErrorMessages() {
        // utility class
    }

    public static String invalidK(int maxK) {
        return "Please enter a number between 0 and " + maxK + ".";
    }

    public static String invalidWholeNumber() {
        return "Please enter a valid whole number.";
    }

    public static String fillBothFromAndToFields() {
        return "Please fill both From and To fields.";
    }

    public static String fillFromField() {
        return "Please fill the From field.";
    }

    public static String fillToField() {
        return "Please fill the To field.";
    }

    public static String wordNotFound(String word) {
        return "Word not found in vocabulary: \"" + word + "\"";
    }

    public static String needAtLeastTwoWords() {
        return "Please enter at least 2 words to calculate distance.";
    }

    public static String noArithmeticResult() {
        return "No result found for this expression.";
    }

    public static String sessionLoadFailed(String detail) {
        return "Could not load session: " + detail;
    }

    public static String sessionSaveFailed(String detail) {
        return "Save failed: " + detail;
    }

    public static String unknownMetric(String name) {
        return "Unknown metric: " + name;
    }

    public static String sessionDataMismatch() {
        return "This session was saved against different embedding data. "
                + "Selected words, axes, or neighbors may not match the data currently loaded.";
    }
}
