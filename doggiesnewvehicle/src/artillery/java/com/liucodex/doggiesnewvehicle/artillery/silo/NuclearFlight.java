package com.liucodex.doggiesnewvehicle.artillery.silo;

import java.util.LinkedHashSet;
import java.util.Set;

/** Deterministic coordinate flight and at most three rolling chunk positions. No target-entity lease. */
public final class NuclearFlight {
    public enum Stage { CLIMB, TRANSFER, DIVE }
    public record Point(double x, double y, double z) {
        public Point { if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Non-finite flight"); }
        public Point add(Point b) { return new Point(x+b.x, y+b.y, z+b.z); }
        public Point scale(double n) { return new Point(x*n, y*n, z*n); }
        public double distance(Point b) { return Math.sqrt(Math.pow(x-b.x,2)+Math.pow(y-b.y,2)+Math.pow(z-b.z,2)); }
    }
    private NuclearFlight() { }
    public static Stage stage(Point position, Point target, Stage current, NuclearRules rules) {
        if(current==Stage.CLIMB&&position.y>=altitude(target,rules)-1e-6)current=Stage.TRANSFER;
        if(current==Stage.TRANSFER&&Math.hypot(target.x-position.x,target.z-position.z)<=rules.terminalDistance())current=Stage.DIVE;
        return current;
    }
    private static double altitude(Point target,NuclearRules rules){return Math.max(rules.cruiseAltitude(),target.y+128);}
    public static double speed(int age,NuclearRules rules){
        if(age<0)throw new IllegalArgumentException("Negative flight age");
        double rate=rules.accelerationRate();
        double initial=rules.exitRise()*Math.expm1(rate)/Math.expm1(rate*rules.exitTicks());
        return initial*Math.exp(Math.min(rate*age,Math.log(rules.maxSpeed()/initial)));
    }
    public static float yaw(Point velocity,float previous) {
        return Math.hypot(velocity.x,velocity.z)<1e-8?previous:(float)-Math.toDegrees(Math.atan2(velocity.x,velocity.z));
    }
    public static Point velocity(Point position, Point target, int age, Stage stage, NuclearRules rules) {
        Point aim = switch(stage){
            case CLIMB -> new Point(position.x,altitude(target,rules),position.z);
            case TRANSFER -> new Point(target.x,altitude(target,rules),target.z);
            case DIVE -> target;
        };
        Point direction = new Point(aim.x-position.x, aim.y-position.y, aim.z-position.z);
        double length = direction.distance(new Point(0,0,0));
        if (length < 1e-8) return new Point(0,0,0);
        double speed = speed(age,rules);
        return direction.scale(Math.min(speed, length)/length);
    }
    public static Set<Long> chunks(Point position, Point velocity) {
        Set<Long> result = new LinkedHashSet<>();
        int x=(int)Math.floor(position.x/16),z=(int)Math.floor(position.z/16);
        int endX=(int)Math.floor((position.x+velocity.x)/16),endZ=(int)Math.floor((position.z+velocity.z)/16);
        result.add(chunk(x,z));
        int dx=velocity.x>0?1:velocity.x<0?-1:0,dz=velocity.z>0?1:velocity.z<0?-1:0;
        double tx=dx==0?Double.POSITIVE_INFINITY:((dx>0?(x+1)*16.0:x*16.0)-position.x)/velocity.x;
        double tz=dz==0?Double.POSITIVE_INFINITY:((dz>0?(z+1)*16.0:z*16.0)-position.z)/velocity.z;
        while(x!=endX||z!=endZ){
            if(tx<tz){x+=dx;tx+=16/Math.abs(velocity.x);}
            else if(tz<tx){z+=dz;tz+=16/Math.abs(velocity.z);}
            else{x+=dx;z+=dz;tx+=16/Math.abs(velocity.x);tz+=16/Math.abs(velocity.z);}
            result.add(chunk(x,z));if(result.size()>3)throw new IllegalArgumentException("Flight step exceeds rolling chunk budget");
        }
        long prediction=chunk((int)Math.floor((position.x+2*velocity.x)/16),(int)Math.floor((position.z+2*velocity.z)/16));
        if(result.size()<3)result.add(prediction);
        return Set.copyOf(result);
    }
    private static long chunk(int x,int z){return (x&0xffffffffL)|((z&0xffffffffL)<<32);}
    public static boolean withinRange(Point origin, Point target, NuclearRules rules) {
        return origin.distance(target) <= rules.coordinateRange();
    }
}
