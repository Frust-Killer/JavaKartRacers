package game.client;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * The {@code GameLobbyDisplay} class is a concrete implementation
 * of {@code Display} for setting up the options for the game.
 * From here, a user can:
 * <ul>
 * <li>Choose their kart for the game.
 * <li>Choose the map for the game.
 * <li>Select when they are ready to start the game.
 * <li>Return to the menu.
 * </ul>
 */
public class GameLobbyDisplay implements Display {

    // Constants.
    private static final int RIGHT  = 0;
    private static final int LEFT   = 1;

    // Buttons.
    private JButton buttonBack;
    private JButton buttonReady;
    private JButton buttonPlayerLeft;
    private JButton buttonPlayerRight;
    private JButton buttonMapLeft;
    private JButton buttonMapRight;

    // Image sets.
    private final ImageIcon[] allOptionsKart = new ImageIcon[7];
    private final ImageIcon[] allOptionsMap = new ImageIcon[4];
    private final ImageIcon[] allPlayerLabelsActive = new ImageIcon[6];
    private final ImageIcon[] allPlayerLabelsInactive = new ImageIcon[6];
    private final ImageIcon[] allPlayerLabelsCurrent = new ImageIcon[6];
    private final ImageIcon[] allDisplayedPlayerLabels = new ImageIcon[6];
    private final ImageIcon[] allDisplayedPlayerKarts = new ImageIcon[6];
    private final ImageIcon[] allDisplayedPlayerReadyStates = new ImageIcon[6];

    // Images.
    private ImageIcon imageLobbyBackground;
    private ImageIcon imageInactiveKart;
    private ImageIcon imageArrowLeft;
    private ImageIcon imageArrowRight;
    private ImageIcon imageBack;
    private ImageIcon imageReady;
    private ImageIcon imageUnready;
    private ImageIcon imageMapLabel;
    private ImageIcon imageMap;
    private ImageIcon imageSymbolReady;
    private ImageIcon imageSymbolNotReady;
    private ImageIcon imageSymbolReadyHidden;

    // Object properties.
    private int playerNumber;
    private int playerSelectedKart;
    private int selectedMap;
    private boolean isBadWeather;
    private boolean isPlayerReady;
    private String localPlayerName = "";
    private int localPlayerWins = 0;

    private final ServerHandler connection = ServerManager.getHandler();
    private final Map<Integer, String> opponentNames = new HashMap<>();
    private final Map<Integer, Integer> opponentWins = new HashMap<>();

    // Constructor.
    public GameLobbyDisplay() {
        baseDisplay.clearComponents();
        loadImages();
    }

    private void loadImages() {
        try {
            Arrays.setAll(allOptionsKart, i -> new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("images/kart/kartOption" + i + ".png"))));
            Arrays.setAll(allOptionsMap, i -> new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("images/racetrack/mapOption" + i + ".png"))));
            Arrays.setAll(allPlayerLabelsActive, i -> new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("images/ui/p" + (i + 1) + "Active.png"))));
            Arrays.setAll(allPlayerLabelsInactive, i -> new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("images/ui/p" + (i + 1) + "Inactive.png"))));
            Arrays.setAll(allPlayerLabelsCurrent, i -> new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("images/ui/p" + (i + 1) + "Current.png"))));

            imageLobbyBackground = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/bg/gameLobbyBackground.png")));
            imageArrowLeft = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/arrowLeft.png")));
            imageArrowRight = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/arrowRight.png")));
            imageBack = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/buttonBack.png")));
            imageReady = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/playerReady.png")));
            imageUnready = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/playerUnready.png")));
            imageSymbolReady = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/readySymbol.png")));
            imageSymbolNotReady = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/notReadySymbol.png")));
            imageSymbolReadyHidden = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/hiddenSymbol.png")));
            imageMapLabel = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/map.png")));
            imageInactiveKart = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/ui/inactiveKart.png")));

            Arrays.setAll(allDisplayedPlayerLabels, i -> allDisplayedPlayerLabels[i] = allPlayerLabelsInactive[i]);
            Arrays.setAll(allDisplayedPlayerKarts, i -> allDisplayedPlayerKarts[i] = imageInactiveKart);
            Arrays.setAll(allDisplayedPlayerReadyStates, i -> allDisplayedPlayerReadyStates[i] = imageSymbolReadyHidden);
        }
        catch (NullPointerException e) {
            System.err.println("Failed to locate a necessary image file.");
        }

    }

    public void prepareLobbyForPlayer() {
        collectPlayerValues();
        if (playerNumber <= 0 || playerNumber > 6) {
            System.err.println("GameLobbyDisplay: invalid playerNumber=" + playerNumber + ", aborting prepareLobbyForPlayer()");
            return;
        }
        displayPlayerValues();
        addDisplayComponents();
    }

    private void collectPlayerValues() {
        isPlayerReady = false;
        playerNumber = connection.getPlayerNumber();
        // Sanity-check playerNumber
        if (playerNumber <= 0 || playerNumber > 6) {
            System.err.println("GameLobbyDisplay.collectPlayerValues: invalid playerNumber=" + playerNumber);
            playerSelectedKart = 0;
            selectedMap = 0;
            return;
        }
        playerSelectedKart = connection.getKartChoice();
        if (playerSelectedKart < 0 || playerSelectedKart >= allOptionsKart.length) playerSelectedKart = 0;
        selectedMap = connection.getMapChoice();
        if (selectedMap < 0 || selectedMap >= allOptionsMap.length) selectedMap = 0;
    }

    private void displayPlayerValues() {
        int playerIndex = playerNumber - 1;
        if (playerIndex < 0 || playerIndex >= allDisplayedPlayerKarts.length) {
            System.err.println("displayPlayerValues: invalid playerIndex=" + playerIndex);
            return;
        }
        allDisplayedPlayerKarts[playerIndex] = allOptionsKart[playerSelectedKart];
        allDisplayedPlayerLabels[playerIndex] = allPlayerLabelsCurrent[playerIndex];
        allDisplayedPlayerReadyStates[playerIndex] = imageSymbolNotReady;
        imageMap = allOptionsMap[selectedMap];
    }

    private void addDisplayComponents() {
        buttonBack = baseDisplay.addButton(imageBack, 20, 37);
        buttonReady = baseDisplay.addButton(imageReady, 704, 37);

        // Guard placement of player arrow buttons
        int leftX = getLeftArrowX(playerNumber);
        int rightX = getRightArrowX(playerNumber);
        int arrowY = getArrowY(playerNumber);
        buttonPlayerLeft = baseDisplay.addButton(imageArrowLeft, leftX, arrowY);
        buttonPlayerRight = baseDisplay.addButton(imageArrowRight, rightX, arrowY);

        buttonMapLeft = baseDisplay.addButton(imageArrowLeft, 617,143);
        buttonMapRight = baseDisplay.addButton(imageArrowRight, 781,143);
    }

    @Override
    public void update(Graphics g) {
        imageLobbyBackground.paintIcon(baseDisplay, g, 0,0);
        drawMapChoice(g);
        drawPlayerKartChoices(g);
        drawPlayerLabels(g);
        drawPlayerReadyStatus(g);
        drawOpponentNames(g);

        // Draw local player info (name and wins) at top-right
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString("Player: " + localPlayerName, 570, 40);
        g.drawString("Wins: " + localPlayerWins, 570, 60);
    }

    public void setOpponentInfo(int playerNumber, String username, int wins) {
        if (playerNumber <= 0 || playerNumber > 6) {
            System.err.println("setOpponentInfo: invalid playerNumber=" + playerNumber);
            return;
        }
        opponentNames.put(playerNumber, username);
        opponentWins.put(playerNumber, wins);
    }

    public void updateOpponentKartChoice(int opponentNumber, int kartChoice) {
        if (opponentNumber <= 0 || opponentNumber > 6) {
            System.err.println("updateOpponentKartChoice: invalid opponentNumber=" + opponentNumber);
            return;
        }
        if (kartChoice < 0 || kartChoice >= allOptionsKart.length) {
            System.err.println("updateOpponentKartChoice: invalid kartChoice=" + kartChoice + " for opponent=" + opponentNumber);
            return;
        }
        int playerIndex = opponentNumber - 1;
        allDisplayedPlayerKarts[playerIndex] = allOptionsKart[kartChoice];
    }

    private void drawOpponentNames(Graphics g) {
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        // Draw small name near each player label
        for (int i = 1; i <= 6; i++) {
            String name = opponentNames.getOrDefault(i, "");
            if (!name.isEmpty()) {
                int x = switch (i) { case 1 -> 50; case 2 -> 241; case 3 -> 434; case 4 -> 48; case 5 -> 241; default -> 434; };
                int y = switch (i) { case 1,2,3 -> 220; default -> 498; };
                g.drawString(name, x, y);
            }
        }
    }

    public void updateOpponentReadyState(int opponentNumber, boolean isReady) {
        if (opponentNumber <= 0 || opponentNumber > 6) {
            System.err.println("updateOpponentReadyState: invalid opponentNumber=" + opponentNumber);
            return;
        }
        int playerIndex = opponentNumber - 1;
        allDisplayedPlayerReadyStates[playerIndex] = (isReady) ? imageSymbolReady : imageSymbolNotReady;
    }

    public void updateSelectedMap(int selectedMap) {
        imageMap = allOptionsMap[selectedMap];
    }

    public void updateWeather(boolean weather) {
        isBadWeather = weather;
    }

    public void updateActiveOpponent(int opponentNumber) {
        if (opponentNumber <= 0 || opponentNumber > 6) {
            System.err.println("updateActiveOpponent: invalid opponentNumber=" + opponentNumber);
            return;
        }
        int playerIndex = opponentNumber - 1;
        allDisplayedPlayerLabels[playerIndex] = allPlayerLabelsActive[playerIndex];
    }

    public void updateInactiveOpponent(int opponentNumber) {
        if (opponentNumber <= 0 || opponentNumber > 6) {
            System.err.println("updateInactiveOpponent: invalid opponentNumber=" + opponentNumber);
            return;
        }
        int playerIndex = opponentNumber - 1;
        allDisplayedPlayerLabels[playerIndex] = allPlayerLabelsInactive[playerIndex];
        allDisplayedPlayerReadyStates[playerIndex] = imageSymbolReadyHidden;
        allDisplayedPlayerKarts[playerIndex] = imageInactiveKart;
    }

    private void drawPlayerKartChoices(Graphics g) {
        allDisplayedPlayerKarts[0].paintIcon(baseDisplay, g, 0, 114);
        allDisplayedPlayerKarts[1].paintIcon(baseDisplay, g, 193, 114);
        allDisplayedPlayerKarts[2].paintIcon(baseDisplay, g, 386, 114);
        allDisplayedPlayerKarts[3].paintIcon(baseDisplay, g, 0, 392);
        allDisplayedPlayerKarts[4].paintIcon(baseDisplay, g, 193, 392);
        allDisplayedPlayerKarts[5].paintIcon(baseDisplay, g, 386, 392);
    }

    private void drawPlayerLabels(Graphics g) {
        allDisplayedPlayerLabels[0].paintIcon(baseDisplay, g, 50, 137);
        allDisplayedPlayerLabels[1].paintIcon(baseDisplay, g, 241, 137);
        allDisplayedPlayerLabels[2].paintIcon(baseDisplay, g, 434, 137);
        allDisplayedPlayerLabels[3].paintIcon(baseDisplay, g, 48, 415);
        allDisplayedPlayerLabels[4].paintIcon(baseDisplay, g, 241, 415);
        allDisplayedPlayerLabels[5].paintIcon(baseDisplay, g, 434, 415);
    }

    private void drawPlayerReadyStatus(Graphics g) {
        allDisplayedPlayerReadyStates[0].paintIcon(baseDisplay, g, 70, 334);
        allDisplayedPlayerReadyStates[1].paintIcon(baseDisplay, g, 263, 334);
        allDisplayedPlayerReadyStates[2].paintIcon(baseDisplay, g, 456, 334);
        allDisplayedPlayerReadyStates[3].paintIcon(baseDisplay, g, 70, 612);
        allDisplayedPlayerReadyStates[4].paintIcon(baseDisplay, g, 263, 612);
        allDisplayedPlayerReadyStates[5].paintIcon(baseDisplay, g, 456, 612);
    }

    private void drawMapChoice(Graphics g) {
        imageMap.paintIcon(baseDisplay, g, 580, 114);
        imageMapLabel.paintIcon(baseDisplay, g, 659, 138);
    }

    public void startGame() {
        // Create the information specific to this new game.
        ControlledPlayer mainPlayer = new ControlledPlayer(playerNumber);

        List<Player> opponents = createOpponents();
        int mapChoice = connection.getMapChoice();
        Map<Integer, Integer> kartChoices = connection.getKartChoices();

        // Package game information into an object to pass to the game creator.
        GameOptions options = new GameOptions(mapChoice, isBadWeather,
                mainPlayer, opponents, kartChoices);

        baseDisplay.setCurrentDisplay(new GameDisplay(new Game(options)));

        connection.clearLocalLobby();
    }

    private List<Player> createOpponents() {
        List<Player> opponents = new ArrayList<>();
        for (Integer opponentNumber : connection.getOpponents()) {
            if (opponentNumber == null) continue;
            if (opponentNumber <= 0 || opponentNumber > 6) {
                System.err.println("createOpponents: skipping invalid opponentNumber=" + opponentNumber);
                continue;
            }
            Player opponent = new Player(opponentNumber);
            // Populate opponent player info (name and wins) from the connection
            String name = connection.getOpponentName(opponentNumber);
            int wins = connection.getOpponentWins(opponentNumber);
            opponent.setName(name);
            opponent.setWins(wins);
            opponents.add(opponent);
        }
        return opponents;
    }

    public void sendPlayerToMenu() {
        connection.terminateConnection();
        baseDisplay.setCurrentDisplay(new MenuDisplay());
    }

    private void updateMapChoice(int direction) {
        selectedMap = selectNextItem(selectedMap, allOptionsMap.length, direction);
        imageMap = allOptionsMap[selectedMap];
        connection.sendMapChoice(selectedMap);
    }

    private void updateKartChoice(int direction) {
        int playerIndex = playerNumber - 1;

        playerSelectedKart = selectNextValidKart(playerSelectedKart, direction);
        allDisplayedPlayerKarts[playerIndex] = allOptionsKart[playerSelectedKart];
        connection.updateKartChoice(playerSelectedKart);
    }

    private int selectNextValidKart(int kartChoice, int direction) {
        int nextKart = selectNextItem(kartChoice, allOptionsKart.length, direction);

        if (connection.isKartChoiceTaken(nextKart)) {
            return selectNextValidKart(nextKart, direction);
        }
        return nextKart;
    }

    private int selectNextItem(int currentItem, int totalItems, int direction) {
        // Modulus is used to ensure item traversal loops back around to the start.
        return switch (direction) {
            case RIGHT  -> (currentItem + 1) % totalItems;
            case LEFT   -> (currentItem + (totalItems - 1)) % totalItems;
            default -> throw new IllegalStateException("Illegal traverse direction: " + direction);
        };
    }

    private void togglePlayerReadyState() {
        int playerIndex = playerNumber - 1;

        if (isPlayerReady) {
            // Allow the player to change their choice again.
            isPlayerReady = false;
            buttonReady.setIcon(imageReady);
            allDisplayedPlayerReadyStates[playerIndex] = imageSymbolNotReady;
            togglePlayerLockedButtonVisibility(true);
        }
        else {
            // Prevent the player from changing their choice.
            isPlayerReady = true;
            buttonReady.setIcon(imageUnready);
            allDisplayedPlayerReadyStates[playerIndex] = imageSymbolReady;
            togglePlayerLockedButtonVisibility(false);
        }

        connection.sendReadyState(isPlayerReady);
    }

    private void togglePlayerLockedButtonVisibility(boolean isVisible) {
        buttonPlayerLeft.setVisible(isVisible);
        buttonPlayerRight.setVisible(isVisible);
        buttonMapLeft.setVisible(isVisible);
        buttonMapRight.setVisible(isVisible);
    }

    private int getLeftArrowX(int playerNumber) {
        return switch (playerNumber) {
            case 1,4 -> 6;
            case 2,5 -> 199;
            case 3,6 -> 392;
            default -> 0;
        };
    }

    private int getRightArrowX(int playerNumber) {
        return switch (playerNumber) {
            case 1,4 -> 134;
            case 2,5 -> 327;
            case 3,6 -> 520;
            default -> 0;
        };
    }

    private int getArrowY(int playerNumber) {
        return switch (playerNumber) {
            case 1,2,3 -> 142;
            case 4,5,6 -> 420;
            default -> 0;
        };
    }

    @Override
    public void buttonHandler(Object button) {
        if (button == buttonReady) togglePlayerReadyState();
        else if (button == buttonBack) sendPlayerToMenu();
        else if (button == buttonPlayerRight) updateKartChoice(RIGHT);
        else if (button == buttonPlayerLeft) updateKartChoice(LEFT);
        else if (button == buttonMapRight) updateMapChoice(RIGHT);
        else if (button == buttonMapLeft) updateMapChoice(LEFT);
    }

    @Override
    public void keyHandler(int keyCode, boolean keyActivated) {
        // No keys used on this display.
    }

    public void setLocalPlayerInfo(String username, int wins) {
        if (username == null) username = "";
        this.localPlayerName = username;
        this.localPlayerWins = wins;
    }
}