package game.client;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.Random;

public class Banana {
    private final ImageIcon image;
    private float x, y;
    private final Rectangle2D.Float bounds;
    private final Racetrack racetrack;

    public Banana(Racetrack racetrack, float x, float y) {
        this.racetrack = racetrack;
        // Try relative resource first
        java.net.URL res = getClass().getResource("images/racetrack/Banana.gif");
        if (res == null) {
            // Try absolute resource path
            res = getClass().getResource("/game/client/images/racetrack/Banana.gif");
        }
        if (res != null) {
            this.image = new ImageIcon(res);
        } else {
            // Fallback to file system path (useful when running from IDE)
            java.io.File f = new java.io.File("src/game/client/images/racetrack/Banana.gif");
            if (f.exists()) {
                this.image = new ImageIcon(f.getAbsolutePath());
            } else {
                System.err.println("[Banana] Resource not found (resource and file): images/racetrack/Banana.gif");
                this.image = new ImageIcon();
            }
        }
        this.x = x;
        this.y = y;
        int w = Math.max(16, image.getIconWidth());
        int h = Math.max(16, image.getIconHeight());
        this.bounds = new Rectangle2D.Float(x, y, w, h);
    }

    public void draw(Graphics g) {
        // Use BaseDisplay component so the GIF animates and the image paints correctly
        if (image != null && image.getIconWidth() > 0 && image.getIconHeight() > 0) {
            image.paintIcon(BaseDisplay.getInstance(), g, (int) x, (int) y);
        } else {
            // Fallback: draw a simple yellow banana marker
            g.setColor(Color.YELLOW);
            g.fillOval((int)x, (int)y, 24, 12);
            g.setColor(Color.BLACK);
            g.drawString("Banana", (int)x, (int)y + 24);
        }
         // update bounds in case image size was unknown at construction
         int w = Math.max(16, image.getIconWidth());
         int h = Math.max(16, image.getIconHeight());
         this.bounds.setRect(x, y, w, h);
    }

    public Rectangle2D.Float getBounds() { return bounds; }

    public float getX() { return x; }
    public float getY() { return y; }

    // Simple helper to spawn a banana at a random location inside the playable area
    public static Banana randomBanana(Racetrack racetrack) {
        // choose a random point within the playable area bounding box and ensure it's inside the track area
        Rectangle r = racetrack.getImage().getIconWidth() > 0 ? new Rectangle(0,0,racetrack.getImage().getIconWidth(), racetrack.getImage().getIconHeight()) : new Rectangle(0,0,800,600);
        Random rnd = new Random();
        float px, py;
        int attempts = 0;
        do {
            px = rnd.nextInt(r.width - 64) + 16; // margin
            py = rnd.nextInt(r.height - 64) + 16;
            attempts++;
            if (attempts > 50) break;
        } while (!racetrack.getPlayableArea().contains(px + 10, py + 10));
        return new Banana(racetrack, px, py);
    }
}