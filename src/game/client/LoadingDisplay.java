package game.client;

import javax.swing.*;
import java.awt.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A small loading display that shows "Loading" with animated dots.
 * This is intended to be shown while network operations (like returning
 * to lobby) happen on a background thread.
 */
public class LoadingDisplay implements Display {

    private String baseText = "Loading";
    private int dotCount = 0;
    private JLabel label;
    private JPanel panel;
    private Timer timer;

    public LoadingDisplay() {
        // nothing to add to BaseDisplay (we'll draw directly in update)
        timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                dotCount = (dotCount + 1) % 4;
            }
        }, 0, 500);
    }

    @Override
    public void update(Graphics g) {
        // draw a simple centered loading text
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRect(0, 0, baseDisplay.getWidth(), baseDisplay.getHeight());

        StringBuilder sb = new StringBuilder(baseText);
        for (int i = 0; i < dotCount; i++) sb.append('.');

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 36));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(sb.toString());
        int h = fm.getHeight();
        int x = (baseDisplay.getWidth() - w) / 2;
        int y = (baseDisplay.getHeight() - h) / 2 + fm.getAscent();
        g2.drawString(sb.toString(), x, y);
        g2.dispose();
    }

    @Override
    public void buttonHandler(Object button) { /* no buttons */ }

    @Override
    public void keyHandler(int keyCode, boolean keyActivated) { /* no keys */ }
}
