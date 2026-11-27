package com.liucodex.noise.client.mesh;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class NoiseDropRigMeshReloadListener
        extends SimplePreparableReloadListener<NoiseDropRigMesh> {
    @Override
    protected NoiseDropRigMesh prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return NoiseDropRigMesh.load(resourceManager);
    }

    @Override
    protected void apply(
            NoiseDropRigMesh mesh,
            ResourceManager resourceManager,
            ProfilerFiller profiler) {
        NoiseDropRigMesh.install(mesh);
    }
}


