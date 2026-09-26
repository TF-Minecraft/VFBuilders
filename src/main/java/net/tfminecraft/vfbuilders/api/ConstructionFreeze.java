package net.tfminecraft.vfbuilders.api;

import java.util.UUID;

/**
 * Lets another plugin pause station countdowns. Register an implementation with
 * Bukkit's ServicesManager; every registration is asked once per station per second.
 */
public interface ConstructionFreeze {

    /**
     * @param constructorUuid player who started the project
     * @return why the project is paused, shown under the station timer, or null to keep counting down
     */
    String freezeReason(UUID constructorUuid);
}
