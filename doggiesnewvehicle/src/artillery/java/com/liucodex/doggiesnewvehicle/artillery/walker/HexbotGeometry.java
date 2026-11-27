package com.liucodex.doggiesnewvehicle.artillery.walker;

/** Pinned upstream hexBot(4,1) dimensions; Minecraft +Y up, +Z forward. */
public final class HexbotGeometry {
    public static final int LEGS = 6;
    public static final double BODY_HEIGHT = 1.1;
    public record Point(double x, double y, double z) {
        public Point add(Point p) { return new Point(x+p.x,y+p.y,z+p.z); }
        public Point subtract(Point p) { return new Point(x-p.x,y-p.y,z-p.z); }
        public Point scale(double s) { return new Point(x*s,y*s,z*s); }
        public double length() { return Math.sqrt(x*x+y*y+z*z); }
        public double distance(Point p) { return subtract(p).length(); }
        public double horizontalDistance(Point p) { return Math.hypot(x-p.x,z-p.z); }
        public Point normalized() { double n=length(); return n<1e-10?new Point(0,0,1):scale(1/n); }
        public Point lerp(Point p,double t) { return scale(1-t).add(p.scale(t)); }
        public Point rotate(double yaw) {
            double c=Math.cos(yaw),s=Math.sin(yaw);
            return new Point(x*c+z*s,y,z*c-x*s);
        }
        public boolean finite() { return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z); }
    }
    private HexbotGeometry() { }
    public static Point attachment(int leg) { return new Point(leg%2==0?.2:-.2,-.35,.2-(leg/2)*.2); }
    public static Point rest(int leg) {
        double[] x={1.3,1.56,1.43},z={1.3,-.1,-1.6};
        return new Point(x[leg/2]*(leg%2==0?1:-1),-BODY_HEIGHT,z[leg/2]);
    }
    public static double[] lengths(int leg) {
        double s=(leg/2==2?1.3:1.1)*.7;
        return new double[]{s*.5,s*.8,s,s};
    }
    public static double reach(int leg) { double n=0; for(double v:lengths(leg))n+=v; return n; }
}
