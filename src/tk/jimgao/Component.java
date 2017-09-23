package tk.jimgao;

import java.util.HashMap;

public class Component {
    public String type;
    public int x;
    public int y;
    public int width;
    public int height;
    public String param;

    public Component(String a, int b, int c, int d, int e){
        type = a;
        x = b;
        y = c;
        width = d;
        height = e;
    }

    public double distanceTo(int xx, int yy){
        return Math.sqrt(Math.pow(x + width / 2 - xx, 2) + Math.pow(y + height / 2, 2));
    }
}
