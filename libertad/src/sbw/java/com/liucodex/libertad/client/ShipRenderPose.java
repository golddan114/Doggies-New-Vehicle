package com.liucodex.libertad.client;

import com.liucodex.libertad.naval.MountGeometry.Angles;
import com.liucodex.libertad.ship.ShipModel;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;

/** Scratch matrices scoped to one ship submission. */
final class ShipRenderPose {
    final Matrix4f position = new Matrix4f();
    final Matrix3f normal = new Matrix3f();
    private final Matrix4d part = new Matrix4d();
    private final Matrix4f local = new Matrix4f();
    private final Matrix3f inverseNormal = new Matrix3f();

    void update(ShipModel model, Matrix4d body, Matrix4f base, Matrix3f baseNormal,
                int id, Angles angles, double spin) {
        if (id == 0) {
            local.set(body);
        } else {
            var motion = id >= ShipModel.PARTS ? model.aaTransform(model.aaMounts.get((id - ShipModel.PARTS) / 2), angles,
                    (id - ShipModel.PARTS) % 2 == 1)
                    : id >= ShipModel.FIRST_PROPELLER ? model.propellerTransform(id, spin) : model.transform(id, angles);
            local.set(body.mul(motion, part));
        }
        base.mul(local, position);
        baseNormal.mul(inverseNormal.set(local).invert().transpose(), normal);
    }
}
