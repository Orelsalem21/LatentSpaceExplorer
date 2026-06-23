package loader;

import exception.EmbeddingLoadException;
import model.EmbeddingSpace;

import java.nio.file.Path;

/**
 * Repository abstraction for loading embedding spaces from external files.
 */
public interface EmbeddingRepository {

    /**
     * Loads an embedding space from the specified file.
     */
    EmbeddingSpace load(Path file) throws EmbeddingLoadException;
}