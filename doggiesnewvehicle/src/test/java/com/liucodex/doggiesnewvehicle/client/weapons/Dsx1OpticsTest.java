package com.liucodex.doggiesnewvehicle.client.weapons;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class Dsx1OpticsTest {
    @Test void realNearPlaneKeepsBothPupilsVisibleDuringUncompressedParentRecoil() throws Exception {
        var o=optics(); var projection=new Matrix4f().perspective((float)Math.toRadians(70),16f/9,.05f,1000);
        var view=new Matrix4f().rotateXYZ(.6f,-1.3f,.09f);
        var base=new Matrix4f().translation(.35f,-.25f,-.7f);
        var reference=new Matrix4f(view).invert().mul(base).scale(.9f,.9f,.015f);
        for(int i=0;i<=100;i++){
            var actual=new Matrix4f(view).invert().mul(base).translate(0,0,i*.0012f)
                    .rotateX(.015f).scale(.9f,.9f,.015f);
            var clip=new Matrix4f(projection).mul(view)
                    .mul(o.renderCorrection(view,reference,actual,projection,1)).mul(actual);
            for(float z:new float[]{o.rearZ(),o.frontZ()})for(int sx:new int[]{-1,1})for(int sy:new int[]{-1,1}){
                var p=project(clip,o.x()+sx*o.halfWidth(),o.y()+sy*o.halfHeight(),z);
                assertTrue(p.z>-1&&p.z<1,"pupil crosses actual near plane at recoil="+i);
            }
            assertTrue(Math.abs(project(clip,o.x(),o.y(),o.reticleZ()).y)>1e-4,"native angular recoil remains visible");
        }
    }
    @Test void splitNativeModelViewAndItemPoseKeepBothPupilsCentredAcrossCameraAngles() throws Exception {
        var o = optics();
        var projection = new Matrix4f().perspective((float)Math.toRadians(70), 16f/9, .05f, 1000);
        for (int yaw : new int[]{-180,-135,-90,-45,0,45,90,135,180})
            for (int pitch : new int[]{-85,-45,0,45,85}) for (boolean left : new boolean[]{false,true}) {
                var view = new Matrix4f().rotateX((float)Math.toRadians(pitch))
                        .rotateY((float)Math.toRadians(yaw));
                // MC1.21.1 puts inverse(camera) in the item pose, then camera in ModelView.
                var cameraPose = new Matrix4f().translation(.35f,-.25f,-.7f).scale(left?-.9f:.9f,.9f,.015f);
                var itemPose = new Matrix4f(view).invert().mul(cameraPose);
                var savedView = new Matrix4f(view); var savedPose = new Matrix4f(itemPose);
                var correction = o.renderCorrection(view,itemPose,projection,1);
                var clip = new Matrix4f(projection).mul(view).mul(correction).mul(itemPose);
                for (float z : new float[]{o.rearZ(),o.frontZ(),o.reticleZ()}) {
                    var centre = project(clip,o.x(),o.y(),z);
                    assertEquals(0,centre.x,2e-5,"pupil X at "+yaw+"/"+pitch+"/"+left);
                    assertEquals(0,centre.y,2e-5,"pupil Y at "+yaw+"/"+pitch+"/"+left);
                    assertTrue(centre.z>-1&&centre.z<1,"pupil clipped at "+yaw+"/"+pitch);
                }
                assertTrue(view.equals(savedView,0)); assertTrue(itemPose.equals(savedPose,0));
            }
    }

    @Test void splitViewCorrectionMatchesForgeThroughoutAdsAndDoesNotMutateCameraState() throws Exception {
        var o = optics();
        var projection = new Matrix4f().perspective((float)Math.toRadians(70), 21f/9, .05f, 1000);
        projection.m20(.013f).m21(-.007f);
        var view = new Matrix4f().translate(.03f,-.02f,.01f).rotateXYZ(.6f,-1.3f,.09f);
        var cameraPose = new Matrix4f().translation(.35f,-.25f,-.7f).rotateXYZ(.12f,-.18f,.07f).scale(.9f,.9f,.015f);
        var itemPose = new Matrix4f(view).invert().mul(cameraPose);
        var savedProjection = new Matrix4f(projection);
        for (int i=0;i<=100;i++) {
            double ads=i/100D;
            var forge = new Matrix4f(o.correction(cameraPose,projection,ads)).mul(cameraPose);
            var neo = new Matrix4f(view).mul(o.renderCorrection(view,itemPose,projection,ads)).mul(itemPose);
            assertTrue(forge.equals(neo,2e-5f),"platform display mismatch at ADS="+ads);
            assertEquals(1,o.renderCorrection(view,itemPose,projection,ads).determinant(),1e-4);
        }
        assertTrue(projection.equals(savedProjection,0));
        assertTrue(o.renderCorrection(new Matrix4f(),cameraPose,projection,1)
                .equals(o.correction(cameraPose,projection,1),1e-6f));
    }

    @Test void rendererConsumesTheExternalShaderModelViewAndRejectsInvalidViews() throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var in=getClass().getResourceAsStream("/com/liucodex/doggiesnewvehicle/client/weapons/Dsx1Renderer.class")) {
            assertNotNull(in); new org.objectweb.asm.ClassReader(in.readAllBytes()).accept(node,0);
        }
        var align=node.methods.stream().filter(m->m.name.equals("alignPupil")).findFirst().orElseThrow();
        boolean shaderView=false,renderCorrection=false;
        for (var instruction:align.instructions) if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call) {
            if (call.owner.equals("com/mojang/blaze3d/systems/RenderSystem")&&call.name.equals("getModelViewMatrix")) shaderView=true;
            if (call.owner.endsWith("/Dsx1Optics")&&call.name.equals("renderCorrection")) {
                assertTrue(shaderView,"renderer must supply the shader's ModelView before aligning"); renderCorrection=true;
            }
            assertFalse(call.owner.endsWith("/Dsx1Optics")&&call.name.equals("correction"),"raw item pose is not camera space");
        }
        assertTrue(renderCorrection);
        var o=optics(); var reference=new Matrix4f().translate(.3f,-.2f,-.7f).scale(1,1,.015f);
        var projection=new Matrix4f().perspective(1.2f,16f/9,.05f,1000);
        for (var invalid:new Matrix4f[]{new Matrix4f().scale(0),new Matrix4f().m00(Float.NaN),new Matrix4f().m30(Float.POSITIVE_INFINITY)})
            assertEquals(new Matrix4f(),o.renderCorrection(invalid,reference,projection,1));
        assertEquals(new Matrix4f(),o.renderCorrection(new Matrix4f().rotateY(.8f),reference,projection,0));
    }

    @Test void continuousNativeActionsDoNotPumpDepthOrCollapseTheProjectedPupil() throws Exception {
        var o=optics();
        var timeline=Dsx1PoseTimeline.read(Files.newInputStream(Path.of("src/main/resources/assets/doggiesnewvehicle/animations/dsx1.animation.json")));
        var frame=new Dsx1PoseTimeline.Frame();
        for(var action:Dsx1PoseTimeline.Action.values())for(double zoom:new double[]{.5,.75,1})for(boolean left:new boolean[]{false,true}){
            var projection=new Matrix4f().perspective((float)Math.toRadians(70),16f/9,.05f,1000);
            var view=new Matrix4f().rotateXYZ(.6f,-1.3f,.09f);
            float previous=Float.NaN;
            for(int i=0;i<=300;i++){
                timeline.sample(action,i/300D,frame);
                float rotation=Dsx1ViewPose.actionRotation(zoom),translation=Dsx1ViewPose.actionTranslation(zoom);
                var parent=new Matrix4f().translation(.35f,-.25f,-.7f).scale(left?-.9f:.9f,.9f,.9f)
                        .translate(-frame.value(0,0)*translation/16,frame.value(0,1)*translation/16,frame.value(0,2)*translation/16)
                        .rotateZ((float)Math.toRadians(frame.value(0,5)*rotation))
                        .rotateY((float)Math.toRadians(-frame.value(0,4)*rotation))
                        .rotateX((float)Math.toRadians(-frame.value(0,3)*rotation))
                        .scale(1,1,Dsx1ViewPose.depthScale(Dsx1ViewPose.ads(zoom,true,i/300D)));
                var itemParent=new Matrix4f(view).invert().mul(parent);
                var clip=new Matrix4f(projection).mul(view)
                        .mul(o.renderCorrection(view,itemParent,projection,zoom)).mul(itemParent);
                var center=project(clip,o.x(),o.y(),o.frontZ());
                var top=project(clip,o.x(),o.y()+o.halfHeight(),o.frontZ());
                float opening=Math.abs(top.y-center.y);
                assertTrue(Float.isFinite(opening)&&opening>.1,"pupil collapsed during "+action+" at "+i);
                if(Float.isFinite(previous))assertTrue(Math.abs(opening-previous)<.025,"single-frame flattening "+action+"/"+zoom+"/"+left+"/"+i+": "+previous+" -> "+opening);
                previous=opening;
            }
        }
    }
    private static Dsx1Optics optics() throws Exception {
        try (var in = Files.newInputStream(Path.of("src/main/resources/assets/doggiesnewvehicle/models/item/dsx1.dsm"))) {
            return Dsx1Optics.from(Dsx1Mesh.read(in).part("dsx_glass"));
        }
    }
    private static Vector3f project(Matrix4f matrix, float x, float y, float z) {
        return matrix.transformProject(new Vector3f(x, y, z));
    }

    @Test void bothActualPupilsAndTheReticleShareScreenCentreAcrossParentTransformsAndProjections() throws Exception {
        var o = optics(); assertNotNull(o);
        assertEquals(-.00096141, o.x(), 1e-6); assertEquals(.3205972, o.y(), 1e-6);
        for (float aspect : new float[]{16f/9, 21f/9, 4f/3, 9f/16}) {
            for (int fov : new int[]{10, 20, 40, 70, 100}) {
                for (boolean left : new boolean[]{false,true}) {
                    var projection = new Matrix4f().perspective((float)Math.toRadians(fov), aspect, .01f, 1000);
                    projection.m20(.013f).m21(-.007f);
                    var reference = new Matrix4f().translation(.35f,-.25f,-.7f).rotateXYZ(.12f,-.18f,.07f)
                            .scale(left ? -.9f : .9f, .9f, .015f).rotateXYZ(-.05f,.08f,-.1f);
                    var before = new Matrix4f(reference); var oldProjection = new Matrix4f(projection);
                    var correction = o.correction(reference, projection, 1);
                    var corrected = new Matrix4f(correction).mul(reference);
                    var clip = new Matrix4f(projection).mul(corrected);
                    for (float z : new float[]{o.rearZ(),o.frontZ(),o.reticleZ()}) {
                        var p = project(clip,o.x(),o.y(),z);
                        assertEquals(0,p.x,1e-5,"pupil X"); assertEquals(0,p.y,1e-5,"pupil Y");
                        assertTrue(p.z > -1 && p.z < 1,"pupil clipped");
                    }
                    assertEquals(before,reference); assertEquals(oldProjection,projection);
                    // Camera-space correction is rigid: authored relative dimensions do not change.
                    var a = reference.transformPosition(new Vector3f(-.1f,.1f,.5f));
                    var b = reference.transformPosition(new Vector3f(.1f,.4f,-1.8f));
                    float distance = a.distance(b);
                    correction.transformPosition(a); correction.transformPosition(b);
                    assertEquals(distance,a.distance(b),1e-5);
                    for (int sx : new int[]{-1,1}) for (int sy : new int[]{-1,1}) {
                        var p = project(clip,o.x()+sx*o.halfWidth(),o.y()+sy*o.halfHeight(),o.frontZ());
                        assertTrue(Float.isFinite(p.x)&&Float.isFinite(p.y),"full tunnel footprint " + p);
                    }
                }
            }
        }
    }

    @Test void adsTransitionIsFiniteAndDoesNotAffectHipOrTheWorldCamera() throws Exception {
        var o=optics();
        var reference=new Matrix4f().translation(.4f,-.2f,-.6f).rotateXYZ(.1f,-.1f,.1f).scale(1,1,.09f);
        var projection=new Matrix4f().perspective(.6f,16f/9,.01f,1000);
        assertTrue(o.correction(reference,projection,0).equals(new Matrix4f(),1e-6f));
        for (int i=0;i<=120;i++) {
            var c=o.correction(reference,projection,i/120.0);
            assertTrue(Float.isFinite(c.determinant()));
            assertEquals(1,c.determinant(),1e-4);
        }
        assertTrue(o.correction(reference,projection,Double.NaN).equals(new Matrix4f(),1e-6f));
        assertTrue(o.correction(new Matrix4f().scale(0),projection,1).equals(new Matrix4f(),1e-6f));
    }

    @Test void actualOpaqueScopeLeavesTheCentralSixtyFivePercentOpenThroughBothMouths()throws Exception{
        Dsx1Mesh mesh;
        try(var in=Files.newInputStream(Path.of("src/main/resources/assets/doggiesnewvehicle/models/item/dsx1.dsm"))){mesh=Dsx1Mesh.read(in);}
        var o=Dsx1Optics.from(mesh.part("dsx_glass"));var scope=mesh.part("dsx_scope");
        for(float aspect:new float[]{4f/3,16f/9,21f/9})for(int fov:new int[]{70,90,110})for(boolean left:new boolean[]{false,true}){
            var projection=new Matrix4f().perspective((float)Math.toRadians(fov),aspect,.01f,1000);
            var reference=new Matrix4f().translation(.35f,-.25f,-.7f).scale(left?-.9f:.9f,.9f,.015f);
            var camera=new Matrix4f(o.correction(reference,projection,1)).mul(reference);
            for(int i=-6;i<=6;i++){
                var ray=new Vector3f(0,(i*.65f/6)/projection.m11(),-1);
                for(int t=0;t<scope.triangles();t++){
                    var a=camera.transformPosition(point(scope,t*3));var b=camera.transformPosition(point(scope,t*3+1));var c=camera.transformPosition(point(scope,t*3+2));
                    assertFalse(intersects(ray,a,b,c),"Opaque tunnel blocks the usable window: "+aspect+"/"+fov+" at "+i+" triangle "+t);
                }
            }
        }
    }
    private static Vector3f point(Dsx1Mesh.Part p,int i){return new Vector3f(p.value(i,0),p.value(i,1),p.value(i,2));}
    private static boolean intersects(Vector3f ray,Vector3f a,Vector3f b,Vector3f c){
        var e1=new Vector3f(b).sub(a);var e2=new Vector3f(c).sub(a);var h=new Vector3f(ray).cross(e2);
        float det=e1.dot(h);if(Math.abs(det)<1e-9)return false;
        var s=new Vector3f(a).negate();float u=s.dot(h)/det;if(u<0||u>1)return false;
        var q=new Vector3f(s).cross(e1);float v=ray.dot(q)/det;
        return v>=0&&u+v<=1&&e2.dot(q)/det>.001;
    }

    @Test void recoilRemainsVisibleAndReturnsToTheSameOpticalAxis() throws Exception {
        var o=optics();var base=new Matrix4f().translation(.4f,-.2f,-.6f).scale(1,1,.09f);
        var projection=new Matrix4f().perspective(.4f,16f/9,.01f,1000);
        var aligned=new Matrix4f(o.correction(base,projection,1)).mul(base);
        var resting=project(new Matrix4f(projection).mul(aligned),o.x(),o.y(),o.reticleZ());
        var firing=project(new Matrix4f(projection).mul(aligned).rotateX(.015f).translate(0,0,.03f),o.x(),o.y(),o.reticleZ());
        assertEquals(0,resting.y,1e-5); assertTrue(Math.abs(firing.y)>1e-4);
        assertEquals(resting,project(new Matrix4f(projection).mul(aligned),o.x(),o.y(),o.reticleZ()));
    }

    @Test void lateAdsAndReverseTransitionKeepBothMouthsOpenWhileDepthChangesEveryFrame() throws Exception {
        var o = optics();
        for (boolean left : new boolean[]{false, true}) for (int fov : new int[]{20, 70, 100}) {
            var projection = new Matrix4f().perspective((float)Math.toRadians(fov), 16f/9, .01f, 1000);
            float previous = Float.NaN;
            for (int i = 0; i <= 100; i++) {
                float zoom = .5f + i * .005f;
                var reference = new Matrix4f().translation(.35f,-.25f,-.7f)
                        .scale(left ? -.9f : .9f, .9f, Dsx1ViewPose.depthScale(zoom));
                var clip = new Matrix4f(projection).mul(o.correction(reference, projection, zoom)).mul(reference);
                var centre = project(clip, o.x(), o.y(), o.frontZ());
                var edge = project(clip, o.x(), o.y() + o.halfHeight(), o.frontZ());
                assertEquals(0, centre.x, 1e-5); assertEquals(0, centre.y, 1e-5);
                assertTrue(edge.y >= .65 && edge.y <= .73, "late ADS must not close then reopen: " + edge);
                if (Float.isFinite(previous)) assertEquals(previous, edge.y, .001);
                previous = edge.y;
            }
        }
    }

    @Test void wholeAdsTransitionDoesNotShrinkTheVisibleOpeningAfterBothMouthsEnterTheView() throws Exception {
        var o = optics();
        for (boolean left : new boolean[]{false,true}) for (int fov : new int[]{20,70,100}) {
            var projection = new Matrix4f().perspective((float)Math.toRadians(fov),16f/9,.01f,1000);
            float previous = 0;
            for (int i=0;i<=200;i++) {
                float zoom = i/200f;
                var reference = new Matrix4f().translation(.35f,-.25f,-.7f)
                    .scale(left?-.9f:.9f,.9f,Dsx1ViewPose.depthScale(zoom));
                var clip = new Matrix4f(projection).mul(o.correction(reference,projection,zoom)).mul(reference);
                float low = -1, high = 1;
                for (float z : new float[]{o.rearZ(),o.frontZ()}) {
                    var a = project(clip,o.x(),o.y()-o.halfHeight(),z);
                    var b = project(clip,o.x(),o.y()+o.halfHeight(),z);
                    low = Math.max(low,Math.min(a.y,b.y)); high = Math.min(high,Math.max(a.y,b.y));
                }
                float opening = Math.max(0,high-low);
                assertTrue(opening + .002 >= previous,"visible opening contracts at zoom="+zoom+", FOV="+fov+": "+previous+" -> "+opening);
                previous = opening;
            }
        }
    }
}
