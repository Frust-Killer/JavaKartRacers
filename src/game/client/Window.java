package game.client;

import javax.swing.*;

/**
 * The {@code Window} class acts as a container for
 * the display JPanels to draw content on.
 */
public class Window extends JFrame {

    // Constructor.
    public Window() {
        setTitle(Main.GAME_TITLE);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        // Supply this JFrame with a JPanel.
        var windowDisplay = new BaseDisplay();
        add(windowDisplay);

        pack(); // Resizes the window to match the JPanel.
        setLocationRelativeTo(null); // Centers the window
        setResizable(false);

        setVisible(true);
    }
}
