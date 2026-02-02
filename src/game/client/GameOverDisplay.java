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
        // Add prominent winner/loser labels via baseDisplay so they appear above other UI elements
        int endType = currentGame.getGameEndType();
        if (endType == RACE_WON) {
            baseDisplay.addLabel("YOU WON!!", 400, 80, 200, 150, Color.WHITE, 48);
        } else if (endType == 2) { // RACE_LOST is defined in Game as 2
            baseDisplay.addLabel("YOU LOST", 400, 80, 200, 140, Color.RED, 48);
            baseDisplay.addLabel(currentGame.getGameEndReason(), 500, 25, 175, 220, Color.white, 20);
        }
        returnToMenuButton = baseDisplay.addButton(returnToMenu, 354, 500);
    }

    @Override
    public void update(Graphics g) {
        racetrackBackground.paintIcon(baseDisplay, g, 0, 0);
        updatePlayerKart(g);
        updateOtherKarts(g);
        gameOverBackground.paintIcon(baseDisplay, g, 0, 0);

        // Draw overlay text depending on end type
        int endType = currentGame.getGameEndType();
        g.setColor(Color.WHITE);
        if (endType == RACE_WON) {
            g.setFont(new Font("Arial", Font.BOLD, 72));
            g.drawString("YOU WON!!", 250, 200);
        } else if (endType == RACE_LOST) {
            g.setFont(new Font("Arial", Font.BOLD, 60));
            g.setColor(Color.RED);
            g.drawString("YOU LOST", 270, 180);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            g.drawString(currentGame.getGameEndReason(), 175, 220);
        } else {
            // default behavior for other end types
        }
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