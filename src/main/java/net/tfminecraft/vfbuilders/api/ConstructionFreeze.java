package net.tfminecraft.vfbuilders.api;

import java.util.UUID;

/**
 * Lets another plugin pause station countdowns. Register an implementation with
 * Bukkit's ServicesManager. Each second, registrations are asked in turn for every
 * station still counting down, stopping at the first one that returns a reason.
 */
public interface ConstructionFreeze {

    /**
     * @param constructorUuid account that started the project
     * @return why the project is paused, shown under the station timer, or null to keep counting down
     */
    String freezeReason(UUID constructorUuid);

    /**
     * Stations call this form. Override it to freeze by character; the default asks
     * {@link #freezeReason(UUID)} with the account.
     *
     * @param constructorUuid account that started the project
     * @param constructorCharacterId character the account was playing when the project started,
     *     or null when it had no current character session or the project predates the field
     * @return why the project is paused, or null to keep counting down
     */
    default String freezeReason(UUID constructorUuid, UUID constructorCharacterId) {
        return freezeReason(constructorUuid);
    }
}
