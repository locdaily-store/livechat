package id.loclive.singleplayer;

/** Pure-Java eye-direction and eased movement math, independently testable without Minecraft. */
public final class LiveChatMotion {
    private static final double FOLLOW_FACTOR = 0.38;
    private static final double MAX_STEP_PER_TICK = 0.85;
    private static final double MIN_MOVE_SQUARED = 0.000225; // 1.5 cm

    private LiveChatMotion() {}

    public record Position(double x, double y, double z) {}

    /** Minecraft yaw: 0 means +Z; Minecraft positive pitch means looking down. */
    public static Position aim(double eyeX, double eyeY, double eyeZ,
                               double yawDegrees, double pitchDegrees, double distance) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(Math.max(-90, Math.min(90, pitchDegrees)));
        double horizontal = Math.cos(pitch) * distance;
        return new Position(
            eyeX - Math.sin(yaw) * horizontal,
            eyeY - Math.sin(pitch) * distance,
            eyeZ + Math.cos(yaw) * horizontal
        );
    }

    /** Exponential chase with a capped per-tick move, avoiding sudden jumps on quick turns. */
    public static Position ease(Position from, Position target) {
        double dx = target.x() - from.x();
        double dy = target.y() - from.y();
        double dz = target.z() - from.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 0.00001) return target;
        double move = Math.min(distance * FOLLOW_FACTOR, MAX_STEP_PER_TICK);
        double factor = move / distance;
        return new Position(
            from.x() + dx * factor,
            from.y() + dy * factor,
            from.z() + dz * factor
        );
    }

    /** Avoid pointless teleports for sub-centimeter changes. */
    public static boolean movedEnough(Position from, Position to) {
        double dx=to.x()-from.x(), dy=to.y()-from.y(), dz=to.z()-from.z();
        return dx * dx + dy * dy + dz * dz >= MIN_MOVE_SQUARED;
    }
}
