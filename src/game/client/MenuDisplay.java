package game.client;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/**
 * The {@code MenuDisplay} class is a concrete implementation
 * of {@code Display} for the menu.
 * From here, a user can:
 * <ul>
 * <li>Go to the game finder display.
 * <li>Exit the program.
 * <li>Mute/unmute sounds.
 * </ul>
 */
public class MenuDisplay implements Display {

    // Images.
    private ImageIcon menuBackground;
    private ImageIcon gameStart;
    private ImageIcon gameExit;
    private ImageIcon muteGame;
    private ImageIcon unmuteGame;

    // Buttons.
    private JButton startButton;
    private JButton exitButton;
    private JButton muteGameButton;

    // Constructor.
    public MenuDisplay() {
        baseDisplay.clearComponents();
        loadImages();
        addDisplayComponents();
        AudioManager.playSound("MENU_THEME", true);
    }

    private void loadImages() {
        try {
            menuBackground = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/bg/gameMenuBackground.png")));
            gameStart = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/gameStart.png")));
            gameExit = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/gameExit.png")));
            muteGame = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonMute.png")));
            unmuteGame = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonUnmute.png")));
        }
        catch (NullPointerException e) {
            System.err.println("Failed to locate a necessary image file.");
        }
    }

    private void addDisplayComponents() {
        startButton = baseDisplay.addButton(gameStart, 93, 329);
        exitButton = baseDisplay.addButton(gameExit, 173, 419);
        muteGameButton = baseDisplay.addButton(muteGame, 10, 610);
        if (AudioManager.isMuted()) muteGameButton.setIcon(unmuteGame);
    }

    @Override
    public void update(Graphics g) {
        menuBackground.paintIcon(baseDisplay, g, 0,0);

        // Draw application information in the lower right.
        g.setColor(Color.white);
        g.drawString(Main.VERSION, 817, 625);
        g.drawString(Main.STUDENT_ID, 795, 640);
    }

    @Override
    public void buttonHandler(Object button) {
        if (button == startButton) {
            baseDisplay.setCurrentDisplay(new GameJoinDisplay());
        }
        else if (button == exitButton) {
            System.exit(0);
        }
        else if (button == muteGameButton) {
            if (AudioManager.isMuted()) {
                muteGameButton.setIcon(muteGame);
                AudioManager.mute(false);
                AudioManager.playSound("MENU_THEME", true);
            }
            else {
                muteGameButton.setIcon(unmuteGame);
                AudioManager.mute(true);
                AudioManager.stopMusic();
            }
        }
    }

    @Override
    public void keyHandler(int keyCode, boolean keyActivated) {
        // No keys used on this display.
    }
}
