package Engine;

public class Double2 {
    public double x;
    public double y;
    public Double2(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return x + "," + y;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Int2) {
            return ((Int2) obj).x == this.x && ((Int2) obj).y == this.y;
        }
        return false;
    }

    public void set(double x, double y) {
        this.x = x;
        this.y = y;
    }
}
