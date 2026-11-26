package ru.nsu.p2p.ui.format;

public final class DataSizeFormatters {
    private static final long BYTES_PER_KIB = 1024;
    private static final long BYTES_PER_MIB = BYTES_PER_KIB * BYTES_PER_KIB;

    private DataSizeFormatters() {
    }

    public static String formatMiBValue(long bytes) {
        return String.format("%.3f", toMiB(bytes));
    }

    public static String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond < BYTES_PER_KIB) {
            return bytesPerSecond + " Б/с";
        }

        if (bytesPerSecond < BYTES_PER_MIB) {
            return String.format(
                    "%.1f КиБ/с", (double) bytesPerSecond / BYTES_PER_KIB
            );
        }

        return String.format("%.1f МиБ/с", toMiB(bytesPerSecond));
    }

    private static double toMiB(long bytes) {
        return (double) bytes / BYTES_PER_MIB;
    }
}