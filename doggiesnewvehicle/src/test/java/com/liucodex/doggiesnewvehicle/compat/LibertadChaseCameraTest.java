package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import net.minecraft.client.Camera;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Diagnostic of the released unobstructed helm orbit, using this MC line's actual Camera.move. */
class LibertadChaseCameraTest {
    @Test void nativeTailReadsVehicleOffsetThenClipsDistanceAndMovesCamera() throws Exception {
        try (var jar=new ZipFile(System.getProperty("dnv.sbw.jar"))) {
            var entry=jar.getEntry("com/atsuishio/superbwarfare/mixins/CameraMixin.class");
            var node=new ClassNode();
            try (var in=jar.getInputStream(entry)) { new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG); }
            var tail=node.methods.stream().filter(m -> m.name.equals("superbWarfare$setup")).findFirst().orElseThrow();
            var calls=java.util.Arrays.stream(tail.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                    .map(MethodInsnNode.class::cast).toList();
            int offset=java.util.stream.IntStream.range(0,calls.size()).filter(i ->
                    calls.get(i).name.equals("getThirdPersonCameraPosition")).findFirst().orElseThrow();
            var cameraWrites=calls.subList(offset+1,calls.size()).stream().filter(c -> c.owner.equals(node.name))
                    .map(c -> c.name).toList();
            boolean forge=node.methods.stream().anyMatch(m -> m.name.equals("m_90568_"));
            assertEquals(forge ? java.util.List.of("m_90566_","m_90568_") : java.util.List.of("getMaxZoom","move"),cameraWrites);
        }
    }
    @ParameterizedTest @ValueSource(ints={0,15,30,60})
    void unobstructedOrbitHeight(int elevation) throws Exception {
        var path=Path.of("../libertad/src/main/resources/data/libertad/sbw/vehicles/libertad.json");
        try (var input = Files.newBufferedReader(path)) {
            var data = JsonParser.parseReader(input).getAsJsonObject();
            var offset = data.getAsJsonArray("ThirdPersonCameraPos");
            var camera = new Camera();
            var rotate = Camera.class.getDeclaredMethod("setRotation",float.class,float.class);
            rotate.setAccessible(true);
            rotate.invoke(camera,37f,(float)-elevation);
            var look=new Vector3f(camera.getLookVector());
            var position = Camera.class.getDeclaredMethod("setPosition",double.class,double.class,double.class);
            position.setAccessible(true);
            position.invoke(camera,25.0,72.0,10.0); // Representative eye nine blocks above a Y=63 surface.
            // Empty scene, normal rear camera. The vanilla offset precedes the SBW tail.
            var move = java.util.Arrays.stream(Camera.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals("move") && m.getParameterCount()==3).findFirst().orElseThrow();
            move.setAccessible(true);
            move(camera,move,-4,0,0);
            move(camera,move,-offset.get(2).getAsDouble(), offset.get(1).getAsDouble(), offset.get(0).getAsDouble());
            double angle = Math.toRadians(elevation);
            double expected=72+offset.get(1).getAsDouble()*Math.cos(angle)-(4+offset.get(2).getAsDouble())*Math.sin(angle);
            assertEquals(expected,camera.getPosition().y,2e-5);
            assertEquals(look,camera.getLookVector());
            System.out.printf("helm elevation=%d cameraY=%.5f surfaceY=63%n",elevation,camera.getPosition().y);
        }
    }
    private static void move(Camera camera, Method move, double back, double up, double side) throws Exception {
        if (move.getParameterTypes()[0] == float.class) move.invoke(camera,(float)back,(float)up,(float)side);
        else move.invoke(camera,back,up,side);
    }
}
