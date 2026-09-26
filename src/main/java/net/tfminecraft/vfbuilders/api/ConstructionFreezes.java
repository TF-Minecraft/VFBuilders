package net.tfminecraft.vfbuilders.api;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Asks every registered {@link ConstructionFreeze} whether a project should hold. */
public final class ConstructionFreezes {

    private ConstructionFreezes() {
    }

    /** @return the first freeze reason for this constructor, or null when nothing pauses it */
    public static String reason(UUID constructorUuid) {
        if (constructorUuid == null) {
            return null;
        }
        for (RegisteredServiceProvider<ConstructionFreeze> registration
                : Bukkit.getServicesManager().getRegistrations(ConstructionFreeze.class)) {
            String reason;
            try {
                reason = registration.getProvider().freezeReason(constructorUuid);
            } catch (RuntimeException e) {
                // A broken provider must not stall every station on the server.
                continue;
            }
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }
}
