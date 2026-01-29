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
        gameTimer.stop();
        gameEndType = RACE_LOST;
        gameEndReason = "Player " + winnerNumber + " has won the game!";
        BaseDisplay.getInstance().setCurrentDisplay(new GameOverDisplay(this));
    }

    public void kartCollision(Player victim1, Player victim2) {
        isGameOver = true;
        gameTimer.stop();
        gameEndType = KART_CRASHED;
        gameEndReason = "Player " + victim1.getPlayerNumber() + " and Player " + victim2.getPlayerNumber() + " have crashed!";
        BaseDisplay.getInstance().setCurrentDisplay(new GameOverDisplay(this));
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