package com.liucodex.doggiesnewvehicle.client.weapons;

import software.bernie.geckolib.core.animatable.model.CoreGeoBone;

/** Full assignment from the immutable rest snapshot; never accumulates a previous action. */
final class Dsx1BonePose {
    private Dsx1BonePose() {}
    static void reset(CoreGeoBone bone) {
        if (bone == null || bone.getInitialSnapshot() == null) return;
        var rest = bone.getInitialSnapshot();
        bone.updatePosition(rest.getOffsetX(), rest.getOffsetY(), rest.getOffsetZ());
        bone.updateRotation(rest.getRotX(), rest.getRotY(), rest.getRotZ());
        bone.updateScale(rest.getScaleX(), rest.getScaleY(), rest.getScaleZ());
    }
    static void apply(CoreGeoBone bone, Dsx1PoseTimeline.Frame frame, int index) {
        apply(bone, frame, index, 1, 1);
    }
    static void apply(CoreGeoBone bone, Dsx1PoseTimeline.Frame frame, int index, float translation, float rotation) {
        reset(bone);
        if (bone == null) return;
        bone.setPosX(bone.getPosX() + translation * frame.value(index, 0));
        bone.setPosY(bone.getPosY() + translation * frame.value(index, 1));
        bone.setPosZ(bone.getPosZ() + translation * frame.value(index, 2));
        bone.setRotX(bone.getRotX() - rotation * (float)Math.toRadians(frame.value(index, 3)));
        bone.setRotY(bone.getRotY() - rotation * (float)Math.toRadians(frame.value(index, 4)));
        bone.setRotZ(bone.getRotZ() + rotation * (float)Math.toRadians(frame.value(index, 5)));
    }
    static float[] capture(CoreGeoBone bone, float[] target) {
        if (bone == null) return null;
        if (target == null) target = new float[9];
        target[0] = bone.getPosX(); target[1] = bone.getPosY(); target[2] = bone.getPosZ();
        target[3] = bone.getRotX(); target[4] = bone.getRotY(); target[5] = bone.getRotZ();
        target[6] = bone.getScaleX(); target[7] = bone.getScaleY(); target[8] = bone.getScaleZ();
        return target;
    }
    static void restore(CoreGeoBone bone, float[] pose) {
        if (bone == null || pose == null) return;
        bone.updatePosition(pose[0], pose[1], pose[2]); bone.updateRotation(pose[3], pose[4], pose[5]);
        bone.updateScale(pose[6], pose[7], pose[8]);
    }
}
