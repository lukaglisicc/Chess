package Engine;

public class Int2 {
    public int x;
    public int y;
    public Int2(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Int2(Int2 int2) {
        this.x = int2.x;
        this.y = int2.y;
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

    public void set(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
