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
    private boolean keyBrakeActive; // space bar brake

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

    // Simple particle representation for collision effects
    private static class Particle {
        float x, y; // relative to kart position
        float vx, vy; // velocity
        int life; // ms remaining
        int maxLife;
        Color color;
        Particle(float x, float y, float vx, float vy, int life, Color color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.life = life; this.maxLife = life; this.color = color;
        }
    }

    // Map of active particles per kart
    private final Map<Integer, List<Particle>> kartParticles = new HashMap<>();

    private void drawSingleKart(Graphics g, Kart kart) {
        kart.updatePosition();
        kart.updateImage();
        // If kart is flashing due to a recent collision, draw with alternating transparency
        if (kart.isFlashing()) {
            long elapsed = System.currentTimeMillis() - kart.getFlashStart();
            int alpha = ((elapsed / 200) % 2 == 0) ? 255 : 100; // flash every 200ms
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255f));
            kart.getImage().paintIcon(baseDisplay, g2, (int) kart.getPosition().x, (int) kart.getPosition().y);
            g2.dispose();

            // Progress bar showing remaining slowdown
            long remainingMs = kart.getSlowUntil() - System.currentTimeMillis();
            if (remainingMs < 0) remainingMs = 0;
            float fraction = 1.0f - (float) remainingMs / (float) Kart.COLLISION_EFFECT_MS;
            if (fraction < 0f) fraction = 0f; if (fraction > 1f) fraction = 1f;
            int barWidth = 40;
            int barHeight = 6;
            int x = (int) kart.getPosition().x;
            int y = (int) kart.getPosition().y + kart.getImage().getIconHeight() + 4;

            // Draw background
            g.setColor(new Color(0,0,0,160));
            g.fillRect(x, y, barWidth, barHeight);
            // Draw filled fraction (green -> red)
            Color fill = new Color((int) (255 * (1-fraction)), (int) (255 * fraction), 0);
            g.setColor(fill);
            g.fillRect(x+1, y+1, (int) ((barWidth-2) * fraction), barHeight-2);

            // Spawn particles on initial flash start
            List<Particle> particles = kartParticles.computeIfAbsent(kart.getKartNumber(), k -> new ArrayList<>());
            if (particles.isEmpty() && elapsed < 200) {
                spawnParticlesForKart(kart, 12);
            }
            // Update and draw particles
            updateAndDrawParticles(g, kart, particles);

        } else {
            kart.getImage().paintIcon(baseDisplay, g, (int) kart.getPosition().x, (int) kart.getPosition().y);
            // Clear particles if any
            kartParticles.remove(kart.getKartNumber());
        }
    }

    private void spawnParticlesForKart(Kart kart, int count) {
        List<Particle> particles = kartParticles.computeIfAbsent(kart.getKartNumber(), k -> new ArrayList<>());
        float w = kart.getImage().getIconWidth();
        float h = kart.getImage().getIconHeight();
        for (int i=0;i<count;i++) {
            float angle = (float) (Math.random() * Math.PI * 2);
            float speed = (float) (Math.random() * 1.8 + 0.6);
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed;
            int life = (int) (300 + Math.random() * 400);
            Color color = new Color(200 + (int)(Math.random()*55), 50, 50, 220);
            particles.add(new Particle(w/2f, h/2f, vx, vy, life, color));
        }
    }

    private void updateAndDrawParticles(Graphics g, Kart kart, List<Particle> particles) {
        Graphics2D g2 = (Graphics2D) g.create();
        int baseX = (int) kart.getPosition().x;
        int baseY = (int) kart.getPosition().y;
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            if (p.life <= 0) { it.remove(); continue; }
            // update
            p.x += p.vx;
            p.y += p.vy;
            p.vx *= 0.98f; p.vy *= 0.98f;
            p.life -= 16; // approx frame
            float alpha = Math.max(0f, (float)p.life / (float)p.maxLife);
            Color c = new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int)(p.color.getAlpha()*alpha));
            g2.setColor(c);
            int size = (int) Math.max(2, 6 * alpha);
            g2.fillOval(baseX + (int)p.x - size/2, baseY + (int)p.y - size/2, size, size);
        }
        g2.dispose();
    }

    // Called by ServerHandler when a synchronized collision message arrives from the server
    public void handleNetworkCollision(int kart1Number, int kart2Number, long timestamp, float orig1, float orig2) {
        // Find the players and schedule the collision with the supplied timestamp and speeds
        for (Player p : opponents) {
            Kart k = p.getKart();
            if (k.getKartNumber() == kart1Number) {
                k.scheduleCollision(timestamp, orig1);
            }
            if (k.getKartNumber() == kart2Number) {
                k.scheduleCollision(timestamp, orig2);
            }
        }
        // Also check main player
        if (mainPlayerKart.getKartNumber() == kart1Number) mainPlayerKart.scheduleCollision(timestamp, orig1);
        if (mainPlayerKart.getKartNumber() == kart2Number) mainPlayerKart.scheduleCollision(timestamp, orig2);
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

        // Brake (space) - strong deceleration while active
        if (keyBrakeActive) {
            kart.applyBrake();
        }
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
            else if (keyCode == KeyEvent.VK_SPACE) keyBrakeActive = keyActivated;
        }

        // Open the pause menu once the player presses "Esc".
        if (keyCode == KeyEvent.VK_ESCAPE) {
            baseDisplay.setCurrentDisplay(new GamePauseDisplay(activeGame, this));
        }
    }
}