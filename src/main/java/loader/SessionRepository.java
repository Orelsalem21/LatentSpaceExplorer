package loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import app.SessionState;

import java.io.IOException;
import java.nio.file.Path;

/** Saves and loads session snapshots as JSON files. */
public class SessionRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void save(SessionState state, Path path) throws IOException {
        MAPPER.writeValue(path.toFile(), state);
    }

    public SessionState load(Path path) throws IOException {
        return MAPPER.readValue(path.toFile(), SessionState.class);
    }
}
