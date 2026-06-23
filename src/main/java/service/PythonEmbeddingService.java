package service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Runs the Python embedding generator as an external process.
 */
public class PythonEmbeddingService {

    public void run(Path scriptPath, Path outputDir) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("python", scriptPath.toString())
                .directory(outputDir.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode  = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(output.lines().reduce((a, b) -> b).orElse(output));
        }
    }
}
