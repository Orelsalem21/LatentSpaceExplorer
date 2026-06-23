package model;

/**
 * Represents a projected word position in 2D or 3D space.
 */
public class ProjectedPoint {

    private final String word;
    private final double x;
    private final double y;
    private final double z;

    public ProjectedPoint(String word, double x, double y, double z) {
        this.word = word;
        this.x    = x;
        this.y    = y;
        this.z    = z;
    }

    public ProjectedPoint(String word, double x, double y) {
        this(word, x, y, 0.0);
    }

    public String getWord() { return word; }
    public double getX()    { return x; }
    public double getY()    { return y; }
    public double getZ()    { return z; }
}