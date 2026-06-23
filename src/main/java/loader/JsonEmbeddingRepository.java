package loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exception.EmbeddingLoadException;
import model.EmbeddingSpace;
import model.WordVector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads word embeddings from JSON format.
 */
public class JsonEmbeddingRepository implements EmbeddingRepository {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Loads word vectors from a JSON file and validates their dimensions.
     */
    @Override
    public EmbeddingSpace load(Path file) throws EmbeddingLoadException {
        try {
            JsonNode root = mapper.readTree(file.toFile());
            List<WordVector> vectors = new ArrayList<>();

            int index = 0;
            for (JsonNode entry : root) {
                JsonNode wordNode = entry.get("word");
                JsonNode vecNode  = entry.get("vector");

                if (wordNode == null)
                    throw new EmbeddingLoadException("Entry #" + index + " is missing 'word' field in " + file.getFileName(), null);

                if (vecNode == null || !vecNode.isArray())
                    throw new EmbeddingLoadException("Entry #" + index + " (\"" + wordNode.asText() + "\") is missing 'vector' field in " + file.getFileName(), null);

                if (vecNode.isEmpty())
                    throw new EmbeddingLoadException("Entry #" + index + " (\"" + wordNode.asText() + "\") has an empty vector in " + file.getFileName(), null);

                double[] vector = new double[vecNode.size()];
                for (int i = 0; i < vecNode.size(); i++) {
                    JsonNode num = vecNode.get(i);

                    if (!num.isNumber())
                        throw new EmbeddingLoadException(
                                "Non-numeric value at position " + i + " for word \""
                                        + wordNode.asText() + "\" in " + file.getFileName(),
                                null
                        );

                    vector[i] = num.asDouble();
                }

                vectors.add(new WordVector(wordNode.asText(), vector));
                index++;
            }

            if (vectors.isEmpty())
                throw new EmbeddingLoadException("File " + file.getFileName() + " contains no entries", null);

            int expectedDim = vectors.getFirst().getVector().length;

            for (int i = 1; i < vectors.size(); i++) {
                int actualDim = vectors.get(i).getVector().length;

                if (actualDim != expectedDim)
                    throw new EmbeddingLoadException(
                            "Dimension mismatch at entry " + i + " (\"" + vectors.get(i).getWord()
                                    + "\"): expected " + expectedDim + ", got " + actualDim,
                            null
                    );
            }

            return new EmbeddingSpace(vectors);

        } catch (IOException e) {
            throw new EmbeddingLoadException("Cannot read file " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }
}