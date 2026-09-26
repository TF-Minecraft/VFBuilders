package net.tfminecraft.vfbuilders.api;

import java.util.UUID;

/**
 * Lets another plugin pause station countdowns. Register an implementation with
 * Bukkit's ServicesManager. Each second, registrations are asked in turn for every
 * station still counting down, stopping at the first one that returns a reason.
 */
public interface ConstructionFreeze {

    /**
     * @param constructorUuid player who started the project
     * @return why the project is paused, shown under the station timer, or null to keep counting down
     */
    String freezeReason(UUID constructorUuid);
}
