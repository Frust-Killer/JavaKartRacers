package game.client;

import java.awt.event.KeyEvent;

/**
 * The {@code ControlledPlayer} class is a {@code Player} that
 * can be controlled with a set of four keys.
 */
public class ControlledPlayer extends Player {

    // Key bindings.
    private int keyForward;
    private int keyBackward;
    private int keyLeft;
    private int keyRight;

    // Property access methods.
    public int getKeyForward()  { return keyForward; }
    public int getKeyBackward() { return keyBackward; }
    public int getKeyLeft()     { return keyLeft; }
    public int getKeyRight()    { return keyRight; }

    // Constructor.
    public ControlledPlayer(int playerNumber) {
        super(playerNumber);
        assignKeys();
    }

    // These can eventually be collected from a settings file.
    private void assignKeys() {
        keyForward = KeyEvent.VK_W;
        keyBackward = KeyEvent.VK_S;
        keyLeft = KeyEvent.VK_A;
        keyRight = KeyEvent.VK_D;
    }
}
