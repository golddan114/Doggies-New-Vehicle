package com.liucodex.doggiesnewvehicle.artillery.silo;

/** Bounded radial acceleration; unknown affiliation must be rejected by the world adapter first. */
public final class VacuumPull {
    public static final double MAX_SPEED = 1.2;
    public record Motion(double x,double y,double z) { }
    private VacuumPull() { }
    public static Motion velocity(double dx,double dy,double dz,double vx,double vy,double vz,int age) {
        if(!Double.isFinite(dx)||!Double.isFinite(dy)||!Double.isFinite(dz)
                ||!Double.isFinite(vx)||!Double.isFinite(vy)||!Double.isFinite(vz)) return new Motion(0,0,0);
        double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(distance>VacuumPayloadProgress.PULL_RADIUS||age<0||age>=VacuumPayloadProgress.PULL_TICKS)
            return new Motion(vx,vy,vz);
        double ramp=Math.min(1,(age+1)/20D);
        double acceleration=distance<.15?0:(.045+.1375*(1-distance/VacuumPayloadProgress.PULL_RADIUS))*ramp;
        double lift=dy>.15?.08*ramp:0;
        double damping=distance<.15?.5:.90, divisor=Math.max(distance,.15);
        double x=vx*damping+dx/divisor*acceleration,y=vy*damping+dy/divisor*acceleration+lift,z=vz*damping+dz/divisor*acceleration;
        double speed=Math.sqrt(x*x+y*y+z*z),scale=speed>MAX_SPEED?MAX_SPEED/speed:1;
        return new Motion(x*scale,y*scale,z*scale);
    }
}
