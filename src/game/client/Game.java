package game.client;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The {@code Game} class controls events that occur
 * during the lifetime of a game and provides access
 * to any necessary game properties.
 */
public class Game {

    // Constants.
    private static final int TOTAL_LAPS = 3;

    // Types of game over.
    private static final int RACE_WON       = 0;
    private static final int KART_CRASHED   = 1;
    private static final int RACE_LOST      = 2;
    private static final int NO_OPPONENTS   = 3;

    // Object properties.
    private Racetrack racetrack;
    private ControlledPlayer mainPlayer;
    private List<Player> opponents;
    private List<Rectangle> gameCheckpoints;
    private int trackType;
    private boolean isBadWeather;
    private boolean isGameOver;
    private int currentLap;
    private int nextCheckpoint;
    private int gameEndType;
    private String gameEndReason;
    private final Timer gameTimer;
    private int gameTimeInSecondsTotal;
    

    // Property access methods.
    public Racetrack getRacetrack()         { return racetrack; }
    public List<Player> getOpponents()      { return opponents; }
    public int getGameEndType()             { return gameEndType; }
    public String getGameEndReason()        { return gameEndReason; }
    public int getCurrentLap()              { return currentLap; }
    public ControlledPlayer getMainPlayer() { return mainPlayer; }
    public int getTrackType()               { return trackType; }
    public boolean getWeatherForecast()     { return isBadWeather; }

    // Constructor.
    public Game(GameOptions options) {
        collectGameInformation(options);

        for (Player player : opponents) assignKartToPlayer(player, options);
        assignKartToPlayer(mainPlayer, options);

        currentLap = 1;
        nextCheckpoint = 0;

        gameTimeInSecondsTotal = 0;
        gameTimer = new Timer(1000, e -> gameTimeInSecondsTotal++);

        ServerManager.getHandler().setGame(this);
    }

    private void collectGameInformation(GameOptions options) {
        trackType = options.getGameMap();
        isBadWeather = options.getWeather();
        racetrack = new Racetrack(trackType);
        gameCheckpoints = racetrack.getCheckpoints();
        opponents = options.getOpponents();
        mainPlayer = options.getMainPlayer();
    }
    
   

    public void startGameTimer() {
        gameTimer.start();
    }

    public void removeOpponent(int opponentNumber) {
        opponents.removeIf(opponent -> opponent.getPlayerNumber() == opponentNumber);
    }

    public void assignKartToPlayer(Player player, GameOptions options) {
        // Setup information needed for the kart.
        int startDirection = racetrack.getStartDirection();
        Point startPosition = racetrack.getStartPosition(player.getPlayerNumber());
        int kartType = options.getPlayerKartChoice(player.getPlayerNumber());

        // Create the kart and provide it to the player.
        var kart = new Kart(startDirection, startPosition, player, kartType, racetrack);
        player.setKart(kart);
    }

    public void winGame(Player winner) {
        isGameOver = true;
        gameTimer.stop();
        gameEndType = RACE_WON;
        gameEndReason = "Player " + winner.getPlayerNumber() + " has won the game!";
        ServerManager.getHandler().raceWon();
        BaseDisplay.getInstance().setCurrentDisplay(new GameOverDisplay(this));
    }

    public void loseGame(String[] data) {
        isGameOver = true;
        int winnerNumber = Integer.parseInt(data[1]);
        String winnerName = "";
        if (data.length > 2) {
            winnerName = data[2].replaceAll("_", " ");
        }
        gameTimer.stop();
        gameEndType = RACE_LOST;
        if (winnerName == null || winnerName.isEmpty()) {
            gameEndReason = "Player " + winnerNumber + " has won the game!";
        } else {
            gameEndReason = "Player " + winnerName + " (" + winnerNumber + ") has won the game!";
        }
        BaseDisplay.getInstance().setCurrentDisplay(new GameOverDisplay(this));
    }

    public void kartCollision(Player victim1, Player victim2) {
        // Apply bounce: push both karts away from collision point for a small distance
        Kart k1 = victim1.getKart();
        Kart k2 = victim2.getKart();
        if (k1 == null || k2 == null) return;

        // Compute vector from k2 -> k1
        float dx = (float) (k1.getPosition().x - k2.getPosition().x);
        float dy = (float) (k1.getPosition().y - k2.getPosition().y);
        float len = (float) Math.sqrt(dx*dx + dy*dy);
        if (len < 1e-3f) {
            // Positions coincide -- use a small random vector
            dx = (float) (Math.random() - 0.5f);
            dy = (float) (Math.random() - 0.5f);
            len = (float) Math.sqrt(dx*dx + dy*dy);
            if (len == 0f) { dx = 1f; len = 1f; }
        }
        // Normalize and scale bounce distance
        float bounceDistance = 12f; // pixels to push apart
        float nx = dx / len;
        float ny = dy / len;

        // Apply bounce in opposite directions
        k1.applyBounce(nx * bounceDistance, ny * bounceDistance);
        k2.applyBounce(-nx * bounceDistance, -ny * bounceDistance);

        // Schedule slowdown/flash to start after a short delay (3 seconds)
        long now = System.currentTimeMillis();
        long scheduledStart = now + 3000; // 3 seconds delay
        float orig1 = k1.getSpeed();
        float orig2 = k2.getSpeed();
        k1.scheduleCollision(scheduledStart, orig1);
        k2.scheduleCollision(scheduledStart, orig2);

        // Notify server so it can broadcast a synchronized collision event with the scheduled timestamp and original speeds
        ServerHandler handler = ServerManager.getHandler();
        if (handler != null) {
            handler.sendCollision(k1.getKartNumber(), k2.getKartNumber(), scheduledStart, orig1, orig2);
        }
        // Play collision sound handled in Kart.onKartCollision (will play when collision actually begins)
    }

    public void endGame() {
        if (isGameOver) return;
        gameTimer.stop();
        gameEndType = NO_OPPONENTS;
        gameEndReason = "No opponents left in the race!";
        BaseDisplay.getInstance().setCurrentDisplay(new GameOverDisplay(this));
    }

    // Collision detection between other karts, boundaries, and checkpoints.
    public boolean isKartValid(Kart kart) {
        checkRaceCheckpoints(kart);
        checkCollisionWithOtherKart(kart);
        return !kart.hasCrashed();
    }

    public void checkCollisionWithOtherKart(Kart playerKart) {
        for (Player opponent : opponents) {
            Kart kart = opponent.getKart();
            if (playerKart.equals(kart)) continue;
            if (playerKart.getHitBox().intersects(kart.getHitBox())) {
                kartCollision(playerKart.getOwner(), opponent);
            }
        }
    }

    public void checkRaceCheckpoints(Kart kart) {
        boolean kartGoingRightWay = !kart.isGoingWrongWay();
        boolean kartPassedNextCheckpoint = kart.getHitBox().intersects(gameCheckpoints.get(nextCheckpoint));

        if (kartPassedNextCheckpoint && kartGoingRightWay) {
            nextCheckpoint++;
            if (nextCheckpoint == gameCheckpoints.size()) completedLap(kart);
        }
    }

    private void completedLap(Kart kart) {
        if (currentLap < TOTAL_LAPS) {
            currentLap++;
            nextCheckpoint = 0; // Reset checkpoints.
            AudioManager.playSound("NEW_LAP", false);
        }
        else winGame(kart.getOwner());
    }

    // Format the game time to use "00:00".
    public String getGameTimeFormatted() {
        int gameTimeInMinutes = gameTimeInSecondsTotal / 60;
        int gameTimeInSeconds = gameTimeInSecondsTotal % 60;

        String gameTimeFormatted;

        if (gameTimeInMinutes < 10 && gameTimeInSeconds < 10) {
            gameTimeFormatted = "0" + gameTimeInMinutes + ":0" + gameTimeInSeconds;
        }
        else if (gameTimeInMinutes < 10) {
            gameTimeFormatted = "0" + gameTimeInMinutes + ":" + gameTimeInSeconds;
        }
        else if (gameTimeInSeconds < 10) {
            gameTimeFormatted = gameTimeInMinutes + ":0" + gameTimeInSeconds;
        }
        else {
            gameTimeFormatted = gameTimeInMinutes + ":" + gameTimeInSeconds;
        }
        return gameTimeFormatted;
    }
}