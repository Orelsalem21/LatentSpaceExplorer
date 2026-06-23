package loader;

import exception.EmbeddingLoadException;

import java.nio.file.Path;

/**
 * Creates embedding repositories according to file type.
 */
public class EmbeddingRepositoryFactory {

    /**
     * Returns a repository capable of loading the given file.
     */
    public static EmbeddingRepository forFile(Path path) throws EmbeddingLoadException {
        if (path.getFileName().toString().toLowerCase().endsWith(".json"))
            return new JsonEmbeddingRepository();

        throw new EmbeddingLoadException(
                "Unsupported file format: " + path.getFileName(),
                null
        );
    }
}