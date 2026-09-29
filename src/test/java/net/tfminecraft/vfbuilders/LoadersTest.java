package net.tfminecraft.vfbuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.ItemAPI;
import net.tfminecraft.vehicleframework.VFLogger;
import net.tfminecraft.vfbuilders.loaders.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;

class LoadersTest {
  @TempDir Path temp;

  @Test
  void yamlLoadersReadEntriesAndHandleMissingOrMalformedFiles() throws Exception {
    MockBukkit.mock();
    try (var libs = mockStatic(TLibs.class);
        var logger = mockStatic(VFLogger.class)) {
      libs.when(TLibs::getItemAPI).thenReturn(mock(ItemAPI.class, RETURNS_DEEP_STUBS));
      StationLoader.get().clear();
      CategoryLoader.get().clear();
      BlueprintLoader.get().clear();
      var file =
          Files.writeString(temp.resolve("config.yml"), "construction-max-distance: 12").toFile();
      new ConfigLoader().load(file);
      assertEquals(12, Cache.constructionDistance);
      Files.writeString(file.toPath(), "dock: {block: v.stone}\n");
      new StationLoader().load(file);
      assertEquals("v.stone", StationLoader.getByString("dock").getBlock());
      Files.writeString(file.toPath(), "boats: {station: dock}\n");
      new CategoryLoader().load(file);
      assertEquals("dock", CategoryLoader.getByString("boats").getStation().getId());
      Files.writeString(file.toPath(), "boat: {category: boats, time: 30}\n");
      new BlueprintLoader().load(file);
      assertEquals(30, BlueprintLoader.getByString("boat").getTime());
      for (String text : new String[] {"broken: [", ""}) {
        Files.writeString(file.toPath(), text);
        assertDoesNotThrow(() -> new ConfigLoader().load(file));
        assertDoesNotThrow(() -> new StationLoader().load(file));
        assertDoesNotThrow(() -> new CategoryLoader().load(file));
        assertDoesNotThrow(() -> new BlueprintLoader().load(file));
      }
      Files.delete(file.toPath());
      new ConfigLoader().load(file);
      new StationLoader().load(file);
      new CategoryLoader().load(file);
      new BlueprintLoader().load(file);
      assertEquals(8, Cache.constructionDistance);
    } finally {
      MockBukkit.unmock();
    }
  }
}
