package tk.jimgao;

import circuitsim.Circuit;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class Main {

    public static final String KEY = "cf25f24484994950954c017c7bb4be15";
    public static HttpClient httpclient;

    public static String submitImage(BufferedImage i) throws Exception {
        URIBuilder builder = new URIBuilder("https://westus.api.cognitive.microsoft.com/vision/v1.0/recognizeText");

        builder.setParameter("handwriting", "true");

        URI uri = builder.build();
        HttpPost request = new HttpPost(uri);
        request.setHeader("Content-Type", "application/octet-stream");
        request.setHeader("Ocp-Apim-Subscription-Key", KEY);

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        ImageIO.write(i, "png", bao);
        bao.flush();

        ByteArrayEntity image = new ByteArrayEntity(bao.toByteArray());
        request.setEntity(image);

        HttpResponse response = httpclient.execute(request);
        HttpEntity entity = response.getEntity();

        return response.getHeaders("Operation-Location")[0].getValue();
    }

    public static JSONObject fetchResult(String resultLocation) throws Exception {
        HttpGet request = new HttpGet(resultLocation);
        //request.setHeader("Content-Type", "application/json");
        request.setHeader("Ocp-Apim-Subscription-Key", KEY);

        HttpResponse response = httpclient.execute(request);
        String responseText = EntityUtils.toString(response.getEntity());

        return new JSONObject(responseText);
    }

    public static void drawBoundingBox(BufferedImage original, JSONObject analysis) throws Exception {
        Graphics2D graphics = original.createGraphics();
        graphics.setColor(Color.RED);
        graphics.setFont(new Font("Arial", Font.BOLD, 22));

        JSONObject recResults = analysis.getJSONObject("recognitionResult");
        JSONArray lines = recResults.getJSONArray("lines");

        for (int i = 0; i < lines.length(); i++) {
            JSONArray words = lines.getJSONObject(i).getJSONArray("words");

            for (int j = 0; j < words.length(); j++) {
                JSONArray boundingBox = words.getJSONObject(j).getJSONArray("boundingBox");
                int sx = 0, sy = 0;

                for (int k = 0; k < 4; k++) {
                    sx += boundingBox.getInt(k * 2);
                    sy += boundingBox.getInt(k * 2 + 1);

                    int curX = boundingBox.getInt(k * 2);
                    int curY = boundingBox.getInt(k * 2 + 1);
                    int nextX = boundingBox.getInt(((k + 1) % 4) * 2);
                    int nextY = boundingBox.getInt(((k + 1) % 4) * 2 + 1);

                    graphics.drawLine(curX, curY, nextX, nextY);
                }

                sx /= 4;
                sy /= 4;

                graphics.drawString(words.getJSONObject(j).getString("text"), sx, sy);
            }
        }

        graphics.dispose();
    }

    static final double MAX_DIST_CORRELATE = 300;

    public static HashMap<Character, Double> siSuffix = new HashMap<Character, Double>();

    static {
        siSuffix.put('p', 1e-12);
        siSuffix.put('n', 1e-9);
        siSuffix.put('u', 1e-6);
        siSuffix.put('m', 1e-3);
        siSuffix.put('k', 1e+3);
        siSuffix.put('M', 1e+6);
        siSuffix.put('G', 1e+9);
        siSuffix.put('T', 1e+12);
    }

    public static char flipCase(char c) {
        if ('a' <= c && c <= 'z') {
            c = (char) (c - 'a' + 'A');
        } else if ('A' <= c && c <= 'Z') {
            c = (char) (c - 'A' + 'a');
        }
        return c;
    }

    public static double parseStringFuzzy(String s) {
        char[] table = new char[256];
        table['b'] = '6';
        table['f'] = '1';
        table['g'] = '9';
        table['h'] = '6';
        table['i'] = '1';
        table['j'] = '1';
        table['k'] = '6';
        table['l'] = '1';
        table['m'] = table['n'] = table['o'] = '0';
        table['q'] = '9';
        table['s'] = '5';
        table['u'] = '0';
        table['y'] = '9';

        table['B'] = '8';
        table['C'] = table['D'] = '0';
        table['E'] = '8';
        table['G'] = '6';
        table['H'] = '8';
        table['I'] = table['J'] = table['L'] = '1';
        table['O'] = table['Q'] = '0';
        table['S'] = '5';
        table['T'] = '1';
        table['U'] = '0';

        String converted = "";

        for(char c : s.toCharArray()) {
            if(table[c] != 0) {
                converted += table[c];
            } else {
                converted += c;
            }
        }

        try {
            return Double.parseDouble(converted);
        } catch (NumberFormatException e) {
            return 1;
        }
    }



    public static double parseSI(String s, double def) {
        if (s == null)
            return def;
        char lastChar = s.charAt(s.length() - 1);
        String str = s.substring(0, s.length() - 1);
        if (siSuffix.containsKey(lastChar)) {
            return parseStringFuzzy(str) * siSuffix.get(lastChar);
        } else if (siSuffix.containsKey(flipCase(lastChar))) {
            return parseStringFuzzy(str) * siSuffix.get(flipCase(lastChar));
        }
        System.err.println("Parsing number failed: " + s);
        return def;
    }

    public static void main(String[] args) throws Exception {



        httpclient = HttpClients.createDefault();

        ProcessBuilder builder = new ProcessBuilder("Mhacks x.exe"); //0 params-default vid, 1=webcam, 2=bmp
        Process process = builder.start();
        final InputStream err = process.getErrorStream();

        new Thread() {
            public void run() {
                int read;
                byte[] b = new byte[4096];
                try {
                    while ((read = err.read(b, 0, 4096)) != -1) {
                        System.err.print(new String(b, 0, read));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
//
        Scanner reader = new Scanner(new InputStreamReader(process.getInputStream()));
//        Scanner reader = new Scanner(System.in);
        ArrayList<Component> comp = new ArrayList<>();
        ArrayList<Wire> wires = new ArrayList<>();

        Circuit.main(new String[0]);
        JSONObject result = null;
        long lastMilli = 0;
        while (true) {
            int status = reader.nextInt();
            if (status == 0) continue;
            comp.clear();
            wires.clear();

//            System.out.println("Scanner read 1 int");

            int N = reader.nextInt();
            int M = reader.nextInt();

//            System.out.println("Read N and M, " + N + ", " + M);

            for (int i = 0; i < N; i++) {
                String type = reader.next();
                int x = reader.nextInt();
                int y = reader.nextInt();
                int width = reader.nextInt();
                int height = reader.nextInt();

                comp.add(new Component(type, x, y, width, height));
            }

//            System.out.println("Read components");

            for (int i = 0; i < M; i++) {
                int x1 = reader.nextInt();
                int y1 = reader.nextInt();
                int x2 = reader.nextInt();
                int y2 = reader.nextInt();

//                System.out.println("Read 1 wire");
                wires.add(new Wire(x1, y1, x2, y2));
//                System.out.println("Added 1 wire");
            }

//            System.out.println("Read wires");

            if (!Circuit.ogf.liveCheck.getState())
                continue;

            System.out.println("a");
            long currTime = System.currentTimeMillis();
            if (currTime - lastMilli > 100) {
                lastMilli = currTime;
                BufferedImage im = null;
                Circuit.ogf.repaint();
                Thread.sleep(50);
                Circuit.ogf.repaint();
                int cnt = 0;
                try {
                    while (cnt < 3 && (im = ImageIO.read(new File("text.png"))) == null) cnt++;

                } catch (Exception e) {
                }
                if (im != null) {
                    System.out.println("b");
                    String loc = submitImage(im);

                    long tStart = System.currentTimeMillis();
                    while (System.currentTimeMillis() - tStart < 1000) {
                        System.out.println("d");
                        result = fetchResult(loc);
                        Circuit.ogf.repaint();
                        Thread.sleep(50);
                        Circuit.ogf.repaint();
                        System.out.println(result);
                        Circuit.ogf.repaint();
                        Thread.sleep(50);
                        Circuit.ogf.repaint();
                        if (!result.isNull("status") && result.getString("status").equals("Succeeded")) {
                            break;
                        }
                        Circuit.ogf.repaint();
                        Thread.sleep(50);
                        Circuit.ogf.repaint();

                    }
                }
            }
            System.out.println("c");

            if (result != null && (!result.isNull("status") && result.getString("status").equals("Succeeded"))) {
                JSONObject recResults = result.getJSONObject("recognitionResult");
                JSONArray lines = recResults.getJSONArray("lines");
                for (int i = 0; i < lines.length(); i++) {
                    JSONArray boundingBox = lines.getJSONObject(i).getJSONArray("boundingBox");
                    String lbl = lines.getJSONObject(i).getString("text").replaceAll(" ", "").replaceAll("\t", "");
                    int txtX = boundingBox.getInt(0);
                    int txtY = boundingBox.getInt(1);
                    int txtW = boundingBox.getInt(4) - txtX;
                    int txtH = boundingBox.getInt(5) - txtY;

                    if (lbl.toLowerCase().contains("v")) {
                        Component c = new Component("source", txtX, txtY, txtW, txtH);
                        c.param = lbl;
                        comp.add(c);
                    } else if (lbl.toLowerCase().equals("gnd")) {
                        Component c = new Component("ground", txtX, txtY, txtW, txtH);
                        comp.add(c);
                    }

                    int sx = 0, sy = 0;
                    for (int k = 0; k < 4; k++) {
                        sx += boundingBox.getInt(k * 2);
                        sy += boundingBox.getInt(k * 2 + 1);
                    }
                    sx /= 4;
                    sy /= 4;

                    int compID = -1;
                    double best = Double.POSITIVE_INFINITY;
                    for (int k = 0; k < comp.size(); k++) {
                        if (comp.get(k).type.equals("junction")) continue;

                        double alt = comp.get(k).distanceTo(sx, sy);
                        if (alt < best) {
                            compID = k;
                            best = alt;
                        }
                    }

                    if (best < MAX_DIST_CORRELATE) {
                        System.out.println("Label: " + lbl);
                        comp.get(compID).param = lbl;
                        System.out.printf("Updating from %s to ", comp.get(compID).type);
                        if (lbl.equals("LED")) {
                            comp.get(compID).type = "LED";
                        } else if (lbl.toLowerCase().equals("gnd")) {
                            comp.get(compID).type = "ground";
                        } else if (lbl.toLowerCase().contains("h")) {
                            comp.get(compID).type = "inductor";
                        } else if (lbl.toLowerCase().contains("r") || lbl.toLowerCase().contains("k")) {
                            comp.get(compID).type = "resistor";
                        } else if (lbl.toLowerCase().contains("q")) {
                            comp.get(compID).type = "transistor";
                        } else if (lbl.toLowerCase().contains("d")) {
                            comp.get(compID).type = "diode";
                        } else if (lbl.toLowerCase().contains("v")) {
                            comp.get(compID).type = "source";
                        } else if (lbl.toLowerCase().contains("f")) {
                            comp.get(compID).type = "capacitor";
                        }
                        System.out.printf("%s\n", comp.get(compID).type);
                    }
                }
            }

            ArrayList<Integer> x = new ArrayList<>();
            ArrayList<Integer> y = new ArrayList<>();
            ArrayList<String> connName = new ArrayList<>();
            ArrayList<Component> connSrc = new ArrayList<>();

            for (Component c : comp) {
                switch (c.type) {
                    case "capacitor":
                    case "inductor":
                    case "resistor":
                    case "diode":
                    case "LED":
                        connSrc.add(c);
                        x.add(c.cx[0]);
                        y.add(c.cy[0]);
                        connSrc.add(c);
                        x.add(c.cx[1]);
                        y.add(c.cy[1]);
                        connName.add("point1");
                        connName.add("point2");
                        break;
                    case "transistor":
                        connName.add("base");
                        connSrc.add(c);
                        x.add(c.x);
                        y.add(c.y + c.height / 2);

                        connName.add("collector");
                        connSrc.add(c);
                        x.add(c.x + c.width);
                        y.add(c.y);

                        connName.add("emitter");
                        connSrc.add(c);
                        x.add(c.x + c.width);
                        y.add(c.y + c.height);
                        break;
                    case "junction":
                        connName.add("point1");
                        connSrc.add(c);
                        x.add(c.x);
                        y.add(c.y);
                        break;
                    case "ground":
                        connName.add("point1");
                        connSrc.add(c);
                        x.add(c.x);
                        y.add(c.y);
                        break;
                    case "source":
                        connName.add("point1");
                        connSrc.add(c);
                        x.add(c.cx[0]);
                        y.add(c.cy[0]);
                        break;
                }
            }

            System.out.println("Finish reading");

            for (Wire w : wires) {

                int k = connSrc.size();
                double mini = Double.POSITIVE_INFINITY;
                int best = -1;
                for (int i = 0; i < k; i++) {
                    double dist = Math.sqrt(Math.pow(w.x1 - x.get(i), 2) + Math.pow(w.y1 - y.get(i), 2));
                    if (dist < mini) {
                        best = i;
                        mini = dist;
                    }
                }

                if (mini > MAX_DIST_CORRELATE) continue;
                w.c1 = connSrc.get(best);
                w.x1 = x.get(best);
                w.y1 = y.get(best);
                w.c1Name = connName.get(best);

                k = connSrc.size();
                mini = Double.POSITIVE_INFINITY;
                best = -1;
                for (int i = 0; i < k; i++) {
                    double dist = Math.sqrt(Math.pow(w.x2 - x.get(i), 2) + Math.pow(w.y2 - y.get(i), 2));
                    if (dist < mini) {
                        best = i;
                        mini = dist;
                    }
                }

                if (mini > MAX_DIST_CORRELATE) continue;
                w.c2 = connSrc.get(best);
                w.x2 = x.get(best);
                w.y2 = y.get(best);
                w.c2Name = connName.get(best);
            }

            System.out.println("Finished parsing");

            Circuit.ogf.parseComponents(comp, wires);

            System.out.println("Finished updating");
        }
    }
}
