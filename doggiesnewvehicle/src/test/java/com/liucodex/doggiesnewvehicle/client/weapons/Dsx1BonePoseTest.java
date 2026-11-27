package com.liucodex.doggiesnewvehicle.client.weapons;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.cache.object.GeoBone;

class Dsx1BonePoseTest {
    @Test void aimedActionsDampOnlyTheCommonParentAndKeepLeafMechanismsAtFullTravel() throws Exception {
        var timeline=Dsx1PoseTimeline.read(Files.newInputStream(Path.of("src/main/resources/assets/doggiesnewvehicle/animations/dsx1.animation.json")));
        var frame=new Dsx1PoseTimeline.Frame();
        for (var action : Dsx1PoseTimeline.Action.values()) for (int t=0;t<=200;t++) {
            timeline.sample(action,t/200D,frame);
            for (int i=0;i<Dsx1PoseTimeline.BONES.length;i++) {
                var bone=new GeoBone(null,Dsx1PoseTimeline.BONES[i],false,null,false,false);
                bone.saveInitialSnapshot();
                boolean parent=i<2;
                Dsx1BonePose.apply(bone,frame,i,parent?Dsx1ViewPose.actionTranslation(1):1,parent?Dsx1ViewPose.actionRotation(1):1);
                assertEquals(frame.value(i,2)*(parent?.18:1),bone.getPosZ(),1e-5);
                assertEquals(Math.toRadians(frame.value(i,3))*(parent?-.08:-1),bone.getRotX(),1e-5);
                assertEquals(1,bone.getScaleZ());
            }
        }
    }
    @Test void actualGeoBoneRestSnapshotSurvivesRepeatedActionsAndForeignOrDeadViews() throws Exception {
        var bone=new GeoBone(null,"rot",false,null,false,false);
        bone.updateRotation(.1f,.2f,.3f);bone.updatePosition(1,2,3);bone.saveInitialSnapshot();
        var timeline=Dsx1PoseTimeline.read(Files.newInputStream(Path.of("src/main/resources/assets/doggiesnewvehicle/animations/dsx1.animation.json")));
        var frame=new Dsx1PoseTimeline.Frame();
        int index=java.util.Arrays.asList(Dsx1PoseTimeline.BONES).indexOf("rot");
        timeline.sample(Dsx1PoseTimeline.Action.BOLT,.4,frame);
        for(int i=0;i<500;i++)Dsx1BonePose.apply(bone,frame,index);
        assertEquals(.3+Math.PI/6,bone.getRotZ(),1e-6);assertEquals(1,bone.getPosX());
        Dsx1BonePose.reset(bone);
        assertEquals(.3,bone.getRotZ(),1e-6);assertEquals(.1,bone.getRotX(),1e-6);assertEquals(.2,bone.getRotY(),1e-6);
        assertEquals(1,bone.getScaleZ());
        timeline.sample(Dsx1PoseTimeline.Action.IDLE,0,frame);Dsx1BonePose.apply(bone,frame,index);
        assertEquals(.3,bone.getRotZ(),1e-6);
        Dsx1BonePose.reset(null);
    }
    @Test void pauseRestoresTheCompleteEvaluatedPoseRatherThanResamplingAnAdvancingHelper() {
        var bone = new GeoBone(null,"Lefthand",false,null,false,false);
        bone.updatePosition(3,4,5); bone.updateRotation(.1f,.2f,.3f); bone.updateScale(.7f,.8f,.9f);
        var frozen = Dsx1BonePose.capture(bone, null);
        for (int i = 0; i < 100; i++) {
            bone.updatePosition(i,0,0); bone.updateRotation(i,0,0); bone.updateScale(1,1,1);
            Dsx1BonePose.restore(bone, frozen);
            assertArrayEquals(frozen, Dsx1BonePose.capture(bone, null));
        }
        assertSame(frozen, Dsx1BonePose.capture(bone, frozen));
    }
}
