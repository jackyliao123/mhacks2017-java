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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
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

    static final double MAX_DIST_CORRELATE = 500;

    public static void main(String[] args) throws Exception {
        httpclient = HttpClients.createDefault();

//        ProcessBuilder builder = new ProcessBuilder("capture.exe");
//        Process process = builder.start();

        Scanner reader = new Scanner(new InputStreamReader(System.in));
        ArrayList<Component> comp = new ArrayList<>();
        ArrayList<Wire> wires = new ArrayList<>();

        int iter = 0;

        Circuit.main(new String[0]);

        while (true) {
            int status = reader.nextInt();
            if (status == 0) continue;
            comp.clear();
            wires.clear();

            int N = reader.nextInt();
            int M = reader.nextInt();

            for (int i = 0; i < N; i++) {
                String type = reader.next();
                int x = reader.nextInt();
                int y = reader.nextInt();
                int width = reader.nextInt();
                int height = reader.nextInt();

                comp.add(new Component(type, x, y, width, height));
            }

            for (int i = 0; i < M; i++) {
                int x1 = reader.nextInt();
                int y1 = reader.nextInt();
                int x2 = reader.nextInt();
                int y2 = reader.nextInt();

                wires.add(new Wire(x1, y1, x2, y2));
            }

            if (iter % 10 == 0) {
                BufferedImage im = ImageIO.read(new File("text.png"));
                String loc = submitImage(im);
                JSONObject result = null;
                while (true) {
                    result = fetchResult(loc);
                    System.out.println(result);
                    if (!result.isNull("status") && result.getString("status").equals("Succeeded")) {
                        break;
                    }
                    Thread.sleep(200);
                }
                iter++;

                JSONObject recResults = result.getJSONObject("recognitionResult");
                JSONArray lines = recResults.getJSONArray("lines");
                for (int i = 0; i < lines.length(); i++) {
                    JSONArray boundingBox = lines.getJSONObject(i).getJSONArray("boundingBox");
                    String lbl = lines.getJSONObject(i).getString("text").replaceAll(" ", "").replaceAll("\t", "");
                    int txtX = boundingBox.getInt(0);
                    int txtY = boundingBox.getInt(1);
                    int txtW = boundingBox.getInt(4) - txtX;
                    int txtH = boundingBox.getInt(4) - txtY;

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

                    int compID = 0;
                    double best = Double.POSITIVE_INFINITY;
                    for (int k = 0; k < comp.size(); k++) {
                        if (comp.get(k).type.equals("junction") || comp.get(k).type.equals("crossover")) continue;

                        double alt = comp.get(k).distanceTo(sx, sy);
                        if (alt < comp.get(compID).distanceTo(sx, sy)) {
                            compID = k;
                            best = alt;
                        }
                    }

                    System.out.println("Label: " + lbl);
                    comp.get(compID).param = lbl;

                    if (best < MAX_DIST_CORRELATE) {
                        System.out.printf("Updating from %s to ", comp.get(compID).type);
                        if (lbl.toLowerCase().contains("f")) {
                            comp.get(compID).type = "capacitor";
                        } else if (lbl.toLowerCase().contains("h")) {
                            comp.get(compID).type = "inductor";
                        } else if (lbl.toLowerCase().contains("r")) {
                            comp.get(compID).type = "resistor";
                        } else if (lbl.toLowerCase().contains("q")) {
                            comp.get(compID).type = "transistor";
                        } else if (lbl.toLowerCase().contains("d")) {
                            comp.get(compID).type = "diode";
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
                        connName.add("point1");
                        connName.add("point2");
                    case "diode":
                        connSrc.add(c);
                        x.add(c.cx[0]);
                        y.add(c.cy[0]);
                        connSrc.add(c);
                        x.add(c.cx[1]);
                        y.add(c.cy[1]);
                        if (c.type.equals("diode")) {
                            connName.add("point1");
                            connName.add("point2");
                        }
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
                    case "crossover":
                        connName.add("top");
                        connSrc.add(c);
                        x.add(c.x + c.width / 2);
                        y.add(c.y);
                        connName.add("bottom");
                        connSrc.add(c);
                        x.add(c.x + c.width / 2);
                        y.add(c.y + c.height);
                        connName.add("left");
                        connSrc.add(c);
                        x.add(c.x);
                        y.add(c.y + c.height / 2);
                        connName.add("right");
                        connSrc.add(c);
                        x.add(c.x + c.width);
                        y.add(c.y + c.height / 2);
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

                if (best > MAX_DIST_CORRELATE) continue;
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

                if (best > MAX_DIST_CORRELATE) continue;
                w.c2 = connSrc.get(best);
                w.x2 = x.get(best);
                w.y2 = y.get(best);
                w.c2Name = connName.get(best);
            }

            System.out.println("Finished parsing");

            Circuit.ogf.parseComponents(comp, wires);
        }
    }
}
