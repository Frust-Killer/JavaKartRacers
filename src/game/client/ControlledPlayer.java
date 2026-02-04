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
    private int keyNitro; // added nitro key binding

    // Property access methods.
    public int getKeyForward()  { return keyForward; }
    public int getKeyBackward() { return keyBackward; }
    public int getKeyLeft()     { return keyLeft; }
    public int getKeyRight()    { return keyRight; }
    public int getKeyNitro()    { return keyNitro; }

    // Constructor.
    public ControlledPlayer(int playerNumber) {
        super(playerNumber);
        assignKeys();
    }

    // These can eventually be collected from a settings file.
    private void assignKeys() {
        // Switched from WASD to arrow keys
        keyForward = KeyEvent.VK_UP;
        keyBackward = KeyEvent.VK_DOWN;
        keyLeft = KeyEvent.VK_LEFT;
        keyRight = KeyEvent.VK_RIGHT;
        keyNitro = KeyEvent.VK_N; // default nitro key
    }
}