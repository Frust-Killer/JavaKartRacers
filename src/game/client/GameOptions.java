package game.client;

import java.util.List;
import java.util.Map;

/**
 * The {@code GameOptions} class is used to transport information
 * about the game to the game creator from the lobby.
 */
public class GameOptions {

    // Object properties.
    private final int gameMap;
    private final boolean isBadWeather;
    private final ControlledPlayer mainPlayer;
    private final List<Player> opponents;
    private final Map<Integer, Integer> playerKartChoices;

    // Property access methods.
    public int getGameMap()                 { return gameMap; }
    public boolean getWeather()             { return isBadWeather; }
    public ControlledPlayer getMainPlayer() { return mainPlayer; }
    public List<Player> getOpponents()      { return opponents; }
    public Integer getPlayerKartChoice(int playerNumber) {
        return playerKartChoices.get(playerNumber);
    }

    // Constructor.
    public GameOptions(int mapChoice, boolean weather, ControlledPlayer mainPlayer, List<Player> opponents, Map<Integer, Integer> kartChoices) {
        gameMap = mapChoice;
        isBadWeather = weather;
        this.mainPlayer = mainPlayer;
        this.opponents = opponents;
        playerKartChoices = kartChoices;

    }
}
