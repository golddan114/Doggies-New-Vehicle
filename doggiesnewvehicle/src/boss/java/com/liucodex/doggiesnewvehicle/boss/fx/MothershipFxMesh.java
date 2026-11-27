package com.liucodex.doggiesnewvehicle.boss.fx;

import com.liucodex.doggiesnewvehicle.boss.MothershipDoomsday;
import com.liucodex.doggiesnewvehicle.boss.MothershipArrival;
import java.util.List;

/** Bounded quad geometry shared by the Minecraft renderer and offline preview exporter. */
public final class MothershipFxMesh {
    @FunctionalInterface
    public interface Sink {
        void vertex(double x, double y, double z, float red, float green, float blue, float alpha);
    }

    private static final double[] X = new double[8];
    private static final double[] Y = new double[8];
    private static final List<MothershipDoomsday.FireSite> FIRE_SITES =
            MothershipDoomsday.ignitionSites(MothershipDoomsday.DEFAULT_BLAST_RADIUS);
    static {
        for (int i = 0; i < 8; i++) {
            X[i] = Math.cos(i * Math.PI / 4);
            Y[i] = Math.sin(i * Math.PI / 4);
        }
    }

    private MothershipFxMesh() { }

    /** Local +Z is the shot direction. Every beam layer begins at exactly (0,0,0). */
    public static void lance(Sink sink, MothershipFx.Sample sample, double clippedLength,
            MothershipFx.Detail detail, double tick, boolean surfaceImpact) {
        double length = MothershipFx.length(clippedLength);
        if (detail == MothershipFx.Detail.HIDDEN || length < 0.01 || !Double.isFinite(tick)) return;
        int step = detail == MothershipFx.Detail.NEAR ? 1 : 2;
        if(sample.phase()==MothershipFx.Phase.ABORT && sample.heat()>0) {
            double radius=.3+sample.charge()*(MothershipFx.ORB_RADIUS-.3);
            float alpha=(float)sample.heat();
            frustum(sink,0,0,-radius,0,0,radius,step,1,.65f,.1f,0,.65f*alpha);
            frustum(sink,0,0,0,Math.min(length,radius),radius,0,step,1,.95f,.65f,.8f*alpha,0);
            if(sample.charge()>0)ring(sink,.1,10+18*(1-sample.charge()),.55,step,.5f*alpha);
        }
        if (sample.phase() == MothershipFx.Phase.CHARGE || sample.phase() == MothershipFx.Phase.LOCK
                || sample.phase() == MothershipFx.Phase.FIRE) {
            double r = 0.3 + sample.charge() * (MothershipFx.ORB_RADIUS - .3);
            frustum(sink, 0, 0, -r, 0, 0, r, step, 1, 0.65f, 0.1f, 0, 0.65f);
            frustum(sink, 0, 0, 0, Math.min(length, r), r, 0, step, 1, 0.95f, 0.65f, 0.8f, 0);
            double charge = sample.charge();
            if (charge > 0) {
                // A broad rotating aperture contracts toward the bright core. Pure projection, no world light or scans.
                double aperture = sample.phase() == MothershipFx.Phase.FIRE ? 10 : 28 - 18 * charge;
                int rings = step == 1 ? 3 : 1;
                for (int band = 0; band < rings; band++) {
                    double angle = tick * .012 * (band % 2 == 0 ? 1 : -1) + band * .6;
                    double c = Math.cos(angle), s = Math.sin(angle);
                    ring((x,y,z,red,g,b,a) -> sink.vertex(x*c-y*s,x*s+y*c,z,1,.68f,.18f,a),
                            Math.min(length, .1 + band * .45), aperture * (1 - band * .17),
                            .55 + 1.2 * charge, step, (float)(.2 + charge * .55));
                }
                if(step==1) {
                    // A shallow volume makes the aperture readable from the side as well as head-on.
                    frustum(sink,0,0,0,Math.min(length,4.5),r,aperture,1,1,.5f,.08f,.15f,0);
                    frustum(sink,0,0,0,Math.min(length,2.7),r*.7,aperture*.65,1,1,.85f,.3f,.22f,0);
                }
            }
        }
        if (sample.beam() > 0) {
            double pulse = 0.97 + 0.03 * Math.sin(tick * 1.7);
            double r = (1 + 7 * sample.beam()) * pulse;
            double neck = Math.min(length, 5);
            // The core is always present, including the far-distance fallback.
            frustum(sink, 0, 0, 0, neck, 0, r * .24, step, 1, .99f, .88f, 1, 1);
            frustum(sink, 0, 0, neck, length, r * .24, r * .24, step, 1, .99f, .88f, 1, .95f);
            if (step == 1) {
                frustum(sink, 0, 0, 0, neck, 0, r*.52, 1, 1, .68f, .12f, .5f, .5f);
                frustum(sink, 0, 0, neck, length, r*.52, r*.52, 1, 1, .68f, .12f, .5f, .35f);
                frustum(sink, 0, 0, 0, neck, 0, r, 1, 1, .28f, .02f, .16f, .2f);
                frustum(sink, 0, 0, neck, length, r, r, 1, 1, .28f, .02f, .2f, .08f);
                for(int i=0;i<3;i++) {
                    double travel=((tick-MothershipFx.CHARGE_TICKS)*.075+i/3.0)%1;
                    if(travel>=0)ring(sink,Math.max(.001,length*travel),r*(1.1+.3*Math.sin(travel*Math.PI)),.65,1,
                            (float)(.55*Math.sin(travel*Math.PI)));
                }
            }
            // Counter-rotating plasma filaments move along the beam, including the far fallback.
            int strands = step == 1 ? 6 : 2, segments = step == 1 ? 16 : 6;
            // The crossed ribbon has a longitudinal width component. Keep filaments
            // just beyond the shared muzzle point and inside the confirmed surface.
            Sink clipped = (x, y, z, red, green, blue, alpha) ->
                    sink.vertex(x, y, Math.max(1.0E-6, Math.min(length, z)), red, green, blue, alpha);
            for (int strand = 0; strand < strands; strand++) for (int part = 0; part < segments; part++) {
                double a = part / (double)segments, b = (part + 1D) / segments;
                double angleA = strand * Math.PI * 2 / strands + a * Math.PI * 5 - tick * .14;
                double angleB = strand * Math.PI * 2 / strands + b * Math.PI * 5 - tick * .14;
                double ra = r * .65 * Math.sin(a * Math.PI), rb = r * .65 * Math.sin(b * Math.PI);
                ribbon(clipped, Math.cos(angleA) * ra, Math.sin(angleA) * ra, neck + (length - neck) * a,
                        Math.cos(angleB) * rb, Math.sin(angleB) * rb, neck + (length - neck) * b,
                        .16 + sample.beam() * .3, 1, .84f, .4f, .8f);
            }
            if(sample.phase()==MothershipFx.Phase.FIRE) for(int ordinal=1;ordinal<=4;ordinal++) {
                double launchAge=tick-MothershipFx.damageTick(ordinal);
                if(launchAge>=0 && launchAge<12)
                    ring(sink,Math.min(length,.08),2+launchAge*1.05,.55,step,(float)(1-launchAge/12));
            }
            if (surfaceImpact) {
                // A bounded flare at the supplied solid-surface endpoint; never perform world queries here.
                burst(sink, length, r * 2, step, .75f);
                double buildup = MothershipFx.smooth((tick - MothershipFx.CHARGE_TICKS - MothershipFx.FIRE_TICKS/2.0)
                        / (MothershipFx.FIRE_TICKS/2.0));
                if (buildup > 0) radialStreams(sink, length, 3 + 13 * buildup, tick, step, (float)buildup);
            }
        }
    }

    /** Aft-engine origin to bow endpoint in a common world-aligned frame, independent of beam aim. */
    public static void gather(Sink sink, MothershipFx.Sample sample, double endX, double endY, double endZ,
            int stream, double tick, MothershipFx.Detail detail) {
        if (detail == MothershipFx.Detail.HIDDEN || !Double.isFinite(tick) || sample.charge() <= 0
                || sample.phase() != MothershipFx.Phase.CHARGE && sample.phase() != MothershipFx.Phase.FIRE
                    && sample.phase() != MothershipFx.Phase.ABORT) return;
        double length = Math.sqrt(endX * endX + endY * endY + endZ * endZ);
        if (!Double.isFinite(length) || length < .01 || length > 192) return;
        if (detail == MothershipFx.Detail.FAR && stream % 3 != 0) return;
        double horizontal = Math.hypot(endX, endZ), sideX = horizontal > .01 ? endZ / horizontal : 1;
        double sideZ = horizontal > .01 ? -endX / horizontal : 0;
        double angle = stream * Math.PI * 2 / 10;
        int segments = detail == MothershipFx.Detail.NEAR ? 14 : 6;
        for (int part = 0; part < segments; part++) {
            double a = part / (double)segments, b = (part + 1D) / segments;
            double arcA = Math.sin(a * Math.PI), arcB = Math.sin(b * Math.PI);
            double sideA = Math.cos(angle) * 24 * arcA, sideB = Math.cos(angle) * 24 * arcB;
            double yA = Math.sin(angle) * 14 * arcA, yB = Math.sin(angle) * 14 * arcB;
            double packet = Math.pow(.5 + .5 * Math.cos((a + b) * 16 - tick * .24 + stream * .8), 8);
            float alpha = (float)(sample.charge() * (.14 + .86 * packet));
            ribbon(sink, endX * a + sideX * sideA, endY * a + yA, endZ * a + sideZ * sideA,
                    endX * b + sideX * sideB, endY * b + yB, endZ * b + sideZ * sideB,
                    .18 + sample.charge() * (.35 + packet * .65), 1, .66f + (float)packet * .3f, .16f + (float)packet * .65f, alpha);
        }
    }

    private static void radialStreams(Sink sink, double z, double radius, double tick, int step, float alpha) {
        int streams = step == 1 ? 16 : 6, segments = step == 1 ? 6 : 3;
        for (int stream = 0; stream < streams; stream++) for (int part = 0; part < segments; part++) {
            double a = part / (double)segments, b = (part + 1D) / segments;
            double theta = stream * Math.PI * 2 / streams + Math.sin(tick * .045) * .12;
            double bendA = theta + a * .6, bendB = theta + b * .6;
            double za = z + Math.sin(stream * 2.4) * radius * a * .6;
            double zb = z + Math.sin(stream * 2.4) * radius * b * .6;
            ribbon(sink, Math.cos(bendA) * radius * a, Math.sin(bendA) * radius * a, za,
                    Math.cos(bendB) * radius * b, Math.sin(bendB) * radius * b, zb,
                    .25 + .6 * (1 - a), 1, .83f, .4f, alpha * (float)(1 - a * .75));
        }
    }

    /** Two crossed ribbons retain visible volume from every view; no per-frame particle allocation. */
    private static void ribbon(Sink sink, double ax, double ay, double az, double bx, double by, double bz,
            double width, float red, float green, float blue, float alpha) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, flat = Math.hypot(dx, dz);
        double nx = flat > 1e-8 ? -dz / flat : 1, nz = flat > 1e-8 ? dx / flat : 0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-8) return;
        for (int plane = 0; plane < 2; plane++) {
            double x = plane == 0 ? nx * width : -dy * nz / len * width;
            double y = plane == 0 ? 0 : (dz * nx - dx * nz) / len * width;
            double z = plane == 0 ? nz * width : dy * nx / len * width;
            sink.vertex(ax - x, ay - y, az - z, red, green, blue, alpha);
            sink.vertex(ax + x, ay + y, az + z, red, green, blue, alpha);
            sink.vertex(bx + x * .7, by + y * .7, bz + z * .7, red, green, blue, alpha * .65f);
            sink.vertex(bx - x * .7, by - y * .7, bz - z * .7, red, green, blue, alpha * .65f);
        }
    }

    /** Flat-ground preview only. Real fire must use validated server block positions, not this plane. */
    public static void groundFirePreview(Sink sink, double tick, MothershipFx.Detail detail) {
        double age = tick - MothershipFx.damageTick(4);
        if (!Double.isFinite(age) || age < 24 || age >= MothershipFx.COOL_TICKS || detail == MothershipFx.Detail.HIDDEN) return;
        int stride = detail == MothershipFx.Detail.NEAR ? 1 : 4;
        float alpha = (float) (0.7 * MothershipFx.clamp((MothershipFx.COOL_TICKS - age) / 40));
        for (int i = 0; i < FIRE_SITES.size(); i += stride) {
            var site = FIRE_SITES.get(i);
            double height = 0.7 + 0.25 * Math.sin(tick * 0.4 + i * 1.7);
            double x = site.x();
            double z = site.z();
            // Two crossed tapered quads, no animated particle or volumetric light.
            for (int axis = 0; axis < 2; axis++) {
                double dx = axis == 0 ? 0.42 : 0;
                double dz = axis == 1 ? 0.42 : 0;
                sink.vertex(x - dx, 0.02, z - dz, 1, 0.35f, 0.02f, alpha);
                sink.vertex(x + dx, 0.02, z + dz, 1, 0.35f, 0.02f, alpha);
                sink.vertex(x + dx * 0.2, height, z + dz * 0.2, 1, 0.8f, 0.15f, 0);
                sink.vertex(x - dx * 0.2, height, z - dz * 0.2, 1, 0.8f, 0.15f, 0);
            }
        }
    }

    /** Four footfall warnings followed by four growing blasts; final visual is 70% of Kirov's radius 42. */
    public static void groundPulse(Sink sink, double tick, MothershipFx.Detail detail) {
        if (detail == MothershipFx.Detail.HIDDEN || !Double.isFinite(tick)) return;
        int step = detail == MothershipFx.Detail.NEAR ? 1 : 2;
        Sink ground = (x,y,z,r,g,b,a) -> sink.vertex(x,z,y,r,g,b,a);
        for (int ordinal = 1; ordinal <= 4; ordinal++) {
            double age = tick - MothershipFx.damageTick(ordinal);
            double gameplayRadius = MothershipDoomsday.DEFAULT_BLAST_RADIUS * ordinal / 4;
            if (age >= -7 && age < 0) {
                double progress = (age + 7) / 7;
                ring(ground, .06, .5 + gameplayRadius * progress, .35 + progress * .55,
                        step, (float)(.35 + .55 * progress));
            }
            double lifetime = ordinal == 4 ? MothershipFx.TERMINAL_TICKS : 12;
            if (age >= 0 && age < lifetime) {
                double visualRadius = ordinal == 4 ? MothershipDoomsday.FINAL_VISUAL_RADIUS : gameplayRadius;
                double spread = MothershipFx.smooth(age / (ordinal == 4 ? 24 : 8));
                float alpha = (float)(1 - MothershipFx.smooth(age / lifetime));
                double radius = 1 + (visualRadius - 1) * spread;
                ring(ground, .08, radius, ordinal == 4 ? 2.2 : 1.2, step, alpha);
                burst(ground, .7, Math.max(.1, radius * .65), step, alpha * .75f);
                if (ordinal == 4) ring(ground, .12, radius * .72, 1.3, step, alpha * .65f);
            }
        }
    }

    /** Only the exterior halo brightens; the authored hull and emission texture stay untouched. */
    public static void hullCharge(Sink sink, MothershipFx.Sample sample, MothershipFx.Detail detail, double tick) {
        if (detail == MothershipFx.Detail.HIDDEN || !Double.isFinite(tick)) return;
        double brightness=switch(sample.phase()) {
            case LOCK -> .06+.12*MothershipFx.smooth(tick/MothershipDoomsday.LOCK_TICKS);
            case CHARGE -> .18+.82*sample.charge();
            case FIRE -> 1;
            case COOL, ABORT -> sample.heat();
            default -> 0;
        };
        if(brightness<=0)return;
        int step = detail == MothershipFx.Detail.NEAR ? 1 : 2;
        double charge = brightness;
        Sink horizontal = (x,y,z,r,g,b,a) -> sink.vertex(x,z,y,r,g,b,a);
        double shimmer = .95 + .05 * Math.sin(tick * .17);
        for (int band = 0; band < 3; band++) {
            double radius = 22 + band * 8 + charge * (5 - band);
            double height = 8 + band * 7;
            ring(horizontal, height, radius, .7 + charge * 1.2, step,
                    (float)((.05 + charge * (.35 + band * .08)) * shimmer));
        }
    }

    /** Six-second ground warning; the ship remains hidden until the warp exit. */
    public static void arrivalBeacon(Sink sink,double tick,double height,MothershipFx.Detail detail) {
        if(detail==MothershipFx.Detail.HIDDEN||!Double.isFinite(tick)||!Double.isFinite(height)||height<=0)return;
        double after=tick-MothershipArrival.BEACON_TICKS;
        if(after>=12)return;
        double progress=MothershipFx.smooth(tick/MothershipArrival.BEACON_TICKS);
        double fade=after<=0?1:1-MothershipFx.smooth(after/12);
        int step=detail==MothershipFx.Detail.NEAR?1:2;
        Sink horizontal=(x,y,z,r,g,b,a)->sink.vertex(x,z,y,r,g,b,a);
        double pulse=.86+.14*Math.sin(tick*.22);
        for(int band=0;band<3;band++) {
            double radius=2.5+band*2.8+progress*5.5;
            ring(horizontal,.07+band*.025,radius,.35+progress*.5,step,
                    (float)((.22+progress*.42)*pulse*fade));
        }
        double columnHeight=Math.min(128,height);
        float alpha=(float)((.08+.25*progress)*fade);
        for(int side=0;side<2;side++) {
            double width=.35+progress*.45;
            double x=side==0?width:0,z=side==0?0:width;
            sink.vertex(-x,0,-z,1,.78f,.38f,alpha);
            sink.vertex(x,0,z,1,.78f,.38f,alpha);
            sink.vertex(x,columnHeight,z,1,.94f,.75f,0);
            sink.vertex(-x,columnHeight,-z,1,.94f,.75f,0);
        }
    }

    /** Ship-local warp streak collapses at the exit; the hull fades in over forty ticks. */
    public static void arrivalWarp(Sink sink,double tick,MothershipFx.Detail detail) {
        if(detail==MothershipFx.Detail.HIDDEN||!Double.isFinite(tick))return;
        double age=tick-MothershipArrival.BEACON_TICKS;
        if(age<0||age>=MothershipArrival.REVEAL_TICKS)return;
        int step=detail==MothershipFx.Detail.NEAR?1:2;
        double flash=1-MothershipFx.smooth(age/14);
        if(flash>0) {
            double length=2+43*flash;
            frustum(sink,0,0,-length,length,2.4*flash+.15,.2,step,
                    .72f,.9f,1,(float)(.7*flash),0);
            if(step==1)frustum(sink,0,0,-length*.8,length*.8,4.5*flash+.2,.7,step,
                    1,.61f,.2f,(float)(.25*flash),0);
        }
        double ringLife=1-MothershipFx.smooth(age/32);
        double radius=5+35*MothershipFx.smooth(age/20);
        for(int i=0;i<8;i+=step) {
            int j=(i+step)%8;
            double inner=radius,outer=radius+1.4+ringLife*2;
            float alpha=(float)(.65*ringLife);
            sink.vertex(X[i]*inner,Y[i]*inner,0,.75f,.9f,1,alpha);
            sink.vertex(X[j]*inner,Y[j]*inner,0,.75f,.9f,1,alpha);
            sink.vertex(X[j]*outer,Y[j]*outer,0,1,.68f,.25f,0);
            sink.vertex(X[i]*outer,Y[i]*outer,0,1,.68f,.25f,0);
        }
    }

    /** Nozzle at local origin, exhaust along local +Z; three spans, at most two layers. */
    public static void exhaust(Sink sink, double thrust, double radius, double tick, MothershipFx.Detail detail) {
        com.liucodex.doggiesnewvehicle.fx.DnvJetMesh.exhaust(sink::vertex, thrust, radius, tick,
                detail == MothershipFx.Detail.HIDDEN ? 0 : detail == MothershipFx.Detail.NEAR ? 2 : 1);
    }

    private static void frustum(Sink sink, double cx, double cy, double z0, double z1, double r0, double r1,
            int step, float red, float green, float blue, float a0, float a1) {
        if (Math.abs(z1 - z0) < 0.000001) return;
        for (int i = 0; i < 8; i += step) {
            int j = (i + step) % 8;
            sink.vertex(cx + X[i] * r0, cy + Y[i] * r0, z0, red, green, blue, a0);
            sink.vertex(cx + X[j] * r0, cy + Y[j] * r0, z0, red, green, blue, a0);
            sink.vertex(cx + X[j] * r1, cy + Y[j] * r1, z1, red, green, blue, a1);
            sink.vertex(cx + X[i] * r1, cy + Y[i] * r1, z1, red, green, blue, a1);
        }
    }

    /** An eight-sided ring beneath each bay grows with the synchronized server charge. */
    public static void dropCharge(Sink sink,double charge,double tick) {
        charge=MothershipFx.clamp(charge);
        if(charge<=0||!Double.isFinite(tick))return;
        double radius=.18+.62*charge;
        float alpha=(float)((.16+.55*charge)*(0.93+.07*Math.sin(tick*.1)));
        ring((x,y,z,r,g,b,a)->sink.vertex(x,-.02,y,.25f,.8f,1,a),0,radius,.035,1,alpha);
    }

    private static void ring(Sink sink, double z, double radius, double width, int step, float alpha) {
        for (int i = 0; i < 8; i += step) {
            int j = (i + step) % 8;
            sink.vertex(X[i] * radius, Y[i] * radius, z, 1, 0.35f, 0.04f, alpha);
            sink.vertex(X[j] * radius, Y[j] * radius, z, 1, 0.35f, 0.04f, alpha);
            sink.vertex(X[j] * (radius + width), Y[j] * (radius + width), z, 1, 0.65f, 0.2f, 0);
            sink.vertex(X[i] * (radius + width), Y[i] * (radius + width), z, 1, 0.65f, 0.2f, 0);
        }
    }

    private static void burst(Sink sink, double z, double radius, int step, float alpha) {
        frustum(sink, 0, 0, z - radius, z, 0, radius, step, 1, 0.75f, 0.3f, 0, alpha);
        frustum(sink, 0, 0, z, z + radius, radius, 0, step, 1, 0.75f, 0.3f, alpha, 0);
    }
}
