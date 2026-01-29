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

    // Property access methods.
    public int getPlayerNumber()    { return playerNumber; }
    public Kart getKart()           { return kart; }
    public void setKart(Kart kart)  { this.kart = kart; }

    // Constructor.
    public Player(int playerNumber) {
        this.playerNumber = playerNumber;
    }
}

