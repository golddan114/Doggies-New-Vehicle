package com.liucodex.doggiesnewvehicle.artillery.walker;

import com.liucodex.doggiesnewvehicle.artillery.walker.HexbotGeometry.Point;

/** One server-owned foot state per leg. Ground queries are bounded by the runtime adapter. */
public final class HexbotGait {
    @FunctionalInterface public interface Ground {
        Point find(Point nominal);
        default Point contact(Point planted){return find(planted);}
    }
    public static final class Foot {
        private Point point,start,target;
        private boolean moving,supported;
        private double progress;
        private int duration;
        private int sinceStop=100;
        public Point point(){return point;}
        public boolean moving(){return moving;}
        public boolean supported(){return supported&&!moving;}
    }
    private final Foot[] feet=new Foot[6];
    private final Point[] ground=new Point[6];
    private Point lastBody;
    private double lastYaw;
    private int nextGroup;
    private static final int[][] GROUPS={{0,3,4},{1,2,5}};
    private int queries;
    public HexbotGait(){for(int i=0;i<6;i++)feet[i]=new Foot();}
    public Foot foot(int i){return feet[i];}
    public int queryCount(){return queries;}
    public void update(Point body,double yaw,Point velocity,Ground sampler) {
        queries=0;
        boolean reset=lastBody==null||lastBody.distance(body)>8;
        Point motion=reset?new Point(0,0,0):body.subtract(lastBody);
        double turn=reset?0:Math.IEEEremainder(yaw-lastYaw,Math.PI*2);
        turn=Math.max(-.15,Math.min(.15,turn));
        for(int i=0;i<6;i++) {
            Point rest=body.add(HexbotGeometry.rest(i).rotate(yaw));
            // Aim where the chassis will be near touchdown, including rotation.
            Point lead=HexbotGeometry.rest(i).rotate(yaw+turn*3).subtract(HexbotGeometry.rest(i).rotate(yaw))
                    .add(new Point(velocity.x(),0,velocity.z()).scale(4));
            if(lead.length()>.6)lead=lead.normalized().scale(.6);
            Point ahead=rest.add(lead);
            Point found=sampler.find(ahead);queries++;
            Point hip=body.add(HexbotGeometry.attachment(i).rotate(yaw));
            ground[i]=found!=null&&found.finite()&&found.distance(hip)<HexbotGeometry.reach(i)*.97?found:null;
            Foot f=feet[i];
            if(reset) {f.point=ground[i]!=null?ground[i]:rest;f.target=f.point;f.start=f.point;f.moving=false;}
            f.sinceStop++;
            Point contact=sampler.contact(f.point);queries++;
            f.supported=contact!=null&&contact.distance(f.point)<.055&&f.point.distance(hip)<=HexbotGeometry.reach(i);
            // An ungrounded foot is no longer a planted world anchor. Carry it with the body
            // until it can recover; never claim this air pose provides support.
            if(!reset&&!f.supported&&!f.moving){
                f.point=body.add(f.point.subtract(lastBody).rotate(turn));
            }
        }
        // Finish every active swing before scheduling. Iteration order must not starve other legs.
        for(int i=0;i<6;i++) {
            Foot f=feet[i];
            if(f.moving) {
                if(ground[i]==null) {
                    f.point=f.point.add(motion);
                    f.moving=false;f.supported=false;continue;
                }
                // Retarget without resetting elapsed progress: moving targets cannot prolong a step forever.
                f.target=ground[i];
                f.progress=Math.min(1,f.progress+1D/f.duration);
                f.point=f.start.lerp(f.target,f.progress).add(new Point(0,.35*4*f.progress*(1-f.progress),0));
                // The target was probed and reach-checked this tick. Count touchdown now,
                // before the opposite group competes for the remaining support budget.
                if(f.progress==1){f.moving=false;f.supported=true;f.sinceStop=0;}
            }
        }
        boolean swinging=false;for(Foot f:feet)swinging|=f.moving;
        if(!swinging)for(int attempt=0;attempt<2;attempt++){
            int group=(nextGroup+attempt)%2;boolean began=false;
            for(int i:GROUPS[group]){
                Foot f=feet[i];Point rest=body.add(HexbotGeometry.rest(i).rotate(yaw));
                double speed=Math.max(velocity.length(),motion.horizontalDistance(new Point(0,0,0)));
                // Translation and turning can pull the outside feet in the same direction.
                speed+=Math.abs(turn)*rest.horizontalDistance(body);
                double trigger=speed>.005||Math.abs(turn)>.001?Math.max(.25,.7-speed*2):.25;
                if(ground[i]==null||f.sinceStop<1)continue;
                Point nextChance=body.add(HexbotGeometry.rest(i).rotate(yaw+turn*2))
                        .add(new Point(velocity.x(),0,velocity.z()).scale(2));
                // At higher speed, account for the other group's swing before this leg
                // can request another step; otherwise a just-in-range foot misses a cycle.
                boolean needsStep=f.point.horizontalDistance(rest)>trigger
                        ||speed>.18&&f.point.horizontalDistance(nextChance)>trigger;
                if(f.supported&&(f.point.distance(ground[i])<.1||!needsStep))continue;
                // Recovering an already airborne foot removes no support. This also escapes
                // the all-unsupported deadlock after deployment, a drop or changed terrain.
                if(f.supported&&supportedCount()<=3)continue;
                f.moving=true;f.start=f.point;f.target=ground[i];f.progress=0;
                f.duration=(int)Math.max(2,Math.min(speed>.18?2:3,Math.ceil(f.start.distance(f.target)/.55)));
                began=true;
            }
            if(began){nextGroup=1-group;break;}
        }
        lastBody=body;lastYaw=yaw;
    }
    public int supportedCount(){int n=0;for(Foot f:feet)if(f.supported())n++;return n;}
    public double groundHeight(){double sum=0;int n=0;for(Point p:ground)if(p!=null){sum+=p.y();n++;}return n>=3?sum/n:Double.NaN;}
    public double pitch() {
        if(ground[0]==null||ground[1]==null||ground[4]==null||ground[5]==null)return 0;
        return Math.atan2((ground[0].y()+ground[1].y()-ground[4].y()-ground[5].y())*.5,2.9);
    }
    public double roll() {
        double left=0,right=0;int n=0;
        for(int i=0;i<6;i+=2)if(ground[i]!=null&&ground[i+1]!=null){left+=ground[i].y();right+=ground[i+1].y();n++;}
        return n==0?0:Math.atan2((right-left)/n,2.8);
    }
}
