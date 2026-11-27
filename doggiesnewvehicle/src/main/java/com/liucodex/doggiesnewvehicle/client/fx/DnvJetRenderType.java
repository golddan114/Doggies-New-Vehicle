package com.liucodex.doggiesnewvehicle.client.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Alpha-weighted light over completed scene depth; transparent tips never occlude later effects. */
public final class DnvJetRenderType extends RenderType {
    public static final RenderType TYPE = textured(
            ResourceLocation.fromNamespaceAndPath("superbwarfare", "textures/entity/white.png"));

    /** Cache the returned type in the owning renderer; no per-frame allocation. */
    public static RenderType textured(ResourceLocation texture) {
        return create("dnv_jet_fx", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new TextureStateShard(
                            texture, false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false));
    }

    private DnvJetRenderType() {
        super("dnv_jet_fx", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                false, false, () -> { }, () -> { });
    }
}
