package com.liucodex.doggiesnewvehicle.boss;

import com.liucodex.doggiesnewvehicle.boss.mesh.MothershipRig.Angles;
import com.liucodex.doggiesnewvehicle.entity.DnvVehiclePresentation;
import java.util.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

/** Exact SBW OBB math plus compiled production transforms. Only SRG names and the unused
 * serialization-bearing part label are adapted; no collision algorithm is replaced. */
public final class NativeHullFixture extends ClassLoader {
    public enum Part { BODY, TURRET, COLLISION, INTERACTIVE, WHEEL_LEFT, WHEEL_RIGHT }
    /** Serialization-free data holder; upstream collision/movement math is still executed. */
    public static class MovementInfo {
        private Vec3 position,size;
        public void setPosition(Vec3 value){position=value;}public Vec3 getPosition(){return position;}
        public void setSize(Vec3 value){size=value;}public Vec3 getSize(){return size;}
        public void setPart(Part value){}
    }
    private static final NativeHullFixture LOADER=new NativeHullFixture();
    private static final String OBB="com/atsuishio/superbwarfare/tools/OBB";
    private static final String OWNER="com/liucodex/doggiesnewvehicle/boss/";
    private static final String PREFIX="com/liucodex/doggiesnewvehicle/boss/hullprobe/";
    private static final Map<String,String> TYPES=Map.of(OBB,PREFIX+"OBB",OBB+"$Companion",PREFIX+"OBB$Companion",
            OBB+"$Part",Type.getInternalName(Part.class),OWNER+"MothershipHull",PREFIX+"MothershipHull",
            OWNER+"DnvTrackedBossHull",PREFIX+"DnvTrackedBossHull",OWNER+"DnvTrackedBossHull$Box",PREFIX+"DnvTrackedBossHull$Box",
            OWNER+"DnvTrackedBossHull$Model",PREFIX+"DnvTrackedBossHull$Model",OWNER+"DnvTrackedBossHull$Models",PREFIX+"DnvTrackedBossHull$Models",
            "com/atsuishio/superbwarfare/data/vehicle/subdata/OBBInfo",Type.getInternalName(MovementInfo.class));
    NativeHullFixture(){super(NativeHullFixture.class.getClassLoader());}
    @Override protected Class<?> findClass(String name)throws ClassNotFoundException {
        String original=TYPES.entrySet().stream().filter(e->e.getValue().equals(name.replace('.','/'))).map(Map.Entry::getKey).findFirst().orElseThrow(()->new ClassNotFoundException(name));
        try(var input=getParent().getResourceAsStream(original+".class")) {
            ClassWriter out=new ClassWriter(0);
            new ClassReader(Objects.requireNonNull(input)).accept(new ClassRemapper(out,new Remapper(){
                @Override public String map(String value){return TYPES.getOrDefault(value,value);}
                @Override public String mapFieldName(String owner,String field,String descriptor){
                    if(owner.equals("net/minecraft/world/phys/Vec3"))return switch(field){case "f_82479_"->"x";case "f_82480_"->"y";case "f_82481_"->"z";default->field;};
                    if(owner.equals("net/minecraft/world/phys/AABB"))return switch(field){case "f_82288_"->"minX";case "f_82289_"->"minY";case "f_82290_"->"minZ";case "f_82291_"->"maxX";case "f_82292_"->"maxY";case "f_82293_"->"maxZ";default->field;};
                    return field;
                }
                @Override public String mapMethodName(String owner,String method,String descriptor){
                    if(owner.equals("net/minecraft/world/phys/AABB")&&descriptor.equals("()Lnet/minecraft/world/phys/Vec3;"))return "getCenter";
                    if(owner.equals("net/minecraft/world/phys/AABB"))return switch(method){case "m_82362_"->"getXsize";case "m_82376_"->"getYsize";case "m_82385_"->"getZsize";default->method;};
                    if(owner.equals("net/minecraft/world/phys/Vec3"))return switch(method){case "m_82546_"->"subtract";case "m_82549_"->"add";case "m_82490_"->"scale";case "m_82548_"->"lengthSqr";default->method;};
                    return method;
                }
            }),0);
            byte[] bytes=out.toByteArray();return defineClass(name,bytes,0,bytes.length);
        }catch(Exception failure){throw new ClassNotFoundException(name,failure);}
    }
    static Class<?> type(String name)throws Exception{return LOADER.loadClass((PREFIX+name).replace('/','.'));}
    static ClassLoader loader(){return LOADER;}
    static List<Box> motherPose(Vec3 bottom,double yaw,Angles[] angles)throws Exception {
        return wrap(type("MothershipHull").getMethod("pose",Vec3.class,double.class,Angles[].class).invoke(null,bottom,yaw,angles));
    }
    static List<Box> tracked(boolean heaven,double yaw,DnvVehiclePresentation state)throws Exception {
        Object hull=trackedHull(heaven,yaw,state);
        return wrap(hull.getClass().getMethod("boxes").invoke(hull));
    }
    static Object trackedHull(boolean heaven,double yaw,DnvVehiclePresentation state)throws Exception {
        Object hull=type("DnvTrackedBossHull").getConstructor(boolean.class).newInstance(heaven);
        hull.getClass().getMethod("update",double.class,double.class,double.class,double.class,DnvVehiclePresentation.class).invoke(hull,271D,93D,-185D,yaw,state);
        return hull;
    }
    static List<Box> wrap(Object values){return ((List<?>)values).stream().map(Box::new).toList();}
    @SuppressWarnings("unchecked") static List<AABB> scanBounds(List<Box> boxes)throws Exception {
        return (List<AABB>)type("MothershipHull").getMethod("scanBounds",List.class).invoke(null,boxes.stream().map(Box::value).toList());
    }
    static boolean intersects(List<Box> boxes,AABB aabb)throws Exception {
        for(Box box:boxes)if((boolean)type("OBB").getMethod("isColliding",type("OBB"),AABB.class).invoke(null,box.value,aabb))return true;
        return false;
    }
    record Box(Object value) {
        AABB bounds()throws Exception{return (AABB)type("OBB").getMethod("getWorldAABB",type("OBB")).invoke(null,value);}
        Vector3d center()throws Exception{return (Vector3d)value.getClass().getField("center").get(value);}
        boolean contains(Vec3 point)throws Exception{return (boolean)value.getClass().getMethod("contains",Vec3.class).invoke(value,point);}
        Box inflate(double amount)throws Exception{return new Box(value.getClass().getMethod("inflate",double.class).invoke(value,amount));}
        boolean clip(Vec3 from,Vec3 to)throws Exception{return ((Optional<?>)value.getClass().getMethod("clip",Vector3d.class,Vector3d.class).invoke(value,new Vector3d(from.x,from.y,from.z),new Vector3d(to.x,to.y,to.z))).isPresent();}
    }
}
