package com.liucodex.doggiesnewvehicle.client.weapons;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Rigid camera-space alignment of the actual DSX1 pupil; no world/camera mutation. */
public record Dsx1Optics(float x, float y, float rearZ, float frontZ, float halfWidth, float halfHeight) {
    public static Dsx1Optics from(Dsx1Mesh.Part glass) {
        if (glass == null || glass.triangles() == 0) return null;
        float rear = -Float.MAX_VALUE, front = Float.MAX_VALUE;
        for (int c = 0; c < glass.triangles() * 3; c++) {
            rear = Math.max(rear, glass.value(c, 2)); front = Math.min(front, glass.value(c, 2));
        }
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int c = 0; c < glass.triangles() * 3; c++) {
            if (Math.abs(glass.value(c, 2) - rear) > .00001f) continue;
            minX = Math.min(minX, glass.value(c, 0)); maxX = Math.max(maxX, glass.value(c, 0));
            minY = Math.min(minY, glass.value(c, 1)); maxY = Math.max(maxY, glass.value(c, 1));
        }
        if (!(maxX > minX && maxY > minY && rear > front)) return null;
        return new Dsx1Optics((minX + maxX) / 2, (minY + maxY) / 2, rear, front,
                (maxX - minX) / 2, (maxY - minY) / 2);
    }

    public float reticleZ() { return frontZ + .006f; }
    // Art occupies radius 178/1024 of its transparent canvas; keep it within the pupil.
    public float reticleSize() { return Math.min(halfWidth, halfHeight) * .86f * 1024 / 178; }

    /** Returns the correction in the item's render space. */
    public Matrix4f renderCorrection(Matrix4fc modelView, Matrix4fc reference, Matrix4fc projection, double ads) {
        return renderCorrection(modelView, reference, reference, projection, ads);
    }

    public Matrix4f renderCorrection(Matrix4fc modelView, Matrix4fc reference, Matrix4fc actual,
            Matrix4fc projection, double ads) {
        if (!Double.isFinite(ads) || ads <= 0 || !finite(modelView) || !finite(reference)
                || !finite(actual) || !finite(projection) || Math.abs(modelView.determinant()) < 1e-8f) return new Matrix4f();
        // 1.21.1 splits the camera between ModelView and inverse(camera) in the item pose.
        // Align in the space actually seen by the shader, then return to the item's space.
        var cameraReference = new Matrix4f(modelView).mul(reference);
        var cameraCorrection = correction(cameraReference, projection, ads);
        // Recoil is above the compressed gun bone. Keep its real motion, only limiting
        // camera penetration when the actual pupil would cross the projection's near plane.
        float near = projection.m32() / (projection.m22() - 1);
        if (Float.isFinite(near) && near > 0 && projection.m23() < -.5f) {
            var cameraActual = new Matrix4f(cameraCorrection).mul(modelView).mul(actual);
            float nearest = -Float.MAX_VALUE;
            for (float z : new float[]{rearZ,frontZ}) for (int sx : new int[]{-1,1}) for (int sy : new int[]{-1,1}) {
                var point = cameraActual.transformPosition(new Vector3f(x+sx*halfWidth,y+sy*halfHeight,z));
                nearest = Math.max(nearest,point.z);
            }
            float retreat = Math.max(0,near+.008f+nearest);
            cameraCorrection.m30(cameraCorrection.m30()+retreat*projection.m20()/projection.m00());
            cameraCorrection.m31(cameraCorrection.m31()+retreat*projection.m21()/projection.m11());
            cameraCorrection.m32(cameraCorrection.m32()-retreat);
        }
        return new Matrix4f(modelView).invert().mul(cameraCorrection).mul(modelView);
    }

    /** reference includes item display, root/depth scale and native idle pose, excluding recoil. */
    public Matrix4f correction(Matrix4fc reference, Matrix4fc projection, double ads) {
        if (!Double.isFinite(ads) || ads <= 0 || !finite(reference) || !finite(projection)
                || Math.abs(reference.determinant()) < 1e-8f || Math.abs(projection.m11()) < .001f
                || Math.abs(projection.m00()) < .001f) return new Matrix4f();
        // Finish optical alignment before the strongest depth compression. Delaying both until
        // the end made the unaligned long tunnel briefly pinch shut, then reopen at full ADS.
        float u = (float)Math.min(1, ads * 2); u = u * u * (3 - 2 * u);
        var centre = reference.transformPosition(new Vector3f(x, y, rearZ));
        var forward = reference.transformDirection(new Vector3f(0, 0, -1)).normalize();
        var up = reference.transformDirection(new Vector3f(0, 1, 0));
        var ray = new Vector3f(projection.m20() / projection.m00(), projection.m21() / projection.m11(), -1);
        var direction = new Vector3f(ray).normalize();
        var aim = new Quaternionf().rotationTo(forward, direction);
        aim.transform(up);
        up.fma(-up.dot(direction), direction).normalize();
        var right = new Vector3f(1, 0, ray.x).normalize();
        var targetUp = new Vector3f(right).cross(direction).normalize();
        float roll = (float)Math.atan2(direction.dot(new Vector3f(up).cross(targetUp)), up.dot(targetUp));
        var targetRotation = new Quaternionf().rotationAxis(roll, direction).mul(aim);
        var extentX = reference.transformDirection(new Vector3f(halfWidth, 0, 0));
        var extentY = reference.transformDirection(new Vector3f(0, halfHeight, 0));
        targetRotation.transform(extentX); targetRotation.transform(extentY);
        float radiusY = Math.abs(projection.m11() * extentX.y + projection.m21() * extentX.z)
                + Math.abs(projection.m11() * extentY.y + projection.m21() * extentY.z);
        float radiusX = Math.abs(projection.m00() * extentX.x + projection.m20() * extentX.z)
                + Math.abs(projection.m00() * extentY.x + projection.m20() * extentY.z);
        float depthMargin = Math.abs(extentX.z) + Math.abs(extentY.z);
        // Fit the FAR mouth of the full tunnel, not just the rear glass. Its depth used to
        // shrink the usable window even when the rear pupil alone passed the footprint test.
        var span = reference.transformDirection(new Vector3f(0, 0, frontZ - rearZ));
        targetRotation.transform(span);
        float distance = Math.max(.025f, depthMargin + Math.max(radiusY / .72f, radiusX / .76f) + span.z);
        var target = new Vector3f(centre).lerp(new Vector3f(ray).mul(distance), u);
        var rotation = new Quaternionf().slerp(targetRotation, u);
        return new Matrix4f().translation(target).rotate(rotation).translate(-centre.x, -centre.y, -centre.z);
    }

    private static boolean finite(Matrix4fc matrix) {
        return Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01()) && Float.isFinite(matrix.m02()) && Float.isFinite(matrix.m03())
            && Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11()) && Float.isFinite(matrix.m12()) && Float.isFinite(matrix.m13())
            && Float.isFinite(matrix.m20()) && Float.isFinite(matrix.m21()) && Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m23())
            && Float.isFinite(matrix.m30()) && Float.isFinite(matrix.m31()) && Float.isFinite(matrix.m32()) && Float.isFinite(matrix.m33());
    }
}
