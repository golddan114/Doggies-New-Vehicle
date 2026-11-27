package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretProfile;
import com.mojang.blaze3d.vertex.PoseStack;

/** Renderer adapter for the new M1/128 mm internal links; no authored triangle ranges. */
public final class DnvM1Hydraulics {
    private DnvM1Hydraulics(){}
    public static void apply(PoseStack stack,DnvDragonTurretProfile profile,DnvDragonTurretProfile.Link link,float elevation){
        var pose=profile.linkPose(link,elevation);
        stack.translate(pose.pivot().x,pose.pivot().y,pose.pivot().z);
        stack.mulPose(pose.rotation());
        stack.translate(-pose.original().x,-pose.original().y,-pose.original().z);
    }
}
