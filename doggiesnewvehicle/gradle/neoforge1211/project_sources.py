"""Generate the 1.21.1 API projection; shared authoring files are read-only inputs.

Platform replacements are explicit and checked before applying. Each emitted file
records its authority and hashes. Runtime state/algorithms remain in their module.
"""
from pathlib import Path
import argparse, hashlib, json, re, os

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/neoforge1211/generated'
RULES = Path(__file__).resolve().parent

def sha(data): return hashlib.sha256(data).hexdigest()

def project_java(text, path):
    changes = []
    def replace(before, after):
        nonlocal text
        count = text.count(before)
        if count:
            text = text.replace(before, after)
            changes.append({'from': before, 'to': after, 'count': count})
    # Namespace-only APIs; semantic changes are handled below or by adapters.
    # Stable 1.20.1 SRG names used by SBW overrides/Mixins, verified against output.srg.
    for before, after in {
        'm_6375_':'mouseClicked', 'm_7856_':'init', 'm_88315_':'render', 'm_8119_':'tick',
        'm_6469_':'hurt', 'm_7967_':'addFreshEntity', 'm_7378_':'readAdditionalSaveData',
        'm_7380_':'addAdditionalSaveData', 'm_8097_':'defineSynchedData',
        'm_146892_':'getEyePosition', 'm_20185_':'getX', 'm_20186_':'getY', 'm_20189_':'getZ',
        'm_20206_':'getBbHeight', 'm_20240_':'saveWithoutId', 'm_6075_':'baseTick',
        'm_6096_':'interact', 'm_7310_':'canAddPassenger', 'm_46961_':'destroyBlock', 'm_46075_':'finalizeExplosion',
        'm_6532_':'onHit',
        'm_151558_':'getMaxBuildHeight',
    }.items(): replace(before, after)
    if path.endswith('/NuclearSiloEntity.java'):
        replace('m_20256_', 'setDeltaMovement')
    if path.endswith('/Dsx1Model.java'):
        replace('client.getFrameTime()', 'client.getTimer().getGameTimeDeltaPartialTick(false)')
    for before, after in [
        ('onRemovedFromWorld', 'onRemovedFromLevel'),
        ('net.minecraftforge.energy.', 'net.neoforged.neoforge.energy.'),
        ('net/minecraftforge/energy/', 'net/neoforged/neoforge/energy/'),
        ('net.minecraftforge.api.distmarker.', 'net.neoforged.api.distmarker.'),
        ('net.minecraftforge.eventbus.api.', 'net.neoforged.bus.api.'),
        ('net.minecraftforge.fml.', 'net.neoforged.fml.'),
        ('net.minecraftforge.client.', 'net.neoforged.neoforge.client.'),
        ('net.minecraftforge.event.', 'net.neoforged.neoforge.event.'),
        ('net.minecraftforge.common.', 'net.neoforged.neoforge.common.'),
        ('net.minecraftforge.registries.', 'net.neoforged.neoforge.registries.'),
        ('import net.neoforged.neoforge.common.MinecraftForge;', 'import net.neoforged.neoforge.common.NeoForge;'),
        ('MinecraftForge.EVENT_BUS', 'NeoForge.EVENT_BUS'),
        ('@Mod.EventBusSubscriber', '@net.neoforged.fml.common.EventBusSubscriber'),
        ('Mod.EventBusSubscriber.Bus.', 'net.neoforged.fml.common.EventBusSubscriber.Bus.'),
        ('EventBusSubscriber.Bus.FORGE', 'EventBusSubscriber.Bus.GAME'),
        ('import net.neoforged.neoforge.registries.RegistryObject;', 'import net.neoforged.neoforge.registries.DeferredHolder;'),
        ('RegistryObject<EntityType<', 'DeferredHolder<EntityType<?>, EntityType<'),
        ('RegistryObject<Item>', 'DeferredHolder<Item, Item>'),
        ('RegistryObject<Block>', 'DeferredHolder<Block, Block>'),
        ('RegistryObject<CreativeModeTab>', 'DeferredHolder<CreativeModeTab, CreativeModeTab>'),
        ('RegistryObject<SoundEvent>', 'DeferredHolder<SoundEvent, SoundEvent>'),
        ('DnvArtilleryEntities.DOGEGG_SILO.isPresent()', 'DnvArtilleryEntities.DOGEGG_SILO.isBound()'),
        ('DnvArtilleryEntities.DOGEGG_MISSILE.isPresent()', 'DnvArtilleryEntities.DOGEGG_MISSILE.isBound()'),
        ('RegistryObject<MenuType<', 'DeferredHolder<MenuType<?>, MenuType<'),
        ('RegistryObject<DnvBehaviorControllerItem<', 'DeferredHolder<Item, DnvBehaviorControllerItem<'),
        ('import net.neoforged.neoforge.registries.ForgeRegistries;', 'import net.minecraft.core.registries.BuiltInRegistries;'),
        ('ForgeRegistries.ENTITY_TYPES', 'BuiltInRegistries.ENTITY_TYPE'),
        ('ForgeRegistries.ITEMS', 'BuiltInRegistries.ITEM'),
        ('ForgeRegistries.SOUND_EVENTS', 'BuiltInRegistries.SOUND_EVENT'),
        ('ForgeRegistries.BLOCKS', 'BuiltInRegistries.BLOCK'),
        ('ForgeRegistries.MENU_TYPES', 'BuiltInRegistries.MENU'),
        ('ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS',
         'net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS'),
        ('IForgeMenuType', 'IMenuTypeExtension'),
        ('new ResourceLocation(', 'ResourceLocation.fromNamespaceAndPath('),
        ('defineSynchedData()', 'defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder)'),
        ('super.defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder);', 'super.defineSynchedData(builder);'),
        ('entityData.define(', 'builder.define('),
        ('getEntityData().define(', 'builder.define('),
        ('net.minecraftforge.network.simple.SimpleChannel', 'com.liucodex.doggiesnewvehicle.neoforge.DnvNeoChannel'),
        ('SimpleChannel', 'DnvNeoChannel'),
        ('net.minecraftforge.network.NetworkRegistry', 'com.liucodex.doggiesnewvehicle.neoforge.DnvNeoChannel'),
        ('NetworkRegistry.newDnvNeoChannel', 'DnvNeoChannel.create'),
        ('DnvNeoChannel.newDnvNeoChannel', 'DnvNeoChannel.create'),
        ('net.minecraftforge.network.NetworkDirection', 'com.liucodex.doggiesnewvehicle.neoforge.DnvNeoChannel.Direction'),
        ('NetworkDirection.', 'Direction.'),
        ('net.minecraftforge.network.NetworkEvent', 'com.liucodex.doggiesnewvehicle.neoforge.DnvNeoChannel.Context'),
        ('NetworkEvent.Context', 'Context'),
        ('net.minecraftforge.network.PacketDistributor', 'com.liucodex.doggiesnewvehicle.neoforge.DnvNeoTargets'),
        ('PacketDistributor.', 'DnvNeoTargets.'),
        ('net.neoforged.fml.DistExecutor', 'com.liucodex.doggiesnewvehicle.neoforge.DnvNeoDist'),
        ('DistExecutor.', 'DnvNeoDist.'),
        ('context.setPacketHandled(true);', ''),
        ('net.neoforged.neoforge.event.entity.living.LivingHurtEvent', 'net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent'),
        ('LivingHurtEvent', 'LivingIncomingDamageEvent'),
        ('LivingAttackEvent', 'LivingIncomingDamageEvent'),
        ('LivingEvent.LivingTickEvent', 'net.neoforged.neoforge.event.tick.EntityTickEvent.Pre'),
        ('import net.neoforged.neoforge.event.TickEvent;', 'import net.neoforged.neoforge.event.tick.ServerTickEvent;\nimport net.neoforged.neoforge.client.event.ClientTickEvent;\nimport net.neoforged.neoforge.event.tick.PlayerTickEvent;'),
        ('TickEvent.ServerTickEvent', 'ServerTickEvent.Post'),
        ('TickEvent.ClientTickEvent', 'ClientTickEvent.Post'),
        ('TickEvent.PlayerTickEvent', 'PlayerTickEvent.Post'),
        ('TickEvent.LevelTickEvent', 'net.neoforged.neoforge.event.tick.LevelTickEvent.Post'),
        ('event.phase!=TickEvent.Phase.END', 'false'),
        ('event.phase != TickEvent.Phase.END', 'false'),
        ('event.phase == TickEvent.Phase.END', 'true'),
        ('.resolve().orElse(null)', '.orElse(null)'),
        ('ItemStack stack, @Nullable Level level,', 'ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,'),
        ('ItemStack stack, Level level, List<Component> tooltip', 'ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip'),
        ('public boolean mouseScrolled(double mouseX, double mouseY, double delta)', 'public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double delta)'),
        ('public boolean mouseScrolled(double x, double y, double delta)', 'public boolean mouseScrolled(double x, double y, double horizontal, double delta)'),
        ('super.mouseScrolled(mouseX, mouseY, delta)', 'super.mouseScrolled(mouseX, mouseY, horizontal, delta)'),
        ('renderBackground(graphics);', 'renderBackground(graphics, mouseX, mouseY, partialTick);'),
        ('event.player', 'event.getEntity()'),
        ('ForgeEventFactory', 'EventHooks'),
        ('EventHooks.getMobGriefingEvent(', 'EventHooks.canEntityGrief('),
        ('.getStepHeight()', '.maxUpStep()'),
        ('ModMobEffects.STRIKE_PROTECTION.get()', 'ModMobEffects.STRIKE_PROTECTION'),
        ('player.canReach(target, 0)', 'player.canInteractWithEntity(target, 0)'),
        ('MultiBufferSource.immediate(new BufferBuilder(', 'MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.ByteBufferBuilder('),
        ('event.getPartialTick()', 'event.getPartialTick().getGameTimeDeltaPartialTick(false)'),
        ('import net.minecraftforge.network.NetworkHooks;', ''),
        ('getAddEntityPacket()', 'getAddEntityPacket(net.minecraft.server.level.ServerEntity tracker)'),
        ('NetworkHooks.getEntitySpawningPacket(this)', 'super.getAddEntityPacket(tracker)'),
        ('NetworkHooks.openScreen(player, ', 'player.openMenu('),
        ('net.minecraftforge.entity.PartEntity', 'net.neoforged.neoforge.entity.PartEntity'),
        ('net.minecraftforge.entity.IEntityAdditionalSpawnData', 'net.neoforged.neoforge.entity.IEntityWithComplexSpawn'),
        ('implements IEntityAdditionalSpawnData', 'implements IEntityWithComplexSpawn'),
        ('writeSpawnData(FriendlyByteBuf buffer)', 'writeSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buffer)'),
        ('readSpawnData(FriendlyByteBuf buffer)', 'readSpawnData(net.minecraft.network.RegistryFriendlyByteBuf buffer)'),
    ]:
        replace(before, after)
    if 'VertexConsumer' in text:
        for before, after in [('.vertex(','.addVertex('), ('.color(','.setColor('),
                              ('.uv(','.setUv('), ('.overlayCoords(','.setOverlay('),
                              ('.uv2(','.setLight('), ('.endVertex()', ''),
                              ('Matrix3f normal', 'PoseStack.Pose normal'),
                              ('= pose.normal();', '= pose;'),
                              ('.normal(pose.normal(),', '.setNormal(pose,'),
                              ('.normal(matrix.normal(),', '.setNormal(matrix,'),
                              ('.normal(current.normal(),', '.setNormal(current,'),
                              ('.normal(p.normal(),', '.setNormal(p,'),
                              ('.normal(normal', '.setNormal(normal'),
                              ('.normal(\n', '.setNormal(\n')]:
            replace(before, after)
        # Keep the silo palette query distinct from VertexConsumer's color setter.
        replace('SiloStatusLight.setColor(', 'SiloStatusLight.color(')
    text, n = re.subn(r'ResourceLocation.fromNamespaceAndPath\(("[^"\n]*"|id)\)', r'ResourceLocation.parse(\1)', text)
    if n: changes.append({'rule': 'one-argument resource location', 'count': n})
    text, n = re.subn(r'(?<![\w.])event\.level\b', 'event.getLevel()', text)
    if n: changes.append({'rule': 'level tick accessor', 'count': n})
    # Entity-owned, non-persistent attachment replaces the removed Forge lazy capability.
    text, n = re.subn(r'(\w+)\s*\.getCapability\(DnvVehicleCrewCapability.CAPABILITY\)', r'DnvVehicleCrewCapability.find(\1)', text)
    if n: changes.append({'rule': 'entity crew attachment lookup', 'count': n})
    text, n = re.subn(r'(BuiltInRegistries\.\w+)\.getValue\(', r'\1.get(', text)
    if n: changes.append({'rule': 'vanilla registry lookup', 'count': n})
    # World draws retain the event pose. Only HUDs projecting world coordinates
    # on the CPU need the camera model-view before the projection matrix.
    if path.endswith(('/CubeHomeHud.java', '/LibertadClient.java')):
        replace('event.getPoseStack()',
                'com.liucodex.doggiesnewvehicle.neoforge.DnvNeoRenderStage.stack(event)')
    if path.endswith(('/DnvVehicleOperationsScreen.java', '/DnvVehicleCrewCoreScreen.java')):
        # Screen.render invokes this virtual hook once, before its widgets.
        replace('public void render(', 'public void renderBackground(')
        replace('    renderBackground(graphics, mouseX, mouseY, partialTick);',
                '    super.renderBackground(graphics, mouseX, mouseY, partialTick);')
        replace('    super.render(graphics, mouseX, mouseY, partialTick);\n', '')
    if path.endswith('/DnvBossLaserHit.java'):
        replace('import net.neoforged.neoforge.common.ForgeHooks;',
                'import net.neoforged.neoforge.common.CommonHooks;')
        replace('!ForgeHooks.onLivingAttack(living, damage, amount)',
                'CommonHooks.onEntityIncomingDamage(living, new net.neoforged.neoforge.common.damagesource.DamageContainer(damage, amount))')
    if path.endswith('/SporeLaserTargetExecutionTest.java'):
        replace('ResourceLocation.fromNamespaceAndPath(((Kind)type).id())', 'ResourceLocation.parse(((Kind)type).id())')
        replace('net/minecraftforge/registries/ForgeRegistries', 'net/minecraft/core/registries/BuiltInRegistries')
        replace('net/minecraftforge/registries/IForgeRegistry', 'net/minecraft/core/Registry')
        replace('Map.entry("net/minecraft/core/Registry", Registry.class)',
                'Map.entry("net/minecraft/core/Registry", Registry.class), Map.entry("net/minecraft/core/DefaultedRegistry", Registry.class)')
        replace('ENTITY_TYPES', 'ENTITY_TYPE')
    if path.endswith('/MothershipWeaponsExecutionTest.java'):
        replace('Map.entry("net/minecraftforge/common/ForgeHooks",Hooks.class)',
                'Map.entry("net/neoforged/neoforge/common/CommonHooks",Hooks.class), '
                'Map.entry("net/neoforged/neoforge/common/damagesource/DamageContainer",DamagePacket.class)')
        replace('public static class Hooks {public static boolean onLivingAttack(LivingActor target,Damage source,float amount){target.world.attacks++;return !target.world.cancelAttack;}}',
                'public record DamagePacket(Damage source,float amount) {}\n'
                '    public static class Hooks {public static boolean onEntityIncomingDamage(LivingActor target,DamagePacket hit){target.world.attacks++;return target.world.cancelAttack;}}')
    # Explicit per-file adaptation, matching the original after namespace projection.
    for patch in json.loads((RULES / 'patches.json').read_text(encoding='utf-8')).get(path, []):
        count = text.count(patch['before'])
        if count != patch.get('count', 1):
            raise RuntimeError(f"Projection drift: {path}: {patch['before']!r}, matches={count}")
        replace(patch['before'], patch['after'])
    return text, changes

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--modules', required=True)
    args = parser.parse_args()
    selected = args.modules.split(',')
    assert set(selected) <= {'main', 'artillery', 'boss', 'platform', 'naval'}
    overrides = json.loads((RULES / 'overrides.json').read_text(encoding='utf-8'))
    art_manifest = RULES.parent / 'runtime-art-inputs.txt'
    deferred_art = set(art_manifest.read_text(encoding='utf-8').splitlines()) if art_manifest.is_file() else set()
    records, emitted, omitted = [], set(), []
    OUT.mkdir(parents=True, exist_ok=True)
    def emit(relative, data, source, changes):
        assert relative not in emitted, f'Duplicate output: {relative}'
        emitted.add(relative)
        target = OUT / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists() or target.read_bytes() != data: target.write_bytes(data)
        records.append(dict(output=relative, source=Path(os.path.relpath(source, ROOT)).as_posix(),
                            inputSha256=sha(source.read_bytes()), outputSha256=sha(data), changes=changes))
    for module in selected:
        java_roots = ([ROOT.parent/'libertad/src/main/java',ROOT.parent/'libertad/src/sbw/java', ROOT/'src/aviation/java', ROOT/'src/navalCommon/java']
                      if module == 'naval' else [ROOT/f'src/{module}/java'])
        resource_root = ROOT.parent/'libertad/src/main/resources' if module == 'naval' else ROOT/f'src/{module}/resources'
        for source_root in java_roots:
          for file in sorted(source_root.rglob('*.java')):
            relative = Path(os.path.relpath(file, ROOT)).as_posix()
            if relative in overrides:
                if overrides[relative] is None:
                    omitted.append({'source': relative, 'sha256': sha(file.read_bytes()), 'reason': 'retired Forge provider; NeoForge entity attachment owns this runtime'})
                    continue
                adapter = ROOT / overrides[relative]
                assert adapter.is_file(), adapter
                emit(f'{module}/java/' + file.relative_to(source_root).as_posix(),
                     adapter.read_bytes(), adapter, [{'overrides': relative}])
            else:
                text, changes = project_java(file.read_text(encoding='utf-8'), relative)
                if module == 'naval':
                    from project_naval import project_naval
                    if relative.startswith("src/aviation/"):
                        from project_aviation import project_aviation
                        text, aviation_changes = project_aviation(text, relative)
                        changes.extend(aviation_changes)
                    text, naval_changes = project_naval(text, relative)
                    changes.extend(naval_changes)
                emit(f'{module}/java/' + file.relative_to(source_root).as_posix(),
                     text.encode('utf-8'), file, changes)
        # Additional adapter helpers must not shadow a shared file implicitly.
        for file in sorted((ROOT / f'src/neoforge1211/{module}/java').rglob('*.java')):
            if file.relative_to(ROOT).as_posix() in overrides.values(): continue
            emit(f'{module}/java/' + file.relative_to(ROOT/f'src/neoforge1211/{module}/java').as_posix(),
                 file.read_bytes(), file, [])
        resource_roots = [resource_root]
        if module == 'naval':
            resource_roots.append(ROOT/'src/aviation/resources')
            for sound, owner in [('idle','boss/resources/assets/dnv_mothership'), ('propulsion','boss/resources/assets/dnv_mothership'),
                                 ('jingzhe_attack','artillery/resources/assets/doggiesnewvehicle')]:
                source = ROOT/f'src/{owner}/sounds/{sound}.ogg'
                if not source.is_file() and source.relative_to(ROOT).as_posix() in deferred_art:
                    continue
                emit(f'naval/resources/assets/dnvcarriercube/sounds/{sound}.ogg', source.read_bytes(), source, [])
        for resource_root in resource_roots:
            for file in sorted(resource_root.rglob('*')):
                if not file.is_file(): continue
                name = file.relative_to(resource_root).as_posix()
                # The native metadata is emitted once from the explicit platform adapter.
                if name in {'META-INF/mods.toml', 'pack.mcmeta'}: continue
                data = file.read_bytes()
                original = name
                if name == 'assets/doggiesnewvehicle/models/item/dsx1.json':
                    metadata = json.loads(data)
                    assert metadata['loader'] == 'forge:separate_transforms'
                    metadata['loader'] = 'neoforge:separate_transforms'
                    data = json.dumps(metadata,ensure_ascii=False,indent=2).encode('utf-8')
                if name.startswith('data/'):
                    parts = name.split('/')
                    parts[2] = {'advancements':'advancement', 'recipes':'recipe', 'loot_tables':'loot_table'}.get(parts[2],parts[2])
                    if parts[2] == 'tags':
                        parts[3] = {'items':'item','blocks':'block','entity_types':'entity_type'}.get(parts[3],parts[3])
                    if parts[1:3] == ['forge','loot_modifiers']: parts[1] = 'neoforge'
                    name = '/'.join(parts)
                    if file.suffix == '.json':
                        # Common item tags moved to c. SBW researching retains its own result schema.
                        metadata = json.loads(data.decode('utf-8').replace('forge:loot_table_id','neoforge:loot_table_id')
                                              .replace('#forge:', '#c:').replace('"forge:', '"c:'))
                        if parts[2] == 'recipe' and metadata.get('type','').startswith('minecraft:'):
                            result = metadata.get('result')
                            if isinstance(result,dict) and 'item' in result: result['id'] = result.pop('item')
                        data = json.dumps(metadata,ensure_ascii=False,indent=2).encode('utf-8')
                if (name.startswith('mixins.') or name == 'libertad.mixins.json') and name.endswith('.json'):
                    metadata = json.loads(data)
                    metadata['compatibilityLevel'] = 'JAVA_21'
                    if name == 'mixins.dnvcarriercube.json':
                        metadata['mixins'].append('CarrierRiderTrackingMixin')
                    # Forge's SRG refmaps are only valid for the Forge 1.20.1 artifact.
                    metadata.pop('refmap', None)
                    data = json.dumps(metadata, indent=2).encode('utf-8')
                if module == 'main' and name.startswith('assets/doggiesnewvehicle/lang/'):
                    values = json.loads(data)
                    for owner in selected:
                        fragment = ROOT/f'src/{owner}/lang/{file.name}'
                        if owner == 'main' or not fragment.is_file(): continue
                        extra = json.loads(fragment.read_text(encoding='utf-8'))
                        assert not set(values) & set(extra), fragment
                        values.update(extra)
                    data = json.dumps(values, ensure_ascii=False, indent=2).encode('utf-8')
                emit(f'{module}/resources/{name}', data, file, [] if name == original else [{'resourcePath': original}])
    metadata = ROOT/'src/neoforge1211/main/resources/META-INF/neoforge.mods.toml'
    toml = metadata.read_text(encoding='utf-8')
    if 'naval' not in selected:
        toml = toml.split('\n[[mods]]\nmodId="libertad"', 1)[0] + '\n'
    for owner in ['artillery','platform']:
        if owner not in selected:
            toml = toml.replace('[[mixins]]\nconfig="mixins.dnv'+owner+'.json"\n', '')
            if owner == 'artillery': toml = toml.replace('[[mixins]]\nconfig="mixins.dnvblizzard.json"\n', '')
    emit('main/resources/META-INF/neoforge.mods.toml', toml.encode('utf-8'), metadata, [])
    pack_metadata = ROOT/'src/neoforge1211/main/resources/pack.mcmeta'
    emit('main/resources/pack.mcmeta', pack_metadata.read_bytes(), pack_metadata, [])
    if 'naval' in selected:
        for test_root in (ROOT.parent/'libertad/src/test/java', ROOT.parent/'libertad/src/sbwTest/java'):
            for file in sorted(test_root.rglob('*.java')):
                if 'libertadcheck' in file.parts:
                    continue
                relative = Path(os.path.relpath(file, ROOT)).as_posix()
                text, changes = project_java(file.read_text(encoding='utf8'), relative)
                from project_naval import project_naval
                text, extra = project_naval(text, relative)
                emit('navalTest/java/' + file.relative_to(test_root).as_posix(),
                     text.encode('utf8'), file, changes + extra)
    for relative in json.loads((RULES/'tests.json').read_text(encoding='utf-8')):
        file = ROOT/relative
        text,changes = project_java(file.read_text(encoding='utf-8'),relative)
        emit('test/java/'+relative.split('/java/',1)[1],text.encode('utf-8'),file,changes)
    manifest = OUT/'projection-manifest.json'
    if manifest.exists():
        prior = json.loads(manifest.read_text(encoding='utf-8'))
        for record in prior['files']:
            if record['output'] in emitted: continue
            stale = (OUT/record['output']).resolve()
            assert stale.is_relative_to(OUT.resolve())
            if stale.is_file(): stale.unlink()  # Only our previously manifested generated outputs.
    manifest.write_text(json.dumps({'modules': selected, 'files': records, 'omitted': omitted}, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'generatedFiles': len(records), 'modules': selected}))

if __name__ == '__main__': main()
