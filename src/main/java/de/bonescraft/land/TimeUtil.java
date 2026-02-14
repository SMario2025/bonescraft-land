package de.bonescraft.land;

public class TimeUtil {
    public static String formatDuration(long seconds) {
        long s = seconds;
        long days = s / 86400; s %= 86400;
        long hours = s / 3600; s %= 3600;
        long mins = s / 60; s %= 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        if (mins > 0 || hours > 0 || days > 0) sb.append(mins).append("m ");
        sb.append(s).append("s");
        return sb.toString().trim();
    }
}
