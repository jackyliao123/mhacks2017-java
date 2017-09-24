package tk.jimgao;

public class Component {
    public String type;
    public int x;
    public int y;
    public int[] cx = new int[2];
    public int[] cy = new int[2];
    public int width;
    public int height;
    public String param;

    public Component(String a, int b, int c, int d, int e){
        type = a;
        x = b;
        y = c;
        width = d;
        height = e;
        cx[0] = x;
        cx[1] = x + width;
        cy[0] = y;
        cy[1] = y + height;
    }

    public double distanceTo(int xx, int yy){
        return Math.sqrt(Math.pow(x + width / 2 - xx, 2) + Math.pow(y + height / 2 - yy, 2));
    }
}
