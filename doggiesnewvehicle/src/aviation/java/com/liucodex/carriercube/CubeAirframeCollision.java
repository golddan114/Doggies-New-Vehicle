package com.liucodex.carriercube;

import com.atsuishio.superbwarfare.tools.OBB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import java.util.List;

/** Continuous separating-axis test for an SBW part translating past a stationary block shape. */
final class CubeAirframeCollision {
    private static final Vector3d[] WORLD={new Vector3d(1,0,0),new Vector3d(0,1,0),new Vector3d(0,0,1)};

    static double contact(OBB part,Vec3 movement,AABB obstacle) {
        return sweep(part, movement, obstacle).fraction;
    }

    private record Hit(double fraction, Vector3d axis) {}
    private static final Hit CLEAR = new Hit(1, null);
    private static final class Window {
        double enter = Double.NEGATIVE_INFINITY, exit = Double.POSITIVE_INFINITY;
        double penetration = Double.POSITIVE_INFINITY;
        Vector3d enteringAxis, shallowAxis;
        boolean deepens;
    }

    private static Hit sweep(OBB part,Vec3 movement,AABB obstacle) {
        var box=OBB.getWorldAABB(part);
        if(!box.expandTowards(movement).intersects(obstacle))return CLEAR;
        var basis=part.getAxes();
        var e=part.extents();
        var center=obstacle.getCenter();
        var offset=new Vector3d(part.center).sub(center.x,center.y,center.z);
        var half=new Vector3d(obstacle.getXsize(),obstacle.getYsize(),obstacle.getZsize()).mul(.5);
        var window=new Window();
        for(var axis:WORLD) if(!overlap(axis,basis,e,offset,half,movement,window))return CLEAR;
        for(var axis:basis) {
            if(!overlap(axis,basis,e,offset,half,movement,window))return CLEAR;
            for(var world:WORLD) {
                var cross=axis.cross(world,new Vector3d());
                if(cross.lengthSquared()>1e-12 && !overlap(cross,basis,e,offset,half,movement,window))return CLEAR;
            }
        }
        if(window.exit<=0 || window.enter>=1)return CLEAR;
        if(window.enter<0)return window.deepens || movement.lengthSqr()==0 ? new Hit(0,window.shallowAxis) : CLEAR;
        return new Hit(Math.max(0,window.enter-.0001),window.enteringAxis);
    }

    private static boolean overlap(Vector3d axis,Vector3d[] basis,Vector3d e,Vector3d offset,
                                   Vector3d half,Vec3 step,Window window) {
        double radius=Math.abs(axis.dot(basis[0]))*e.x+Math.abs(axis.dot(basis[1]))*e.y
                +Math.abs(axis.dot(basis[2]))*e.z+Math.abs(axis.x)*half.x+Math.abs(axis.y)*half.y+Math.abs(axis.z)*half.z;
        double pos=offset.dot(axis),speed=axis.dot(step.x,step.y,step.z);
        double depth=(radius-Math.abs(pos))/axis.length();
        if(depth<window.penetration) {
            window.penetration=depth;
            window.shallowAxis=axis;
            window.deepens=pos*speed<0;
        }
        if(Math.abs(speed)<1e-10)return Math.abs(pos)<radius-1e-8;
        double a=(-radius-pos)/speed,b=(radius-pos)/speed;
        double entry=Math.min(a,b);
        if(entry>window.enter) {
            window.enter=entry;
            window.enteringAxis=axis;
        }
        window.exit=Math.min(window.exit,Math.max(a,b));
        return window.enter<window.exit;
    }

    static Vec3 resolve(List<OBB> parts, List<AABB> obstacles, Vec3 movement) {
        Vec3 allowed=movement;
        // Re-sweep from the original pose after removing each blocked component.
        // This checks the final straight displacement even at a floor/wall corner.
        for(int pass=0;pass<4;pass++) {
            Hit first=CLEAR;
            for(var obstacle:obstacles) for(var part:parts) {
                if(part.part==OBB.Part.COLLISION)continue;
                Hit hit=sweep(part,allowed,obstacle);
                if(hit.fraction<first.fraction)first=hit;
            }
            if(first==CLEAR)return allowed;
            if(pass==3)return allowed.scale(first.fraction);
            var axis=first.axis;
            double x=Math.abs(axis.x*allowed.x), y=Math.abs(axis.y*allowed.y), z=Math.abs(axis.z*allowed.z);
            if(y>=x && y>=z)allowed=allowed.multiply(1,first.fraction,1);
            else if(x>=z)allowed=allowed.multiply(first.fraction,1,1);
            else allowed=allowed.multiply(1,1,first.fraction);
        }
        return allowed;
    }
    private CubeAirframeCollision(){}
}
