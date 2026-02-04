package game.client;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.util.*;

/**
 * The {@code Kart} class controls updating and processing
 * of kart information.
 */
public class Kart {

    // Constants.
    private static final float SPEED_MAX    = 2f;
    private static final int SPEED_MIN      = 0;
    private static final float ACCELERATION = 0.1f;
    private static final float TURN_SPEED   = 1.5f;
    private static final int TURN_CIRCLE    = 160;
    private static final int HIT_BOX_BUFFER = 15;
    private static final float SLOW_RATE    = 0.025f;
    private static final int BOTTOM         = 0;
    private static final int RIGHT          = 1;
    private static final int TOP            = 2;
    private static final int LEFT           = 3;

    // Image sets.
    private final ImageIcon[] kartSprites = new ImageIcon[16];

    // Object properties.
    private final int kartNumber;
    private int direction;
    private float rotation;
    private float speed;
    private final Point2D.Float position = new Point2D.Float();
    private int kartType;
    private ImageIcon image;
    private final Player owner;
    private final Rectangle hitBox;
    private final Racetrack racetrack;
    private final Area track;
    private boolean kartCrashed;
    private boolean boundaryCollisionSoundPlayed = false;
    // Collision visual and slow state
    private long slowUntil = 0;
    private long flashStart = 0;
    public static final long COLLISION_EFFECT_MS = 5000; // 5 seconds (made public for UI)

    // store original speed to use for recovery curve
    private float savedOriginalSpeed = 0f;

    // Pending scheduled collision start (grace period)
    private long pendingCollisionStart = 0;

    // Slip (banana) state
    private long slipUntil = 0;
    private long slipStart = 0;
    private float slipSavedSpeed = 0f;
    private float slipSpinRate = 8f; // degrees per update

    // Nitro (boost) state
    private float nitroCapacity = 100f; // starts full (percentage)
    private boolean nitroActive = false;
    private boolean nitroDepleted = false; // when true, nitro won't recharge automatically
    private static final float NITRO_DEPLETION_RATE = 40f; // percent per second
    private static final float NITRO_RECHARGE_RATE = 10f; // percent per second when not in use
    private static final float NITRO_BOOST_MULTIPLIER = 1.8f; // speed multiplier during nitro
    private long lastNitroUpdate = System.currentTimeMillis();

    // Property access methods.
    public boolean isFlashing() { return System.currentTimeMillis() < slowUntil; }
    public long getFlashStart() { return flashStart; }
    public boolean isSlipping() { return System.currentTimeMillis() < slipUntil; }
    public long getSlipUntil() { return slipUntil; }
    public float getNitroCapacity() { return nitroCapacity; }
    public boolean isNitroActive() { return nitroActive; }
    public Player getOwner()            { return owner; }
    public float getSpeed()             { return speed; }
    public float getRotation()          { return rotation; }
    public int getKartNumber()          { return kartNumber; }
    public ImageIcon getImage()         { return image; }
    public Rectangle getHitBox()        { return hitBox; }
    public Point2D.Float getPosition()  { return position; }
    public boolean hasCrashed()         { return kartCrashed; }
    public long getSlowUntil()          { return slowUntil; }
    public long getPendingCollisionStart() { return pendingCollisionStart; }

    public void setRotation(float newRotation) {
        rotation = newRotation;
    }

    public void setSpeed(float newSpeed) {
        speed = newSpeed;
    }

    public void setPosition(float x, float y) {
        position.x = x;
        position.y = y;
    }

    public boolean isMoving() {
        return speed != SPEED_MIN;
    }

    public void reduceSpeed() {
        if (speed < SPEED_MIN - SLOW_RATE) speed += SLOW_RATE;
        else if (speed > SPEED_MIN + SLOW_RATE) speed -= SLOW_RATE;
        else speed = SPEED_MIN;
    }

    // Constructor.
    public Kart(int startDirection, Point startPosition, Player owner, int kartType, Racetrack racetrack) {
        this.racetrack = racetrack;
        track = racetrack.getPlayableArea();
        this.kartType = kartType;

        loadImages();

        // Allocate kart information.
        this.owner  = owner;
        kartNumber  = owner.getPlayerNumber();
        direction   = startDirection;
        rotation    = (float) direction * 10;
        image       = kartSprites[direction];
        position.setLocation(startPosition);

        // Create a forgiving kart bounds area that is smaller than the
        // image for more accurate collision detection and leniency.
        hitBox = new Rectangle((int) (position.x + HIT_BOX_BUFFER), (int) (position.y + HIT_BOX_BUFFER),
                image.getIconWidth() - HIT_BOX_BUFFER * 2, image.getIconHeight() - HIT_BOX_BUFFER * 2);
    }

    private void loadImages() {
        try {
            Arrays.setAll(kartSprites, i -> new ImageIcon(
                    Objects.requireNonNull(getClass().getResource("images/kart/style" + kartType + "/kart" + i + ".png"))));
        }
        catch (NullPointerException e) {
            System.err.println("Failed to locate a necessary image file.");
        }
    }

    public void updatePosition() {
        // Compute movement using direction multipliers and current speed
        // Update nitro depletion/recharge first
        long now = System.currentTimeMillis();
        float deltaSec = (now - lastNitroUpdate) / 1000f;
        lastNitroUpdate = now;
        if (nitroActive) {
            nitroCapacity -= NITRO_DEPLETION_RATE * deltaSec;
            if (nitroCapacity <= 0f) {
                nitroCapacity = 0f;
                nitroActive = false; // stop when empty
                nitroDepleted = true; // mark as depleted so it doesn't auto-recharge
            }
        } else {
            // recharge slowly when not active, but only if not depleted
            if (!nitroDepleted) {
                nitroCapacity += NITRO_RECHARGE_RATE * deltaSec;
                if (nitroCapacity > 100f) nitroCapacity = 100f;
            }
        }

        // If nitro is active, temporarily increase effective movement
        float effectiveSpeed = speed;
        if (nitroActive) effectiveSpeed = speed * NITRO_BOOST_MULTIPLIER;

        // If nitro is active and the kart is stationary, give a small initial push
        if (nitroActive && speed <= 0f) {
            // apply a small instantaneous bump so nitro is noticeable even when not accelerating
            speed = Math.min(SPEED_MAX, ACCELERATION * 6f);
            effectiveSpeed = speed * NITRO_BOOST_MULTIPLIER;
        }

        float deltaX = getDirectionMultiplierX() * effectiveSpeed;
        float deltaY = getDirectionMultiplierY() * effectiveSpeed;
        float newPositionX = position.x + deltaX;
        float newPositionY = position.y + deltaY;

        hitBox.setLocation((int) newPositionX + HIT_BOX_BUFFER,(int) newPositionY + HIT_BOX_BUFFER);

        // If a scheduled collision start time has arrived, start the collision effect
        if (pendingCollisionStart > 0 && now >= pendingCollisionStart) {
            // Start collision effect now using the scheduled start and savedOriginalSpeed
            onKartCollision(pendingCollisionStart, savedOriginalSpeed);
            pendingCollisionStart = 0;
        }

        // Collision detection with track boundaries.
        if (isNewPositionOnTrack()) {
            kartCrashed = false;
            boundaryCollisionSoundPlayed = false; // reset boundary collision sound once we're back on track
             // If currently slowed due to collision, apply recovery curve based on savedOriginalSpeed
             if (now < slowUntil) {
                long elapsed = now - flashStart;
                float progress = (float) elapsed / (float) COLLISION_EFFECT_MS;
                if (progress < 0f) progress = 0f;
                if (progress > 1f) progress = 1f;
                float effectiveSpeed2 = savedOriginalSpeed * progress;
                // Move according to direction and effective speed (don't use current speed which is set to 0 on collision)
                position.setLocation(position.x + (getDirectionMultiplierX() * effectiveSpeed2), position.y + (getDirectionMultiplierY() * effectiveSpeed2));
                // continue without changing the stored speed directly
             } else {
                position.setLocation(position.x + (getDirectionMultiplierX() * effectiveSpeed), position.y + (getDirectionMultiplierY() * effectiveSpeed));
             }
         }

        // If slipping due to banana, override rotation and movement accordingly
        if (now < slipUntil) {
            // spin the kart visually
            rotation = (rotation + slipSpinRate) % TURN_CIRCLE;
            // move according to the current (saved) speed but allow it to collide with walls
            float slipMultiplier = 0.9f; // slightly reduced control
            float slipDeltaX = getDirectionMultiplierX() * (slipSavedSpeed * slipMultiplier);
            float slipDeltaY = getDirectionMultiplierY() * (slipSavedSpeed * slipMultiplier);
            position.setLocation(position.x + slipDeltaX, position.y + slipDeltaY);
            // Play slip sound once at start
            if (now - slipStart < 200) {
                AudioManager.playSound("SLIP_SOUND", false);
            }
        }

        // Stop any collision sound effects when both effects are finished
        if (now >= slowUntil && now >= slipUntil) {
            AudioManager.stopSoundEffect();
        }
    }

    public boolean isNewPositionOnTrack() {
        if (track.contains(hitBox)) return true;

        kartCrashed = true;
        speed = -0.5f; // Bounce off the boundary.
        // Play collision sound only once per boundary collision until we re-enter the track
        if (!boundaryCollisionSoundPlayed) {
            AudioManager.playSound("KART_COLLISION", false);
            boundaryCollisionSoundPlayed = true;
        }
        return false;
    }

    // Direction multipliers (unit multipliers independent of speed)
    private float getDirectionMultiplierX() {
        return switch (direction) {
            case 2,3,4,5,6      ->  1;      // Fast movement right
            case 1,7            ->  0.5f;   // Slow movement right
            case 0,8            ->  0;      // No horizontal movement
            case 9,15           -> -0.5f;   // Slow movement left
            case 10,11,12,13,14 -> -1;      // Fast movement left
            default -> 0;
        };
    }

    private float getDirectionMultiplierY() {
        return switch (direction) {
            case 6,7,8,9,10     ->  1;      // Fast movement down
            case 5,11           ->  0.5f;   // Slow movement down
            case 4,12           ->  0;      // No vertical movement
            case 3,13           -> -0.5f;   // Slow movement up
            case 0,1,2,14,15    -> -1;      // Fast movement up
            default -> 0;
        };
    }

    public void updateRotation(int rotationDirection) {
        final int right = 0;
        final int left = 1;

        // Modulus is used to ensure rotation loops back around from max to min.
        rotation = switch (rotationDirection) {
            case right -> (rotation + TURN_SPEED) % TURN_CIRCLE;
            case left -> (rotation + (TURN_CIRCLE - TURN_SPEED)) % TURN_CIRCLE;
            default -> throw new IllegalStateException("Unrecognised direction: " + rotationDirection);
        };
        updateImage();
    }

    public void updateImage() {
        direction = (int) rotation / 10;
        image = kartSprites[direction];
    }

    // Called when this kart collides with another kart
    public void onKartCollision() {
        // Use more dramatic recovery: store original speed and recover from 0 -> original over time
        onKartCollision(System.currentTimeMillis(), this.speed);
    }

    // New overload to allow synchronized collisions from server with supplied timestamp and original speed
    public void onKartCollision(long flashStartTimestamp, float originalSpeed) {
        this.slowUntil = flashStartTimestamp + COLLISION_EFFECT_MS;
        this.flashStart = flashStartTimestamp;
        this.savedOriginalSpeed = originalSpeed;
        // Temporarily set the current speed to 0; movement will be handled via recovery curve in updatePosition
        this.speed = 0f;
        AudioManager.playSound("KART_COLLISION", false);
    }

    // Schedule a collision effect to start at a future timestamp (grace period)
    public void scheduleCollision(long startTimestamp, float originalSpeed) {
        this.pendingCollisionStart = startTimestamp;
        this.savedOriginalSpeed = originalSpeed;
    }

    // Start a slip effect (banana) immediately
    public void startSlip(long now, long durationMs) {
        this.slipStart = now;
        this.slipUntil = now + durationMs;
        this.slipSavedSpeed = this.speed;
        // optionally set a spin rate based on current speed
        this.slipSpinRate = 6f + Math.min(10f, Math.abs(this.slipSavedSpeed) * 10f);
    }

    // Apply a small bounce displacement to the kart (used immediately on collision)
    public void applyBounce(float dx, float dy) {
        // Displace position a bit, ensuring we remain within track bounds
        position.x += dx;
        position.y += dy;
        hitBox.setLocation((int) position.x + HIT_BOX_BUFFER, (int) position.y + HIT_BOX_BUFFER);
    }

    // Called by player input to brake quickly
    public void applyBrake() {
        // Strong deceleration
        speed -= ACCELERATION * 2;
        if (speed < SPEED_MIN) speed = SPEED_MIN;
    }

    // Nitro control
    public void startNitro() {
        if (!nitroDepleted && nitroCapacity > 5f) {
            nitroActive = true;
            AudioManager.playSound("NITRO_SOUND", false);
            lastNitroUpdate = System.currentTimeMillis();
            // Give a small initial boost if currently stopped so the player notices the effect
            if (speed <= 0f) {
                speed = Math.min(SPEED_MAX, ACCELERATION * 6f);
            }
        }
    }

    public void stopNitro() {
        nitroActive = false;
        lastNitroUpdate = System.currentTimeMillis();
    }

    // Allow external reset (e.g., new race)
    public void resetNitro() {
        nitroCapacity = 100f;
        nitroDepleted = false;
        nitroActive = false;
        lastNitroUpdate = System.currentTimeMillis();
    }

    public void updateSpeed(int speedDirection) {
        speed += speedDirection * ACCELERATION;
        // If the new speed breaches the bounds, return it back within.
        if (speed > SPEED_MAX) speed = SPEED_MAX;
        else if (speed < SPEED_MIN) speed = SPEED_MIN;
    }

    public boolean isGoingWrongWay() {
        if (isOnBottomTrack() && isFacingLeft()) return true;
        else if (isOnRightTrack() && isFacingDown()) return true;
        else if (isOnTopTrack() && isFacingRight()) return true;
        else return isOnLeftTrack() && isFacingUp();
    }

    private boolean isFacingLeft() {
        return direction <= 15 && direction >= 9;
    }

    private boolean isFacingDown() {
        return direction <= 11 && direction >= 5;
    }

    private boolean isFacingRight() {
        return direction <= 7 && direction >= 1;
    }

    private boolean isFacingUp() {
        return (direction <= 3 && direction >= 0) || (direction <= 15 && direction >= 13);
    }

    private boolean isOnBottomTrack() {
        return position.y + HIT_BOX_BUFFER >= racetrack.getInnerBounds(BOTTOM).y;
    }

    private boolean isOnRightTrack() {
        return position.x + HIT_BOX_BUFFER >= racetrack.getInnerBounds(RIGHT).x;
    }

    private boolean isOnTopTrack() {
        return position.y - HIT_BOX_BUFFER <= racetrack.getInnerBounds(TOP).y;
    }

    private boolean isOnLeftTrack() {
        return position.x - HIT_BOX_BUFFER <= racetrack.getInnerBounds(LEFT).x;
    }
}
