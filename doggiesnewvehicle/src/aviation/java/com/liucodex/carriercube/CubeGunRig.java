package com.liucodex.carriercube;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class CubeGunRig {
    static final Vec3 PIVOT;
    static final Vec3 TRAVERSE_AXIS;
    static final Vec3 MUZZLE;
    static {
        try(var in=Objects.requireNonNull(CubeGunRig.class.getResourceAsStream("/data/dnvcarriercube/gun_sockets.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)) {
            JsonObject sockets=JsonParser.parseReader(reader).getAsJsonObject();
            TRAVERSE_AXIS=socket(sockets,"traverseAxis");
            PIVOT=socket(sockets,"pivot");
            MUZZLE=socket(sockets,"muzzle");
        } catch(java.io.IOException e) {throw new ExceptionInInitializerError(e);}
    }
    private static Vec3 socket(JsonObject data,String key) {
        var p=data.getAsJsonArray(key);
        return new Vec3(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());
    }
    private CubeGunRig() {}
}
