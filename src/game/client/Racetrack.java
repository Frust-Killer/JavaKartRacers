package game.client;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The {@code Racetrack} class sets up the racetrack information
 * needed for the game.
 */
public class Racetrack {

    // Lines that need to be crossed by the kart to progress the race.
    private final List<Rectangle> checkpoints = new ArrayList<>();
    private final Rectangle checkpoint1 = new Rectangle(700,350,100,1);
    private final Rectangle checkpoint2 = new Rectangle(425,100,1,100);
    private final Rectangle checkpoint3 = new Rectangle(50,350,100,1);
    private final Rectangle finishLine = new Rectangle(425,500,1,100);

    //Inner bounds, only used to determine if the kart is going the wrong direction, not for collision detection.
    private final Rectangle innerBoundsBottom = new Rectangle(166,499,518,1);
    private final Rectangle innerBoundsRight = new Rectangle(699,216,1,271);
    private final Rectangle innerBoundsTop = new Rectangle(166,200,518,1);
    private final Rectangle innerBoundsLeft = new Rectangle(150,216,1,271);

    // The drivable track, contains central area to be cut out in the constructor.
    private final Area playableArea = new Area(new Polygon(
            new int[] {50, 50, 800, 800},
            new int[] {100, 600, 600, 100},
            4));

    // Object properties.
    private int startDirection;
    private ImageIcon image;
    private final Point[] startPositions = new Point[6];

    // Property access methods.
    public ImageIcon getImage()                     { return image; }
    public List<Rectangle> getCheckpoints()         { return checkpoints; }
    public Area getPlayableArea()                   { return playableArea; }
    public int getStartDirection()                  { return startDirection; }
    public Point getStartPosition(int playerNumber) { return startPositions[playerNumber-1]; }
    public Rectangle getInnerBounds(int side) {
        return switch (side) {
            case 0 -> innerBoundsBottom;
            case 1 -> innerBoundsRight;
            case 2 -> innerBoundsTop;
            case 3 -> innerBoundsLeft;
            default -> throw new IllegalStateException("Unrecognised inner-boundary side");
        };
    }

    // Constructor.
    public Racetrack(int racetrackOption) {

        // Cut the inner area away from the drivable track.
        Area innerBoundaryArea = new Area(new Polygon(
                new int[] { 150, 150, 166, 684, 699, 699, 684, 166 },
                new int[] { 216, 487, 499, 499, 487, 216, 200, 200 },
                8));
        playableArea.subtract(innerBoundaryArea);

        loadImages(racetrackOption);

        checkpoints.add(checkpoint1);
        checkpoints.add(checkpoint2);
        checkpoints.add(checkpoint3);
        checkpoints.add(finishLine);

        setupStartPositions();
    }

    private void loadImages(int type) {
        try {
            image = new ImageIcon(Objects.requireNonNull(getClass().getResource("images/racetrack/racetrack" + type + ".png")));
        }
        catch (NullPointerException e) {
            System.err.println("Failed to locate a necessary image file.");
        }
    }

    private void setupStartPositions() {
        startDirection = 4;
        startPositions[0] = new Point(365,495);
        startPositions[1] = new Point(startPositions[0].x,startPositions[0].y + 50);
        startPositions[2] = new Point(startPositions[0].x - 54,startPositions[0].y);
        startPositions[3] = new Point(startPositions[2].x,startPositions[1].y);
        startPositions[4] = new Point(startPositions[2].x - 54,startPositions[0].y);
        startPositions[5] = new Point(startPositions[4].x,startPositions[1].y);
    }
}
