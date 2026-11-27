"""Loader-specific APIs for the shared Yue and Feiying code after common projection."""

def project_aviation(text, path):
    changes = []
    def replace(old, new):
        nonlocal text
        if old not in text:
            raise ValueError(f"Aviation projection drift in {path}: {old}")
        text = text.replace(old, new)
        changes.append(dict(before=old, after=new))

    file = path.rsplit('/', 1)[-1]
    if file == 'CubePrototype.java':
        replace('import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;', 'import net.neoforged.bus.api.IEventBus;')
        replace('public CubePrototype() {\n        var modBus = FMLJavaModLoadingContext.get().getModEventBus();',
                'public CubePrototype(IEventBus modBus) {\n        CarrierTickets.register(modBus);')
        replace('RegistryObject<net.minecraft.sounds.SoundEvent>', 'DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent>')
    elif file == 'CarrierAssemblyMixin.java':
        replace('CompoundTag box = BlockItem.getBlockEntityData(stack);\n        if (box == null || box.contains("Entity")) return;',
                'var data = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);\n        if (data == null || data.contains("Entity")) return;\n        CompoundTag box = data.copyTag();')
    elif file == 'CubeAimPoint.java':
        replace('CHANNEL.messageBuilder(CubeAimPoint.class,0,Direction.PLAY_TO_SERVER)\n                .encoder(', 'CHANNEL.registerMessage(0,CubeAimPoint.class,')
        replace('})\n                .decoder(', '}, ')
        replace('buf.readDouble())))\n                .consumerMainThread((msg,ctx)->{',
                'buf.readDouble())), (msg,ctx)->ctx.get().enqueueWork(()->{')
        replace('                    ctx.get().setPacketHandled(true);\n                }).add();', '                }), java.util.Optional.of(Direction.PLAY_TO_SERVER));')
    elif file in ('CubeHangar.java', 'CubeVehicle.java'):
        if file == 'CubeVehicle.java':
            for world in ('world','server'):
                replace('net.neoforged.neoforge.common.world.ForgeChunkManager.forceChunk('+world+',CubePrototype.ID,','CarrierTickets.forceChunk('+world+',')
            replace('isAddedToWorld()', 'isAddedToLevel()')
            replace('oldStack.putByte("Count", (byte)1)', 'oldStack.putInt("count", 1)')
            replace('ItemStack.of(oldStack)', 'ItemStack.parseOptional(registryAccess(), oldStack)')
            replace('int steps, boolean teleport)', 'int steps)')
            replace('pitch,steps,teleport)', 'pitch,steps)')
            replace('BlockItem.getBlockEntityData(hangar().box(bay))', 'CubeHangar.crateData(hangar().box(bay))')
            replace('hangar().save()', 'hangar().save(registryAccess())')
            replace('hangar().restore(state.getCompound("hangar"))', 'hangar().restore(state.getCompound("hangar"),registryAccess())')
        else:
            replace('    static boolean allowed(ItemStack stack)', '    static CompoundTag crateData(ItemStack stack) {\n        var data = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);\n        return data == null ? null : data.copyTag();\n    }\n    static boolean allowed(ItemStack stack)')
            replace('BlockItem.getBlockEntityData(stack)', 'crateData(stack)')
            replace('CompoundTag save()', 'CompoundTag save(net.minecraft.core.HolderLookup.Provider registries)')
            replace('.save(new CompoundTag())', '.save(registries)')
            replace('void restore(CompoundTag tag)', 'void restore(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries)')
            replace('ItemStack.of(row.getCompound("box"))','ItemStack.parseOptional(registries,row.getCompound("box"))')
    elif file == 'CubeFleet.java':
        replace('computeIfAbsent(CubeFleet::load,CubeFleet::new,','computeIfAbsent(new SavedData.Factory<>(CubeFleet::new,(tag,registries)->load(tag)),')
        replace('save(CompoundTag tag)', 'save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries)')
    elif file == 'CubeFighterSight.java':
        replace('RegisterGuiOverlaysEvent','RegisterGuiLayersEvent')
        replace('event.registerAboveAll("fighter_sight",(gui,g,partial,width,height)->{',
                'event.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CubePrototype.ID,"fighter_sight"),(g,time)->{\n                float partial=time.getGameTimeDeltaPartialTick(false);\n                int width=g.guiWidth(), height=g.guiHeight();')
    elif file == 'CubeKeys.java':
        replace('net.neoforged.neoforge.event.ClientTickEvent.Post','net.neoforged.neoforge.client.event.ClientTickEvent.Post')
        replace('        if (event.phase != net.neoforged.neoforge.event.TickEvent.Phase.END) return;\n','')
        replace('getEntityReach()', 'entityInteractionRange()')
    elif file == 'CubePropulsion.java':
        for obj in ('out','vertex'):
            replace(obj+'.vertex(',obj+'.addVertex(')
        for old,new in [('.color(','.setColor('),('.uv(','.setUv('),('.overlayCoords(','.setOverlay('),('.uv2(','.setLight('),
                        ('.normal(mat.normal(),','.setNormal(mat,'),('.normal(matrix.normal(),','.setNormal(matrix,'),('.endVertex()','')]:
            replace(old,new)
    elif file == 'CubeMesh.java':
        replace('ModelState state, ResourceLocation modelLocation)', 'ModelState state)')
        replace('new QuadBakingVertexConsumer.Buffered()', 'new QuadBakingVertexConsumer()')
        replace('quad.setTintIndex(0)', 'quad.setTintIndex(-1)')
        replace('quad.setUv(texture.getU(uv[t] * 16F), texture.getV((1F - uv[t + 1]) * 16F));',
                'quad.setUv(texture.getU(uv[t]), texture.getV(1F - uv[t + 1]));')
        replace('                quad;\n', '')
        replace('quad.getQuad()', 'quad.bakeQuad()')
    elif file == 'CubeRenderer.java':
        replace('event.register("mesh",', 'event.register(ResourceLocation.fromNamespaceAndPath(CubePrototype.ID, "mesh"),')
        replace('MODELS.values().forEach(event::register)', 'MODELS.values().forEach(id -> event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(id)))')
        replace('getModel(MODELS.get(name))','getModel(net.minecraft.client.resources.model.ModelResourceLocation.standalone(MODELS.get(name)))')
        # The atlas entitySolid layer is a fixed buffer. Finish it before
        # drawing the coplanar white layer, or the deferred hull covers it.
        replace('if (white > 0) carrierParts(cube, partialTick, pose, buffers, light, white);',
                'if (white > 0) {\n'
                '                if (buffers instanceof MultiBufferSource.BufferSource batch) batch.endBatch(RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS));\n'
                '                carrierParts(cube, partialTick, pose, buffers, light, white);\n'
                '            }')
    elif file == 'CubeStationScreen.java':
        for field in ('coordX','coordZ'):
            replace('            '+field+'.tick();\n','')
        # Screen.render calls renderBackground before widgets in 1.21.1.
        replace('void renderBackground(GuiGraphics g)', 'void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partial)')
        replace('super.renderBackground(g);', 'super.renderBackground(g,mouseX,mouseY,partial);')
        replace('        renderBackground(g);\n', '')
        replace('mouseScrolled(double px,double py,double change)', 'mouseScrolled(double px,double py,double horizontal,double change)')
        replace('super.mouseScrolled(px,py,change)', 'super.mouseScrolled(px,py,horizontal,change)')
    elif file == 'CubeStorageMenu.java':
        replace('NetworkHooks.openScreen(p,','p.openMenu(')
    elif file == 'CubeStorageScreen.java':
        replace('net.neoforged.fml.event.lifecycle.FMLClientSetupEvent','net.neoforged.neoforge.client.event.RegisterMenuScreensEvent')
        replace('setup(FMLClientSetupEvent event)','setup(RegisterMenuScreensEvent event)')
        replace('event.enqueueWork(()->MenuScreens.register(CubeStorageMenu.TYPE.get(),CubeStorageScreen::new))','event.register(CubeStorageMenu.TYPE.get(),CubeStorageScreen::new)')
        replace('renderBackground(g);super.render','super.render')
    return text, changes
