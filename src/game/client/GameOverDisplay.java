package game.client;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.Collections;
import java.util.ArrayList;

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
    private static final int RACE_LOST = 2; // defined here so this class can reference it

    // Buttons.
    private JButton returnToMenuButton;

    // Images.
    private ImageIcon gameOverBackground;
    private ImageIcon racetrackBackground;
    private ImageIcon returnToMenu;

    // Object properties.
    private Game currentGame;
    private final List<Player> playersInGame;

    // When server asks the client to display a game-over without a local Game object
    // we store the alternate end type and reason here and draw them in update().
    private Integer altEndType = null;
    private String altReason = null;

    // Constructor.
    public GameOverDisplay(Game currentGame) {
        baseDisplay.clearComponents();
        this.currentGame = currentGame;
        // defensive copy so the UI won't be affected if the game's opponent list changes concurrently
        playersInGame = new ArrayList<>(currentGame.getOpponents());
        loadImages();
        addDisplayComponents();
        AudioManager.stopMusic();

        // Play a different sound depending on if the player won or lost.
        if (currentGame.getGameEndType() == RACE_WON) AudioManager.playSound("GAME_WIN", false);
        else if (currentGame.getGameEndType() == KART_CRASHED) AudioManager.playSound("GAME_OVER", false);
    }

    // Alternate constructor for when the server asks the client to display a game-over without a local Game object
    public GameOverDisplay(int endType, String reason) {
        baseDisplay.clearComponents();
        this.currentGame = null;
        this.playersInGame = Collections.emptyList();
        // Store the alternate reason and end type and draw in update() — avoid creating JLabels which may duplicate
        this.altEndType = endType;
        this.altReason = reason;
        // minimal image setup
        try {
            returnToMenu = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonMainMenu.png")));
        } catch (Exception e) {
            returnToMenu = null;
        }
        // add only the return button here — drawing will be done in update()
        returnToMenuButton = baseDisplay.addButton(returnToMenu, 354, 500);
        AudioManager.stopMusic();
    }

    private void loadImages() {
        try {
            returnToMenu = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonMainMenu.png")));
            if (currentGame != null) {
                gameOverBackground = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/bg/gameOverBackground" + currentGame.getGameEndType() + ".png")));
                racetrackBackground = currentGame.getRacetrack().getImage();
            }
        }
        catch (NullPointerException e) {
            System.err.println("Failed to locate a necessary image file.");
        }
    }

    private void addDisplayComponents() {
        // Keep the time label as a component (informational)
        if (currentGame != null) {
            baseDisplay.addLabel("Time: " + currentGame.getGameTimeFormatted(), 500, 25, 175, 450, Color.white, 20);
        }
        // The big winner/loser messages are drawn directly in update(), avoid duplicating them as components
        if (returnToMenu == null) loadImages();
        returnToMenuButton = baseDisplay.addButton(returnToMenu, 354, 500);
    }

    @Override
    public void update(Graphics g) {
        // If currentGame is available, draw race background and karts; otherwise rely on baseDisplay labels or altReason
        if (currentGame != null) {
            if (racetrackBackground != null) racetrackBackground.paintIcon(baseDisplay, g, 0, 0);
            updatePlayerKart(g);
            updateOtherKarts(g);
            if (gameOverBackground != null) gameOverBackground.paintIcon(baseDisplay, g, 0, 0);

            // Draw overlay text depending on end type (draw once in paint to avoid duplicate JLabels)
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
            }
        } else {
            if (gameOverBackground != null) gameOverBackground.paintIcon(baseDisplay, g, 0, 0);
            // If we have an alternate reason supplied by the server, draw it here (single source of truth)
            if (altEndType != null) {
                g.setColor(Color.WHITE);
                if (altEndType == RACE_WON) {
                    g.setFont(new Font("Arial", Font.BOLD, 72));
                    g.drawString("YOU WON!!", 250, 200);
                } else if (altEndType == RACE_LOST) {
                    g.setFont(new Font("Arial", Font.BOLD, 60));
                    g.setColor(Color.RED);
                    g.drawString("YOU LOST", 270, 180);
                    if (altReason != null && !altReason.isEmpty()) {
                        g.setColor(Color.WHITE);
                        g.setFont(new Font("Arial", Font.BOLD, 20));
                        g.drawString(altReason, 175, 220);
                    }
                }
            }
            // baseDisplay labels handle text added via other constructors if any — but we avoid adding duplicates here
        }
    }

    private void updatePlayerKart(Graphics g) {
        if (currentGame == null) return;
        if (currentGame.getMainPlayer() == null) return;
        Kart mainKart = currentGame.getMainPlayer().getKart();
        if (mainKart == null) return;
        if (mainKart.isMoving()) mainKart.reduceSpeed();
        drawSingleKart(g, mainKart);
    }

    private void updateOtherKarts(Graphics g) {
        if (playersInGame == null) return;
        for (Player player : playersInGame) {
            if (player == null) continue;
            Kart kart = player.getKart();
            if (kart == null) continue;
            drawSingleKart(g, kart);
        }
    }

    private void drawSingleKart(Graphics g, Kart kart) {
        if (kart == null) return;
        try {
            kart.updatePosition();
            kart.updateImage();
            if (kart.getImage() != null) kart.getImage().paintIcon(baseDisplay, g, (int) kart.getPosition().x, (int) kart.getPosition().y);
        } catch (Exception e) {
            // Protect UI from exceptions in kart drawing so a bad kart state doesn't freeze UI
            System.err.println("Error drawing kart on GameOverDisplay: " + e.getMessage());
        }
    }

    @Override
    public void buttonHandler(Object button) {
        if (button == returnToMenuButton) {
            // Stop music and clear local game reference
            AudioManager.stopMusic();
            currentGame = null;

            // Attempt to immediately tell the handler that the game ended so it stops sending kart updates
            ServerHandler handler = ServerManager.getHandler();
            if (handler != null) {
                try {
                    handler.endGame(); // quick local state update (non-blocking)
                } catch (Exception ignored) {}
            }

            // Show loading display immediately
            BaseDisplay.getInstance().setCurrentDisplay(new LoadingDisplay());

            // Perform any remaining network cleanup on a background thread so the UI stays responsive
            new Thread(() -> {
                boolean success = false;
                try {
                    if (handler != null) {
                        handler.requestLobbyData();
                    }
                    success = true;
                } catch (Exception ignored) {}

                // After network ops complete (or fail), switch to the lobby on the EDT
                javax.swing.SwingUtilities.invokeLater(() -> {
                    // Replace loading display with the lobby regardless of success to keep UX responsive
                    BaseDisplay.getInstance().setCurrentDisplay(new GameLobbyDisplay());
                    // Optionally, we could show an error pop-up if !success
                });
            }, "GameOver-ReturnToMenu-Net").start();
        }
    }

    @Override
    public void keyHandler(int keyCode, boolean keyActivated) {
        // No keys used on the display.
    }
}