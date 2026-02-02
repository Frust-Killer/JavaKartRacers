package game.client;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * The {@code GameDisplay} class is a concrete implementation
 * of {@code Display} for racing a kart around a racetrack.
 * From here, a user can:
 * <ul>
 * <li>Control their kart with key inputs.
 * <li>Progress laps and cross the finish line to win.
 * <li>Crash into an opponent to end the game.
 * <li>Open the pause menu.
 * </ul>
 */
public class GameDisplay implements Display {

    // Constants.
    private static final int RIGHT      = 0;
    private static final int LEFT       = 1;
    private static final int FORWARD    = 1;
    private static final int BACKWARD   = -1;

    // Image sets.
    private final ImageIcon[] raceCountdown = new ImageIcon[4];
    private final ImageIcon[] lapImages = new ImageIcon[3];
    private final ImageIcon[] weatherImages = new ImageIcon[3];

    // Player controls.
    private int keyLeft;
    private int keyRight;
    private int keyForward;
    private int keyBackward;

    // Player control states.
    private boolean keyForwardActive;
    private boolean keyBackwardActive;
    private boolean keyLeftActive;
    private boolean keyRightActive;

    // Images.
    private ImageIcon wrongWayMessage;
    private ImageIcon racetrackBackground;
    private ImageIcon spectators0;
    private ImageIcon spectators1;
    private ImageIcon spectators2;
    private ImageIcon weather;
    private ImageIcon playerPointer;

    // Object properties.
    private Racetrack racetrack;
    private ControlledPlayer mainPlayer;
    private Kart mainPlayerKart;
    private Game activeGame;
    private List<Player> opponents;
    private Timer raceCountdownTimer;
    private int raceCountdownStage;
    private boolean raceCountdownFinished;
    private boolean isBadWeather;
    private boolean hasRaceStarted;
    private long lastKartSendTime = 0;
    private static final long KART_SEND_INTERVAL_MS = 100; // 10 updates per second

    private final ServerHandler connection = ServerManager.getHandler();

    public void suspendForwardMovement() {
        if (keyForwardActive) keyForwardActive = false;
    }

    // Constructor.
    public GameDisplay(Game newGame) {
        baseDisplay.clearComponents();
        connection.setGameDisplay(this);
        collectGameInformation(newGame);
        loadImages();
        collectPlayerControls();
        beginRaceCountdown();
        AudioManager.stopMusic();
        AudioManager.playSound("RACE_THEME", true);
    }

    private void collectGameInformation(Game game) {
        activeGame = game;
        racetrack = activeGame.getRacetrack();
        opponents = activeGame.getOpponents();
        mainPlayer = activeGame.getMainPlayer();
        mainPlayerKart = mainPlayer.getKart();
        isBadWeather = activeGame.getWeatherForecast();
    }

    private void collectPlayerControls() {
        keyLeft = mainPlayer.getKeyLeft();
        keyRight = mainPlayer.getKeyRight();
        keyForward = mainPlayer.getKeyForward();
        keyBackward = mainPlayer.getKeyBackward();
    }

    private void loadImages() {
        try {
            Arrays.setAll(raceCountdown, i -> new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("images/racetrack/raceCountdown" + i + ".png"))));
            Arrays.setAll(lapImages, i -> new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("images/ui/lap" + i + ".png"))));
            Arrays.setAll(weatherImages, i -> new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("images/racetrack/weather" + i + ".gif"))));

            racetrackBackground = racetrack.getImage();
            wrongWayMessage = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/wrongWay.png")));
            spectators0 = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/racetrack/spectators0.gif")));
            spectators1 = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/racetrack/spectators1.gif")));
            spectators2 = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/racetrack/spectators2.gif")));
            playerPointer = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/playerPointer.gif")));
            weather = weatherImages[activeGame.getTrackType()];
        }
        catch (NullPointerException e) {
            System.err.println("Failed to locate a necessary image file.");
        }
    }

    private void beginRaceCountdown() {
        raceCountdownStage = 0;
        raceCountdownFinished = false;
        raceCountdownTimer = new Timer(1000, e -> updateRaceCountdown());
        raceCountdownTimer.start();
        AudioManager.playSound("RACE_COUNTDOWN", false);
    }

    @Override
    public void update(Graphics g) {
        // Throttle kart updates to avoid network congestion
        long now = System.currentTimeMillis();
        if (now - lastKartSendTime >= KART_SEND_INTERVAL_MS) {
            connection.sendKart(mainPlayerKart);
            lastKartSendTime = now;
        }
        drawRacetrack(g);
        updateOtherKarts(g);
        updatePlayerKart(g);
        processKeyInputs();

        if (isBadWeather) weather.paintIcon(baseDisplay, g, 0, 0);

        drawHUD(g);

        if (mainPlayerKart.isGoingWrongWay()) wrongWayMessage.paintIcon(baseDisplay, g, 0, 284);
        if (!raceCountdownFinished) raceCountdown[raceCountdownStage].paintIcon(baseDisplay, g, 0, 0);
    }

    private void updateRaceCountdown() {
        if (raceCountdownStage == 2) {
            hasRaceStarted = true;
            activeGame.startGameTimer();
        }
        else if (raceCountdownStage + 1 == raceCountdown.length) {
            raceCountdownFinished = true;
            raceCountdownTimer.stop();
        }
        raceCountdownStage++;
    }

    public void updateOpponentKart(int kartNumber, float rotation, float speed, float positionX, float positionY) {
        for (Player opponent : opponents) {
            if (opponent.getPlayerNumber() == kartNumber) {
                Kart kart = opponent.getKart();
                kart.setRotation(rotation);
                kart.setSpeed(speed);
                kart.setPosition(positionX, positionY);
                break;
            }
        }
    }

    private void drawRacetrack(Graphics g) {
        racetrackBackground.paintIcon(baseDisplay, g, 0,0);
        spectators0.paintIcon(baseDisplay, g, 173, 59);
        spectators1.paintIcon(baseDisplay, g, 214, 449);
        spectators2.paintIcon(baseDisplay, g, 571, 447);
    }

    private void updatePlayerKart(Graphics g) {
        Kart kart = mainPlayer.getKart();
        if (kart.isMoving()) kart.reduceSpeed();
        if (!activeGame.isKartValid(kart)) suspendForwardMovement();
        drawSingleKart(g, kart);
    }

    private void updateOtherKarts(Graphics g) {
        for (Player opponent : opponents) {
            Kart kart = opponent.getKart();
            activeGame.checkCollisionWithOtherKart(kart);
            drawSingleKart(g, kart);
        }
    }

    private void drawSingleKart(Graphics g, Kart kart) {
        kart.updatePosition();
        kart.updateImage();
        kart.getImage().paintIcon(baseDisplay, g, (int) kart.getPosition().x, (int) kart.getPosition().y);
    }

    private void drawHUD(Graphics g) {
        // Black semi-transparent.
        g.setColor(new Color(0,0,0, 128));

        // Player lap area, lower left.
        g.fillRect(0, 600, 189, 50);
        ImageIcon playerLap = lapImages[activeGame.getCurrentLap()-1];
        playerLap.paintIcon(baseDisplay, g, 0, 600);

        // Game time area, top central.
        g.fillRect(375, 0, 100, 50);
        g.setColor(Color.white);
        g.setFont(new Font("Arial", Font.BOLD, 30));
        g.drawString(activeGame.getGameTimeFormatted(), 386, 36);

        // Display an arrow above the player's head for easier identification.
        playerPointer.paintIcon(baseDisplay, g, (int) mainPlayerKart.getPosition().x, (int) mainPlayerKart.getPosition().y);

        // Draw opponent names above their karts
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        for (Player opponent : opponents) {
            Kart k = opponent.getKart();
            String name = opponent.getName();
            if (name == null || name.isEmpty()) continue;
            int x = (int) k.getPosition().x;
            int y = (int) k.getPosition().y - 10; // above kart
            g.drawString(name, x, y);
        }
    }

    public void sendPlayerToMenu() {
        baseDisplay.setCurrentDisplay(new MenuDisplay());
    }

    private void processKeyInputs() {
        Kart kart = mainPlayer.getKart();

        if (keyRightActive) kart.updateRotation(RIGHT);
        else if (keyLeftActive) kart.updateRotation(LEFT);

        if (keyForwardActive) kart.updateSpeed(FORWARD);
        else if (keyBackwardActive) kart.updateSpeed(BACKWARD);
    }

    @Override
    public void buttonHandler(Object button) {
        // No buttons used on this display.
    }

    @Override
    public void keyHandler(int keyCode, boolean keyActivated) {
        // Prevent the player from driving off before the countdown finishes.
        if (hasRaceStarted) {
            if (keyCode == keyLeft) keyLeftActive = keyActivated;
            else if (keyCode == keyRight) keyRightActive = keyActivated;
            else if (keyCode == keyForward) keyForwardActive = keyActivated;
            else if (keyCode == keyBackward) keyBackwardActive = keyActivated;
        }

        // Open the pause menu once the player presses "Esc".
        if (keyCode == KeyEvent.VK_ESCAPE) {
            baseDisplay.setCurrentDisplay(new GamePauseDisplay(activeGame, this));
        }
    }
}