package com.liucodex.doggiesnewvehicle.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class DnvM1IconResourceTest {
    @Test
    void nativeVehicleIconUsesAnIndependentWhiteSilhouetteWithoutStretchingTheContainer() throws Exception {
        var data = JsonParser.parseString(Files.readString(Path.of(
                "src/artillery/resources/data/doggievehicle/sbw/vehicles/m1_yunlong.json"))).getAsJsonObject();
        String[] resource = data.get("VehicleIcon").getAsString().split(":", 2);
        Path killPath = Path.of("src/artillery/resources/assets", resource[0], resource[1]);
        var kill = ImageIO.read(killPath.toFile());
        assertNotNull(kill);
        assertEquals(256, kill.getWidth(), "The native kill feed uses a horizontal icon");
        assertEquals(64, kill.getHeight());
        int opaque = 0;
        int transparent = 0;
        for (int y = 0; y < kill.getHeight(); y++) {
            for (int x = 0; x < kill.getWidth(); x++) {
                int pixel = kill.getRGB(x, y);
                if ((pixel >>> 24) == 0) {
                    transparent++;
                } else {
                    opaque++;
                    assertEquals(0xffffff, pixel & 0xffffff, "Visible silhouette pixels must be white");
                    assertTrue(x >= 2 && x < 254 && y >= 2 && y < 62, "Keep transparent edge padding");
                }
            }
        }
        assertTrue(opaque > 1000 && transparent > 8000, "Reject empty or filled placeholder textures");
        Path containerPath = Path.of(
                "src/artillery/resources/assets/superbwarfare/textures/vehicle_icon/container/m1_yunlong.png");
        var container = ImageIO.read(containerPath.toFile());
        assertEquals(128, container.getWidth());
        assertEquals(128, container.getHeight());
        assertFalse(java.util.Arrays.equals(Files.readAllBytes(containerPath), Files.readAllBytes(killPath)));
    }
}
