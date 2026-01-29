package game.client;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;

/**
 * The {@code GamePauseDisplay} class is a concrete implementation
 * of {@code Display} for providing the player with options mid-game.
 * From here, a user can:
 * <ul>
 * <li>Return to the game.
 * <li>Mute/unmute the sounds.
 * <li>Return to the menu.
 * </ul>
 */
public class GamePauseDisplay implements Display {

    // Buttons.
    private JButton returnToMenuButton;
    private JButton resumeGameButton;
    private JButton muteGameButton;

    // Images.
    private ImageIcon gamePausedBackground;
    private ImageIcon racetrackBackground;
    private ImageIcon returnToMenu;
    private ImageIcon resumeGame;
    private ImageIcon muteGame;
    private ImageIcon unmuteGame;

    // Object properties.
    private Game currentGame;
    private final GameDisplay suspendedGameDisplay;
    private final List<Player> playersInGame;

    // Constructor.
    public GamePauseDisplay(Game currentGame, GameDisplay suspendedGameDisplay) {
        // Do not clear components here as the suspended GameDisplay would break.
        this.currentGame = currentGame;
        this.suspendedGameDisplay = suspendedGameDisplay;
        loadImages();
        addDisplayComponents();
        playersInGame = currentGame.getOpponents();
    }

    private void loadImages() {
        try {
            gamePausedBackground = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/bg/gamePausedBackground.png")));
            returnToMenu = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonMainMenu.png")));
            resumeGame = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonResume.png")));
            muteGame = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonMute.png")));
            unmuteGame = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonUnmute.png")));
            racetrackBackground = currentGame.getRacetrack().getImage();
        }
        catch (NullPointerException e) {
            System.err.println("Failed to locate a necessary image file.");
        }
    }

    private void addDisplayComponents() {
        resumeGameButton = baseDisplay.addButton(resumeGame, 370, 400);
        muteGameButton = baseDisplay.addButton(muteGame, 371, 450);
        if (AudioManager.isMuted()) muteGameButton.setIcon(unmuteGame);
        returnToMenuButton = baseDisplay.addButton(returnToMenu, 354, 500);
    }

    @Override
    public void update(Graphics g) {
        racetrackBackground.paintIcon(baseDisplay, g, 0, 0);
        updatePlayerKart(g);
        updateOtherKarts(g);
        gamePausedBackground.paintIcon(baseDisplay, g, 0, 0);
    }

    private void updatePlayerKart(Graphics g) {
        Kart mainKart = currentGame.getMainPlayer().getKart();
        if (mainKart.isMoving()) mainKart.reduceSpeed();
        if (!currentGame.isKartValid(mainKart)) suspendedGameDisplay.suspendForwardMovement();
        drawSingleKart(g, mainKart);
    }

    private void updateOtherKarts(Graphics g) {
        for (Player player : playersInGame) {
            Kart kart = player.getKart();
            currentGame.checkCollisionWithOtherKart(kart);
            drawSingleKart(g, kart);
        }
    }

    private void drawSingleKart(Graphics g, Kart kart) {
        kart.updatePosition();
        kart.updateImage();
        kart.getImage().paintIcon(baseDisplay, g, (int) kart.getPosition().x, (int) kart.getPosition().y);
    }

    @Override
    public void buttonHandler(Object button) {
        if (button == resumeGameButton) {
            // Remove GamePauseDisplay components and send the player back to the game.
            baseDisplay.remove(resumeGameButton);
            baseDisplay.remove(muteGameButton);
            baseDisplay.remove(returnToMenuButton);
            baseDisplay.setCurrentDisplay(suspendedGameDisplay);
        }
        else if (button == muteGameButton) {
            if (AudioManager.isMuted()) {
                muteGameButton.setIcon(muteGame);
                AudioManager.mute(false);
            }
            else {
                muteGameButton.setIcon(unmuteGame);
                AudioManager.mute(true);
            }
        }
        else if (button == returnToMenuButton) {
            // Close game-related operations and send the player to the menu.
            AudioManager.stopMusic();
            currentGame = null;
            ServerManager.getHandler().endGame();
            ServerManager.getHandler().terminateConnection();
            baseDisplay.setCurrentDisplay(new MenuDisplay());
        }
    }

    @Override
    public void keyHandler(int keyCode, boolean keyActivated) {
        // No keys used on the display.
    }
}
