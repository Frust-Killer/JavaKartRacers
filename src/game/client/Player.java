package game.client;

/**
 * The {@code Player} class is identifies each player
 * and their kart.
 * This can eventually hold more information
 * like scores and other statistics.
 */
public class Player {

    // Object properties.
    protected Kart kart;
    protected int playerNumber;
    private String name = "";
    private int wins = 0;

    // Property access methods.
    public int getPlayerNumber()    { return playerNumber; }
    public Kart getKart()           { return kart; }
    public void setKart(Kart kart)  { this.kart = kart; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    // Constructor.
    public Player(int playerNumber) {
        this.playerNumber = playerNumber;
    }
}