package com.liucodex.doggiesnewvehicle.client.mesh;

import com.liucodex.doggiesnewvehicle.armored.ApocalypseRig;
import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Render-thread local, exact-phase CPU bake. No phase quantization, world references or GPU/shader ownership. */
public final class ApocalypseRunningGear {
    private static final int LIMIT = 32;
    private final Map<Object, Gear> entities = new WeakHashMap<>();
    private DnvTurretMesh.MeshData source;

    public Gear sample(Object entity, DnvTurretMesh.MeshData mesh, float wheelL, float wheelR, float trackL, float trackR) {
        // Resource reload installs a new mesh identity; every derived stream must follow it.
        if (mesh != source) { entities.clear(); source = mesh; }
        Gear gear = entities.get(entity);
        if (gear == null) {
            if (entities.size() >= LIMIT) entities.clear();
            gear = new Gear(mesh); entities.put(entity, gear);
        }
        gear.update(mesh, wheelL, wheelR, trackL, trackR);
        return gear;
    }

    int retainedEntities() { return entities.size(); }

    public static final class Gear {
        private final float[] wheels, tracks;
        private float wheelL = Float.NaN, wheelR = Float.NaN, trackL = Float.NaN, trackR = Float.NaN;
        private int wheelBuilds, trackBuilds;
        private Gear(DnvTurretMesh.MeshData mesh) {
            var rig = ApocalypseRig.INSTANCE;
            wheels = new float[rig.wheels.stream().mapToInt(w -> DnvQuadStream.quads(mesh.part(w.part()).main()).length).sum()];
            tracks = new float[rig.tracks.stream().mapToInt(t -> t.count()).sum() * DnvQuadStream.quads(mesh.part("track_link").main()).length];
        }
        public float[] wheels() { return wheels; }
        public float[] tracks() { return tracks; }
        int wheelBuilds() { return wheelBuilds; }
        int trackBuilds() { return trackBuilds; }
        private void update(DnvTurretMesh.MeshData mesh, float wl, float wr, float tl, float tr) {
            var rig = ApocalypseRig.INSTANCE;
            if (wl != wheelL || wr != wheelR) {
                int offset = 0;
                for (var wheel : rig.wheels) {
                    var p = wheel.position();
                    var rotation = new Quaternionf().rotationX((p.x < 0 ? wl : wr) * ((float) Math.PI / 180F));
                    var transform = new Matrix4f().translation((float) p.x, (float) p.y, (float) p.z).rotate(rotation);
                    offset = append(wheels, offset, DnvQuadStream.quads(mesh.part(wheel.part()).main()), transform);
                }
                wheelL = wl; wheelR = wr; wheelBuilds++;
            }
            if (tl != trackL || tr != trackR) {
                int offset = 0;
                float[] linkMesh = DnvQuadStream.quads(mesh.part("track_link").main());
                for (var track : rig.tracks) {
                    double distance = (track.x() < 0 ? tl : tr) * track.length();
                    for (int i = 0; i < track.count(); i++) {
                        var link = ApocalypseRig.link(track, distance + track.length() * i / track.count());
                        var p = link.position();
                        var transform = new Matrix4f().translation((float) p.x, (float) p.y, (float) p.z)
                                .rotate(new Quaternionf().rotationX((float) link.angle()));
                        offset = append(tracks, offset, linkMesh, transform);
                    }
                }
                trackL = tl; trackR = tr; trackBuilds++;
            }
        }
        private static int append(float[] into, int offset, float[] from, Matrix4f transform) {
            Vector3f point = new Vector3f();
            for (int i = 0; i < from.length; i += 8) {
                transform.transformPosition(point.set(from[i], from[i + 1], from[i + 2]));
                into[offset++] = point.x; into[offset++] = point.y; into[offset++] = point.z;
                transform.transformDirection(point.set(from[i + 3], from[i + 4], from[i + 5]));
                into[offset++] = point.x; into[offset++] = point.y; into[offset++] = point.z;
                into[offset++] = from[i + 6]; into[offset++] = from[i + 7];
            }
            return offset;
        }
    }
}
