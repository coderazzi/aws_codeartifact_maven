package net.coderazzi.aws_codeartifact_maven.settings;

import java.util.List;
import org.jetbrains.annotations.NotNull;

public record RefreshOption(String label, long minutes) {

    public static final RefreshOption DISABLED = new RefreshOption("Never", 0);
    public static final List<RefreshOption> REFRESH_OPTIONS = List.of(
            DISABLED,
            new RefreshOption("Every 15 minutes", 15),
            new RefreshOption("Every hour", 60),
            new RefreshOption("Every 2 hours", 120),
            new RefreshOption("Every 3 hours", 180),
            new RefreshOption("Every 6 hours", 360),
            new RefreshOption("Every 12 hours", 720)
    );

    public static @NotNull RefreshOption ofMinutes(long minutes) {
        return REFRESH_OPTIONS.stream()
                .filter(option -> option.minutes == minutes)
                .findFirst()
                .orElseGet(() -> new RefreshOption("Every %s minutes".formatted(minutes), minutes));
    }

    @Override
    public String toString() {
        return label;
    }
}
