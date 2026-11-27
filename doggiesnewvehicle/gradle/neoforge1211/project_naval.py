"""Explicit platform API projection for the shared Libertad owner."""


def project_naval(text, path):
    changes = []
    def replace(before, after):
        nonlocal text
        count = text.count(before)
        if count:
            text = text.replace(before, after)
            changes.append(dict(before=before, after=after, count=count))
    replace('net.neoforged.neoforge.common.ForgeMod', 'net.neoforged.neoforge.common.NeoForgeMod')
    replace('ForgeMod.WATER_TYPE.get()', 'ForgeMod.WATER_TYPE.value()')
    replace('var water = ForgeMod.', 'var water = NeoForgeMod.')
    replace('net.minecraft.world.level.chunk.ChunkStatus', 'net.minecraft.world.level.chunk.status.ChunkStatus')
    replace('import net.neoforged.neoforge.common.world.ForgeChunkManager;', '')
    replace('ForgeChunkManager.forceChunk(level, Libertad.ID, ship.getUUID(),',
            'ShipTicketController.forceChunk(level, ship.getUUID(),')
    replace('ModMobEffects.SHOCK.get()', 'ModMobEffects.SHOCK')
    for before, after in {
        'm_142687_':'remove', 'm_7340_':'onPassengerTurned', 'm_20351_':'removePassenger',
        'm_6034_':'setPos', 'm_20184_':'getDeltaMovement',
        'onAddedToWorld':'onAddedToLevel',
        'canChangeDimensions()':'canChangeDimensions(net.minecraft.world.level.Level from, net.minecraft.world.level.Level to)',
    }.items(): replace(before, after)
    if path.endswith('/ShellStepMixin.java'):
        replace('FriendlyByteBuf', 'RegistryFriendlyByteBuf')
    if path.endswith('/LibertadEntity.java'):
        replace('updateFluidHeightAndDoFluidPushing(java.util.function.Predicate<net.minecraft.world.level.material.FluidState> predicate)',
                'updateFluidHeightAndDoFluidPushing()')
        replace('ShipFluidAccess.update(this, predicate)', 'ShipFluidAccess.update(this, state -> true)')
        replace('super.updateFluidHeightAndDoFluidPushing(predicate)', 'super.updateFluidHeightAndDoFluidPushing()')
    if path.endswith('/ShipPresence.java'):
        replace('computeIfAbsent(ShipPresence::read, ShipPresence::new, "libertad_dimension_ship")',
                'computeIfAbsent(new SavedData.Factory<>(ShipPresence::new, (tag, registries) -> read(tag)), "libertad_dimension_ship")')
        replace('save(CompoundTag tag)', 'save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries)')
    if path.endswith('/LibertadRenderer.java'):
        replace('.normal(pose.normal, vertices[i + 3], vertices[i + 4], vertices[i + 5])',
                '.setNormal(pose.normal.m00() * vertices[i + 3] + pose.normal.m10() * vertices[i + 4] + pose.normal.m20() * vertices[i + 5], '
                'pose.normal.m01() * vertices[i + 3] + pose.normal.m11() * vertices[i + 4] + pose.normal.m21() * vertices[i + 5], '
                'pose.normal.m02() * vertices[i + 3] + pose.normal.m12() * vertices[i + 4] + pose.normal.m22() * vertices[i + 5])')
    if path.endswith('/ShellTracer.java'):
        replace('ClientTickEvent.Post', 'ClientTickEvent.Pre')
        replace('event.phase == TickEvent.Phase.START && ', '')
    if path.endswith('/RuntimeRenderCheck.java'):
        # This fixture compares raw normal matrices; it is not a vertex writer.
        replace('PoseStack.Pose normal', 'Matrix3f normal')
        replace('method.getName().equals("vertex")', 'method.getName().equals("addVertex")')
        replace('method.getName().equals("uv2")', 'method.getName().equals("setLight")')
    if path.endswith('/RuntimeGeometryCheck.java'):
        replace('public static void main(String[] args) throws Exception {',
                'public static void main(String[] args) throws Exception {\n        RuntimePhysicsCheck.bootstrapStandalone();')
    if path.endswith('/RuntimeInputCheck.java'):
        replace('new ClientTickEvent.Post(TickEvent.Phase.END)', 'new ClientTickEvent.Post()')
        replace('net/minecraftforge/network/simple/SimpleChannel', 'com/liucodex/doggiesnewvehicle/neoforge/DnvNeoChannel')
        replace('net/minecraftforge/network/simple/DnvNeoChannel', 'com/liucodex/doggiesnewvehicle/neoforge/DnvNeoChannel')
        replace('Map.entry("net/minecraft/world/effect/MobEffect", Effect.class)',
                'Map.entry("net/minecraft/world/effect/MobEffect", Effect.class), Map.entry("net/minecraft/core/Holder", Registry.class)')
        replace('hasEffect(Effect effect)', 'hasEffect(Registry effect)')
    if '/src/sbwTest/' in path:
        replace('net/minecraftforge/registries/RegistryObject', 'net/neoforged/neoforge/registries/DeferredHolder')
    if path.endswith('/WaterEntry.java'):
        replace('ClipContext.Fluid.ANY, projectile)',
                'ClipContext.Fluid.ANY, projectile == null ? net.minecraft.world.phys.shapes.CollisionContext.empty() : net.minecraft.world.phys.shapes.CollisionContext.of(projectile))')
    if path.endswith('/RuntimePhysicsCheck.java'):
        start = text.index('    static void bootstrapStandalone() {')
        end = text.index('    public static void main(', start)
        replace(text[start:end], '''    static void bootstrapStandalone() {
        // No game is started. Native bootstrap needs an empty feature-flag discovery context.
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

''')
    if path.endswith('/CannonMixinCheck.java'):
        replace('CompatibilityLevel.JAVA_17', 'CompatibilityLevel.JAVA_21')
        replace('    @Override public ClassNode getClassNode(String name, boolean runTransformers) throws IOException {',
                '    @Override public ClassNode getClassNode(String name, boolean runTransformers, int flags) throws IOException { return getClassNode(name, runTransformers); }\n'
                '    @Override public ClassNode getClassNode(String name, boolean runTransformers) throws IOException {')
        replace('        var selected = new JsonArray();',
                '        if (name.equals(LIBERTAD) && Boolean.getBoolean("probe.allNavalMixins")) return config.toString().getBytes(StandardCharsets.UTF_8);\n'
                '        var selected = new JsonArray();')
        replace('            var cannon = transformed.get(SHELL + "CannonShellEntity");',
                '''            if (Boolean.getBoolean("probe.allNavalMixins")) {
                var extraTargets = new ArrayList<>(List.of(SBW + "item/gun/GunItem", SBW + "data/gun/GunData",
                        SBW + "entity/vehicle/damage/DamageModifier", SBW + "block/ContainerBlock$Companion",
                        SBW + "block/entity/ContainerBlockEntity$Companion"));
                if (args[5].equals("CLIENT")) extraTargets.add(SBW + "event/ClientEventHandler");
                for (String target : extraTargets) {
                    byte[] before = bytes(target + ".class");
                    transformed.put(target, node(transformer.transformClass(environment, target.replace('/', '.'), before)));
                }
                System.out.println("PASS: all naval Mixin targets applied, " + transformed.size() + " native target classes");
            }
            var cannon = transformed.get(SHELL + "CannonShellEntity");''')
    if path.endswith('/RuntimePackCheck.java'):
        replace('''        return Pack.readMetaAndCreate("mod:libertad", Component.literal("Libertad"), true,
                id -> Files.isDirectory(path) ? new PathPackResources(id, path, false)
                        : new FilePackResources(id, path.toFile(), false),
                type, Pack.Position.TOP, PackSource.DEFAULT);''',
                '''        return Pack.readMetaAndCreate(new net.minecraft.server.packs.PackLocationInfo(
                        "mod:libertad", Component.literal("Libertad"), PackSource.DEFAULT, java.util.Optional.empty()),
                Files.isDirectory(path) ? new PathPackResources.PathResourcesSupplier(path)
                        : new FilePackResources.FileResourcesSupplier(path),
                type, new net.minecraft.server.packs.PackSelectionConfig(true, Pack.Position.TOP, false));''')
    if path.endswith('/WatchCamera.java'):
        replace('event.getScrollDelta()', 'event.getScrollDeltaY()')
    if path.endswith('/LibertadClient.java'):
        replace('com.liucodex.doggiesnewvehicle.neoforge.DnvNeoRenderStage.stack(event).last().pose()', 'event.getModelViewMatrix()')
        replace('import net.neoforged.neoforge.client.event.RenderGuiOverlayEvent;',
                'import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;')
        replace('import net.neoforged.neoforge.client.gui.overlay.ForgeGui;', '')
        replace('''        if (event.phase == TickEvent.Phase.START) {
            constrainLocalHelm(client);
            return;
        }
''', '')
        replace('TickEvent.RenderTickEvent', 'net.neoforged.neoforge.client.event.RenderFrameEvent.Pre')
        replace('if (event.phase == TickEvent.Phase.START) constrainLocalHelm(Minecraft.getInstance());',
                'constrainLocalHelm(Minecraft.getInstance());')
        replace('RenderGuiOverlayEvent.Pre', 'RenderGuiLayerEvent.Pre')
        replace('event.getOverlay().id()', 'event.getName()')
        replace('client.gui instanceof ForgeGui forge ? Math.max(forge.leftHeight, forge.rightHeight) : 45',
                'Math.max(client.gui.leftHeight, client.gui.rightHeight)')
        # Keep the pre-entity yaw constraint separate from post-tick input submission.
        replace('    @SubscribeEvent\n    public static void tick(ClientTickEvent.Post event)',
                '    @SubscribeEvent\n    public static void beforeTick(ClientTickEvent.Pre event) { constrainLocalHelm(Minecraft.getInstance()); }\n\n'
                '    @SubscribeEvent\n    public static void tick(ClientTickEvent.Post event)')
    return text, changes
