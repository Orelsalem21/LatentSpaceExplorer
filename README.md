# LatentSpace Explorer

Interactive JavaFX application for visual exploration of word embeddings and semantic vector spaces.

## Features

- 2D and 3D visualization
- PCA-based projections
- Custom Axis Projection (semantic axis defined by two words)
- Cosine and Euclidean distance metrics
- K-nearest neighbors search
- Vector arithmetic (A − B + C)
- Semantic distance matrix
- Centroid calculation
- Undo / Redo support
- Enter key support on all text fields
- Consistent error handling via Alert dialogs

## Requirements

- Java 21 (JDK)
- Python 3.8+

Required Python packages:

```bash
pip install gensim numpy scikit-learn
```

The project uses the **Maven Wrapper**, so no separate Maven installation is required.

## Run

### Windows

```bash
mvnw.cmd javafx:run
```

### macOS / Linux

```bash
chmod +x mvnw
./mvnw javafx:run
```

On first launch, if `python/pca_vectors.json` does not exist yet, the application automatically runs `python/embedder.py` (downloading the GloVe model and computing PCA) before opening the main window. This may take a few minutes.

Every later launch finds the generated `full_vectors.json` and `pca_vectors.json` already on disk and loads instantly.

To generate the embeddings yourself ahead of time instead of waiting on first launch:

```bash
cd python
python embedder.py
```

## Architecture

The system is organized into several layers:

- **app** – application startup, configuration and global state
- **model** – semantic data structures
- **loader** – loading JSON vector files
- **metric** – distance metrics (Cosine / Euclidean)
- **projection** – PCA and custom projections
- **service** – semantic operations and calculations
- **command** – Undo / Redo implementation
- **controller** – application logic
- **view** – JavaFX user interface
- **exception** – custom domain exceptions (WordNotFoundException, InvalidExpressionException, EmbeddingLoadException)
- **utils** – shared UI helpers (AlertHelper, ButtonStyler, RangeNormalizer)

## Design Patterns

- MVC (Model–View–Controller)
- Strategy Pattern
- Command Pattern

## Design Notes

Distance metrics (CosineDistance, EuclideanDistance) and projections (PCAProjection, CustomAxisProjection, ThreeDimensionalProjection) both use the Strategy pattern so they can be swapped at runtime without touching the consumers. Adding a new metric or projection means one new class—nothing else changes.

Each user action is wrapped in a Command with `execute()` / `undo()`, and `CommandHistory` maintains the stack. This keeps Undo/Redo out of the controllers entirely.

Services (NearestNeighborService, VectorArithmeticService, etc.) contain no JavaFX code and can be tested without launching the application. Controllers wire services to views and nothing else.

The semantic model (EmbeddingSpace, WordVector) is dimension-agnostic, so 3D was added purely as a new ProjectionStrategy and a new SubScene view—the model layer was not touched.

All domain errors are represented as typed exceptions in the `exception` package. Every error surface in the UI goes through `AlertHelper`, ensuring a single, consistent presentation layer for user-facing messages. Shared UI utilities (`ButtonStyler`, `RangeNormalizer`) are extracted into the `utils` package, eliminating code duplication across views and controllers.

## Technologies

- Java 21
- JavaFX
- Jackson (jackson-databind)
- Maven Wrapper
- Python
- Gensim
- NumPy
- Scikit-learn
- GloVe Embeddings