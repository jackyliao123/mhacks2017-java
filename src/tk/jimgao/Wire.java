package tk.jimgao;

import java.util.HashMap;

public class Wire {

    public static HashMap<String, Integer> pinLookup = new HashMap<String, Integer>();

    static {
        pinLookup.put("point1", 0);
        pinLookup.put("point2", 1);
    }

    public int x1;
    public int y1;
    public int x2;
    public int y2;
    public Component c1;
    public String c1Name;
    public Component c2;

    public String c2Name;

    public Wire(int a, int b, int c, int d){
        x1 = a;
        y1 = b;
        x2 = c;
        y2 = d;
    }
}
