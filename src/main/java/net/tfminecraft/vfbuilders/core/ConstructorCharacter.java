package net.tfminecraft.vfbuilders.core;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.tfminecraft.tlibs.character.Characters;

/**
 * The character a player builds as. TLibs' session API is probed on first use, so an older
 * TLibs records no character instead of failing every build. Main thread only.
 */
public final class ConstructorCharacter {

    static final String API = "net.tfminecraft.tlibs.character.Characters";
    static final String SESSION = "net.tfminecraft.tlibs.character.CharacterSession";

    static Boolean available;

    private ConstructorCharacter() {
    }

    /**
     * @return the player's current character, or null when the player has no current session
     *     or TLibs lacks the session API
     */
    public static UUID of(Player player) {
        if (available == null) {
            available = probe(API);
        }
        return available ? current(player) : null;
    }

    static boolean probe(String api) {
        try {
            Class<?> characters = Class.forName(api);
            characters.getMethod("currentSession", Player.class);
            characters.getMethod("isCurrent", Class.forName(SESSION));
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            Bukkit.getLogger().warning(
                "[VFBuilders] TLibs has no character session API; constructions record no character");
            return false;
        }
    }

    private static UUID current(Player player) {
        return Characters.currentSession(player)
            .filter(Characters::isCurrent)
            .map(session -> session.ref().characterId())
            .orElse(null);
    }
}
