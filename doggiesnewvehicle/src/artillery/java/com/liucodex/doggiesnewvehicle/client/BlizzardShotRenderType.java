package com.liucodex.doggiesnewvehicle.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** The narrow shot core participates in scene depth even when later entities render afterward. */
final class BlizzardShotRenderType extends RenderType {
    static final RenderType BEAM_GLOW = beam("dnv_blizzard_beam_glow", COLOR_WRITE);
    static final RenderType BEAM_CORE = beam("dnv_blizzard_beam_core", COLOR_DEPTH_WRITE);
    private static RenderType beam(String name, WriteMaskStateShard mask) {
        return create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false,
                CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("superbwarfare", "textures/entity/white.png"), false, false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(mask).setOutputState(PARTICLES_TARGET)
                        .setCullState(NO_CULL).setOverlayState(OVERLAY).createCompositeState(false));
    }
    static final RenderType CORE = create("dnv_blizzard_shot_core", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(
                            ResourceLocation.fromNamespaceAndPath("superbwarfare", "textures/entity/white.png"), false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL).setOverlayState(OVERLAY).createCompositeState(false));

    private BlizzardShotRenderType() {
        super("dnv_blizzard_shot_core", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                256, false, false, () -> { }, () -> { });
    }
}
