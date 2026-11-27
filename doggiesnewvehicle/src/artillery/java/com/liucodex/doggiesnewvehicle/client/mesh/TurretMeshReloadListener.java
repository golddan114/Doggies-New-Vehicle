package com.liucodex.doggiesnewvehicle.client.mesh;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Parses all custom turret meshes on the resource-loading worker thread. */
public final class TurretMeshReloadListener
        extends SimplePreparableReloadListener<TurretMeshReloadListener.MeshBundle> {
    @Override
    protected MeshBundle prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return new MeshBundle(
                DnvTurretMesh.ENDFIELD.load(resourceManager),
                DnvTurretMesh.STORM.load(resourceManager),
                DnvTurretMesh.JINGZHE.load(resourceManager),
                DnvTurretMesh.M1.load(resourceManager),
                DnvTurretMesh.APOCALYPSE.load(resourceManager),
                DnvTurretMesh.PROJECT612.load(resourceManager),
                DnvTurretMesh.BLIZZARD.load(resourceManager),
                DnvTurretMesh.BLIZZARD_MISSILE.load(resourceManager),
                DnvTurretMesh.NUCLEAR_SILO.load(resourceManager),
                DnvTurretMesh.NUCLEAR_MISSILE.load(resourceManager),
                DnvTurretMesh.DOGEGG.load(resourceManager),
                NuclearVisualData.load(resourceManager),
                DnvTurretMesh.DRAGON.entrySet().stream().filter(entry->!entry.getKey().equals("m1_yunlong"))
                    .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey,entry->entry.getValue().load(resourceManager))));
    }

    @Override
    protected void apply(
            MeshBundle meshes,
            ResourceManager resourceManager,
            ProfilerFiller profiler) {
        DnvTurretMesh.ENDFIELD.install(meshes.endfield());
        DnvTurretMesh.STORM.install(meshes.storm());
        DnvTurretMesh.JINGZHE.install(meshes.jingzhe());
        DnvTurretMesh.M1.install(meshes.m1());
        DnvTurretMesh.APOCALYPSE.install(meshes.apocalypse());
        DnvTurretMesh.PROJECT612.install(meshes.project612());
        DnvTurretMesh.BLIZZARD.install(meshes.blizzard());
        DnvTurretMesh.BLIZZARD_MISSILE.install(meshes.missile());
        DnvTurretMesh.NUCLEAR_SILO.install(meshes.silo());
        DnvTurretMesh.NUCLEAR_MISSILE.install(meshes.nuclearMissile());
        DnvTurretMesh.DOGEGG.install(meshes.dogEgg());
        NuclearVisualData.install(meshes.nuclearVisuals());
        meshes.dragon().forEach((key,value)->DnvTurretMesh.DRAGON.get(key).install(value));
        var client=net.minecraft.client.Minecraft.getInstance();
        if(client.level!=null)for(var entity:client.level.entitiesForRendering())
            if(entity instanceof com.liucodex.doggiesnewvehicle.entity.DnvM1YunlongEntity m1)m1.clearVisualRecoil();
        com.liucodex.doggiesnewvehicle.client.BlizzardClientEffects.reload();
        com.liucodex.doggiesnewvehicle.client.NuclearClientEffects.reload();
        com.liucodex.doggiesnewvehicle.client.NuclearSiloSoundEffects.reload();
        com.liucodex.doggiesnewvehicle.client.DnvDynamicLights.reload();
        com.liucodex.doggiesnewvehicle.client.BlizzardMissileEffects.reset();
    }

    record MeshBundle(
            DnvTurretMesh.MeshData endfield,
            DnvTurretMesh.MeshData storm,
            DnvTurretMesh.MeshData jingzhe,
            DnvTurretMesh.MeshData m1,
            DnvTurretMesh.MeshData apocalypse,
            DnvTurretMesh.MeshData project612,
            DnvTurretMesh.MeshData blizzard,
            DnvTurretMesh.MeshData missile,
            DnvTurretMesh.MeshData silo,
            DnvTurretMesh.MeshData nuclearMissile,
            DnvTurretMesh.MeshData dogEgg,
            NuclearVisualData nuclearVisuals,
            java.util.Map<String,DnvTurretMesh.MeshData> dragon) {
    }
}
