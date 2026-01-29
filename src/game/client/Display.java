package game.client;

import java.awt.*;

/**
 * The {@code Display} interface provides each display with
 * the neccessary implementation to handle events needed in
 * the {@code BaseDisplay} class.
 */
public interface Display {

    BaseDisplay baseDisplay = BaseDisplay.getInstance();

    void update(Graphics g);
    void buttonHandler(Object button);
    void keyHandler(int keyCode, boolean keyActivated);
}
