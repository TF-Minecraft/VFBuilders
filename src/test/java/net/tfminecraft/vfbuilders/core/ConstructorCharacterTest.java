package net.tfminecraft.vfbuilders.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.tlibs.character.*;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class ConstructorCharacterTest {
  ServerMock server;

  @BeforeEach
  void setup() {
    server = MockBukkit.mock();
    ConstructorCharacter.available = null;
  }

  @AfterEach
  void teardown() {
    ConstructorCharacter.available = null;
    MockBukkit.unmock();
  }

  @Test
  void currentSessionNamesTheCharacterAndStaleOrMissingSessionsNameNone() {
    var player = server.addPlayer();
    assertNull(ConstructorCharacter.of(player));
    assertTrue(ConstructorCharacter.available);

    var character = UUID.randomUUID();
    var session = new CharacterSession(new CharacterRef(player.getUniqueId(), character), 4L);
    var identity = mock(CharacterIdentity.class);
    when(identity.currentSession(player)).thenReturn(Optional.of(session));
    when(identity.isCurrent(session)).thenReturn(true);
    server
        .getServicesManager()
        .register(
            CharacterIdentity.class,
            identity,
            MockBukkit.createMockPlugin(),
            ServicePriority.Normal);
    assertEquals(character, ConstructorCharacter.of(player));
    when(identity.isCurrent(session)).thenReturn(false);
    assertNull(ConstructorCharacter.of(player));
    when(identity.currentSession(player)).thenReturn(Optional.empty());
    assertNull(ConstructorCharacter.of(player));
  }

  @Test
  void olderTlibsWithoutTheSessionApiRecordsNoCharacter() {
    assertTrue(ConstructorCharacter.probe(ConstructorCharacter.API));
    assertFalse(ConstructorCharacter.probe("net.tfminecraft.tlibs.character.Missing"));
    // An older Characters class without the session methods.
    assertFalse(ConstructorCharacter.probe(String.class.getName()));

    var player = server.addPlayer();
    var identity = mock(CharacterIdentity.class);
    server
        .getServicesManager()
        .register(
            CharacterIdentity.class,
            identity,
            MockBukkit.createMockPlugin(),
            ServicePriority.Normal);
    ConstructorCharacter.available = false;
    assertNull(ConstructorCharacter.of(player));
    verifyNoInteractions(identity);
  }
}
