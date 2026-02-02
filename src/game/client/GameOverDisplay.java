package game.client;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;

/**
 * The {@code GameOverDisplay} class is a concrete implementation
 * of {@code Display} for ending the game.
 * From here, a user can:
 * <ul>
 * <li>Return to the menu.
 * </ul>
 */
public class GameOverDisplay implements Display {

    // Constants.
    private static final int RACE_WON = 0;
    private static final int KART_CRASHED = 1;

    // Buttons.
    private JButton returnToMenuButton;

    // Images.
    private ImageIcon gameOverBackground;
    private ImageIcon racetrackBackground;
    private ImageIcon returnToMenu;

    // Object properties.
    private Game currentGame;
    private final List<Player> playersInGame;

    // Constructor.
    public GameOverDisplay(Game currentGame) {
        baseDisplay.clearComponents();
        this.currentGame = currentGame;
        playersInGame = currentGame.getOpponents();
        loadImages();
        addDisplayComponents();
        AudioManager.stopMusic();

        // Play a different sound depending on if the player won or lost.
        if (currentGame.getGameEndType() == RACE_WON) AudioManager.playSound("GAME_WIN", false);
        else if (currentGame.getGameEndType() == KART_CRASHED) AudioManager.playSound("GAME_OVER", false);
    }

    private void loadImages() {
        try {
            returnToMenu = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonMainMenu.png")));
            gameOverBackground = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/bg/gameOverBackground" + currentGame.getGameEndType() + ".png")));
            racetrackBackground = currentGame.getRacetrack().getImage();
        }
        catch (NullPointerException e) {
            System.err.println("Failed to locate a necessary image file.");
        }
    }

    private void addDisplayComponents() {
        baseDisplay.addLabel(currentGame.getGameEndReason(), 500, 25, 175, 400, Color.white, 20);
        baseDisplay.addLabel("Time: " + currentGame.getGameTimeFormatted(), 500, 25, 175, 450, Color.white, 20);
        returnToMenuButton = baseDisplay.addButton(returnToMenu, 354, 500);
    }

    @Override
    public void update(Graphics g) {
        racetrackBackground.paintIcon(baseDisplay, g, 0, 0);
        updatePlayerKart(g);
        updateOtherKarts(g);
        gameOverBackground.paintIcon(baseDisplay, g, 0, 0);
    }

    private void updatePlayerKart(Graphics g) {
        Kart mainKart = currentGame.getMainPlayer().getKart();
        if (mainKart.isMoving()) mainKart.reduceSpeed();
        drawSingleKart(g, mainKart);
    }

    private void updateOtherKarts(Graphics g) {
        for (Player player : playersInGame) {
            Kart kart = player.getKart();
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
        if (button == returnToMenuButton) {
            // Close game-related operations and send the player to the menu.
            AudioManager.stopMusic();
            currentGame = null;
            // Return player to the lobby so they can play again without re-login.
            ServerHandler handler = ServerManager.getHandler();
            if (handler != null) {
                try {
                    handler.endGame();
                    // Request lobby data to re-enter lobby
                    handler.requestLobbyData();
                } catch (Exception ignored) {}
            }
            baseDisplay.setCurrentDisplay(new GameJoinDisplay());
        }
    }

    @Override
    public void keyHandler(int keyCode, boolean keyActivated) {
        // No keys used on the display.
    }
}