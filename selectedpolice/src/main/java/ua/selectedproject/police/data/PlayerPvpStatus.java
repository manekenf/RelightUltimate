package ua.selectedproject.police.data;

import java.time.Instant;
import java.util.UUID;

public record PlayerPvpStatus(
        UUID playerUuid,
        boolean isPvp,
        Instant lastToggle,
        boolean isCriminal,
        Instant criminalUntil,  // null if not criminal or no timer set
        boolean isBound,
        Instant boundUntil,  // null if not bound
        UUID boundBy,        // officer UUID who bound them, null if not bound
        boolean isCaught,
        UUID caughtBy,       // officer UUID, null if not caught
        boolean isLeashed,
        UUID leashedTo,      // officer UUID, null if not leashed
        String spawnWorld,   // prison spawn world, null if not set
        double spawnX,
        double spawnY,
        double spawnZ
) {}
