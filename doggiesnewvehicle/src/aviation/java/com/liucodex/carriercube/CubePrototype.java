package com.liucodex.carriercube;

import com.atsuishio.superbwarfare.item.container.ContainerBlockItem;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(CubePrototype.ID)
public final class CubePrototype {
    static final String ID = "dnvcarriercube";
    private static final double BOARD_RANGE = CubeGeometry.HALF_LENGTH + 4;
    private static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    static final RegistryObject<EntityType<CubeVehicle>> CARRIER =
            type("carrier", YueCarrier::new, (float) (2 * CubeGeometry.HALF_WIDTH), (float) CubeGeometry.DECK);
    static final RegistryObject<EntityType<CubeVehicle>> AIRCRAFT = type("aircraft", CubeVehicle::new, 11.93F, 4.037F);

    static final RegistryObject<EntityType<FeiyingMissile>> SPECIAL_MISSILE = TYPES.register("feiying_missile",
            () -> EntityType.Builder.<FeiyingMissile>of(FeiyingMissile::new, MobCategory.MISC)
                    .sized(.5F, .5F).clientTrackingRange(10).updateInterval(1).build(ID + ":feiying_missile"));

    private static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ID);
    static final RegistryObject<net.minecraft.sounds.SoundEvent> IDLE = sound("idle");
    static final RegistryObject<net.minecraft.sounds.SoundEvent> WARP_LOCK = sound("warp_lock");
    static final RegistryObject<net.minecraft.sounds.SoundEvent> PROPULSION = sound("propulsion");

    private static RegistryObject<net.minecraft.sounds.SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ID, name)));
    }

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    static {
        TABS.register("shanyue", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.dnvcarriercube.shanyue"))
                .icon(() -> ContainerBlockItem.createInstance(CARRIER.get()))
                .displayItems((parameters, output) -> {
                    output.accept(ContainerBlockItem.createInstance(CARRIER.get()));
                    output.accept(ContainerBlockItem.createInstance(AIRCRAFT.get()));
                }).build());
    }

    private static void addContainers(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().toString().equals("doggiesnewvehicle:doggies_vehicle")) {
            event.accept(ContainerBlockItem.createInstance(CARRIER.get()));
            event.accept(ContainerBlockItem.createInstance(AIRCRAFT.get()));
            ShipyardItems.ITEMS.getEntries().forEach(entry -> event.accept(entry.get()));
        }
    }

    public CubePrototype() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        CubeStorageMenu.MENUS.register(modBus);
        TYPES.register(modBus);
        ShipyardItems.ITEMS.register(modBus);
        if (net.minecraftforge.fml.ModList.get().isLoaded("doggiesnewvehicle")) {
            modBus.addListener(CubePrototype::addContainers);
        } else {
            TABS.register(modBus);
        }
        SOUNDS.register(modBus);
        CubeAimPoint.register();
        MinecraftForge.EVENT_BUS.addListener(CubePrototype::commands);
        MinecraftForge.EVENT_BUS.addListener(CubePrototype::alignMissileAtRelease);
    }

    private static void alignMissileAtRelease(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof FeiyingMissile missile)
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || event.loadedFromDisk()
                || !(missile.getOwner() instanceof net.minecraft.world.entity.player.Player pilot) || !fliesFighter(pilot)) return;
        var fighter = (CubeVehicle) pilot.getVehicle();
        Vec3 direction = fighter.getViewVector(1);
        if (fighter.hoverMode()) {
            var target = com.atsuishio.superbwarfare.tools.EntityFindUtil.findEntity(level, missile.getTargetUUID());
            if (!surfaceTarget(pilot, target)) return;
            direction = target.getBoundingBox().getCenter().subtract(missile.position());
        }
        if (direction.lengthSqr() < 1e-8) return;
        // SBW coasts on inherited motion, then ignites along the projectile's facing.
        // At zero speed the stock facing is world +Z irrespective of the aircraft's heading.
        missile.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        missile.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
        missile.yRotO = missile.getYRot();
        missile.xRotO = missile.getXRot();
    }

    private static RegistryObject<EntityType<CubeVehicle>> type(String name, EntityType.EntityFactory<CubeVehicle> factory, float width, float height) {
        return TYPES.register(name, () -> EntityType.Builder.<CubeVehicle>of(factory, MobCategory.MISC)
                .sized(width, height).clientTrackingRange(10).updateInterval(1).noSummon()
                .build(ID + ":" + name));
    }

    private static void commands(RegisterCommandsEvent event) {
        var root = Commands.literal("carriercube")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("spawn").requires(source -> source.hasPermission(2))
                        .executes(context -> spawn(context.getSource().getPlayerOrException())))
                .then(Commands.literal("crew")
                        .executes(context -> boardNearby(context.getSource().getPlayerOrException())))
                .then(Commands.literal("flight_station").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    return player.isPassenger() ? result(player, selectFlightStation(player), "Launch station")
                            : boardNearby(player);
                }))
                .then(Commands.literal("seat").then(Commands.argument("number", IntegerArgumentType.integer(1, 3))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            int seat = IntegerArgumentType.getInteger(context, "number") - 1;
                            return result(player, player.getVehicle() instanceof CubeVehicle cube && cube.carrier()
                                    && (cube.getSeatIndex(player) == seat || (seat==1?selectFlightStation(player):cube.changeSeat(player,seat))), "Change seat");
                        })));
        root.then(deckAction("hover", CubeVehicle::requestHover));
        root.then(deckAction("resume", CubeVehicle::resume));
        root.then(deckAction("toggle_hover", CubeVehicle::toggleHover));
        root.then(action("board_aircraft", true, CubeVehicle::boardAircraft));
        root.then(Commands.literal("board").then(Commands.argument("cell",IntegerArgumentType.integer(1,3)).executes(c->{
            var p=c.getSource().getPlayerOrException();
            return result(p,p.getVehicle() instanceof CubeVehicle v && v.carrier() && v.boardAircraft(p,CubeHangar.Bay.values()[IntegerArgumentType.getInteger(c,"cell")-1]),"Board");
        })));
        root.then(action("auto_on",true,(v,p)->v.setAutoLaunch(p,true)));
        root.then(action("auto_off",true,(v,p)->v.setAutoLaunch(p,false)));
        root.then(action("join_carrier",false,CubeVehicle::joinCarrier));
        root.then(Commands.literal("disband").then(Commands.argument("aircraft",net.minecraft.commands.arguments.UuidArgument.uuid()).executes(c->{
            var p=c.getSource().getPlayerOrException();
            return result(p,p.getVehicle() instanceof CubeVehicle v && v.disband(p,net.minecraft.commands.arguments.UuidArgument.getUuid(c,"aircraft")),"disband");
        })));
        root.then(action("pilot_launch",false,CubeVehicle::pilotLaunch));
        root.then(action("return_key",false,CubeVehicle::returnKey));
        root.then(action("storage",true,CubeStorageMenu::open));
        root.then(action("launch", true, CubeVehicle::launch));
        root.then(action("lower_aircraft", true, CubeVehicle::lowerAircraft));
        root.then(action("abort", true, CubeVehicle::abort));
        root.then(action("capture", false, CubeVehicle::requestCapture));
        root.then(action("flight_mode", false, CubeVehicle::toggleFlightMode));
        root.then(action("return_to_carrier", false, CubeVehicle::returnToCarrier));
        root.then(action("request_stow", true, CubeVehicle::askForRecovery));
        root.then(Commands.literal("radar_page").then(Commands.argument("sort",com.mojang.brigadier.arguments.BoolArgumentType.bool())
                .then(Commands.argument("offset",IntegerArgumentType.integer(0)).then(Commands.argument("filter",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c->{
                    var p=c.getSource().getPlayerOrException();String filter=com.mojang.brigadier.arguments.StringArgumentType.getString(c,"filter");
                    return p.getVehicle() instanceof CubeVehicle v && v.radarPage(p,filter.equals("*")?"":filter,
                            com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"sort"),IntegerArgumentType.getInteger(c,"offset"))?1:0;
                })))));
        root.then(Commands.literal("map_view").then(Commands.argument("x",IntegerArgumentType.integer(-30001024,30001024))
                .then(Commands.argument("z",IntegerArgumentType.integer(-30001024,30001024))
                .then(Commands.argument("radius",IntegerArgumentType.integer(1,1024)).executes(c->{
                    var p=c.getSource().getPlayerOrException();
                    return p.getVehicle() instanceof CubeVehicle v && v.mapView(p,IntegerArgumentType.getInteger(c,"x"),
                            IntegerArgumentType.getInteger(c,"z"),IntegerArgumentType.getInteger(c,"radius"))?1:0;
                })))));
        root.then(action("scan", true, CubeVehicle::scanRadar));
        root.then(action("stop_cruise", true, CubeVehicle::cancelCruise));
        root.then(Commands.literal("waypoint").then(Commands.argument("x",IntegerArgumentType.integer(-30000000,30000000))
                .then(Commands.argument("z",IntegerArgumentType.integer(-30000000,30000000)).executes(c->{
                    var p=c.getSource().getPlayerOrException();
                    return p.getVehicle() instanceof CubeVehicle ship && ship.setWaypoint(p,IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"z"))?1:0;
                }))));
        root.then(Commands.literal("warp").then(Commands.argument("x",IntegerArgumentType.integer(-30000000,30000000))
                .then(Commands.argument("z",IntegerArgumentType.integer(-30000000,30000000)).executes(c->{
                    var p=c.getSource().getPlayerOrException();return result(p,p.getVehicle() instanceof CubeVehicle v && v.startNavigation(p,
                            IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"z"),true),"Warp");
                }))));
        root.then(Commands.literal("cruise").then(Commands.argument("x",IntegerArgumentType.integer(-30000000,30000000))
                .then(Commands.argument("z",IntegerArgumentType.integer(-30000000,30000000)).executes(c->{
                    var p=c.getSource().getPlayerOrException();
                    return result(p,p.getVehicle() instanceof CubeVehicle ship && ship.setCruise(p,IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"z")),"Cruise");
                }))));
        event.getDispatcher().register(root);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
            action(String name, boolean carrier, Action action) {
        return Commands.literal(name).executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            return result(player, player.getVehicle() instanceof CubeVehicle cube
                    && cube.carrier() == carrier && action.apply(cube, player), name);
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
            deckAction(String name, Action action) {
        return Commands.literal(name).executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            if (player.getVehicle() instanceof CubeVehicle ship && ship.carrier()) return action.apply(ship, player) ? 1 : 0;
            player.sendSystemMessage(Component.translatable("message.dnvcarriercube.deck.helm"));
            return 0;
        });
    }

    public static boolean stationaryFlight(com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity vehicle) {
        return vehicle instanceof CubeVehicle cube && !cube.carrier() && (cube.hoverMode() || cube.onDeck());
    }

    public static boolean fliesFighter(net.minecraft.world.entity.player.Player player) {
        return player.getVehicle() instanceof CubeVehicle cube && !cube.carrier() && cube.getFirstPassenger()==player;
    }

    static boolean guidedWeapon(String weapon) {
        return "Missile".equals(weapon);
    }

    public static boolean specialMissileSelected(net.minecraft.world.entity.player.Player pilot) {
        return fliesFighter(pilot) && guidedWeapon(((CubeVehicle) pilot.getVehicle()).getGunName(0));
    }

    public static com.atsuishio.superbwarfare.tools.SeekTool.Builder targetHeight(
            com.atsuishio.superbwarfare.tools.SeekTool.Builder query, double min, double max, net.minecraft.world.entity.player.Player pilot) {
        return specialMissileSelected(pilot) ? query : query.heightRange(min, max);
    }

    public static boolean selectedMissileTarget(net.minecraft.world.entity.player.Player pilot,
                                                net.minecraft.world.entity.Entity candidate) {
        return specialMissileSelected(pilot) && missileTarget("Missile", pilot, candidate);
    }

    static boolean missileTarget(String weapon, net.minecraft.world.entity.LivingEntity pilot,
                                net.minecraft.world.entity.Entity candidate) {
        return guidedWeapon(weapon) && validMissileTarget(pilot, candidate);
    }

    static boolean validMissileTarget(net.minecraft.world.entity.Entity owner, net.minecraft.world.entity.Entity target) {
        return (target instanceof net.minecraft.world.entity.LivingEntity
                    || target instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity)
                && target.isAlive() && !target.isRemoved() && !target.isSpectator() && !target.isUnderWater()
                && !target.isPassenger() && target != owner && (owner == null || target != owner.getVehicle())
                && com.atsuishio.superbwarfare.tools.SeekTool.BASIC_FILTER.test(target)
                && !com.atsuishio.superbwarfare.tools.SeekTool.IS_FRIENDLY.test(owner, target);
    }

    public static boolean surfaceTarget(net.minecraft.world.entity.LivingEntity pilot, net.minecraft.world.entity.Entity candidate) {
        return candidate != null && candidate != pilot && candidate != pilot.getVehicle() && candidate.isAlive()
                && !candidate.isSpectator() && !candidate.isUnderWater() && candidate.getVehicle()!=pilot.getVehicle();
    }

    public static void beginUnpacking(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof CubeVehicle vehicle) vehicle.beginUnpacking();
    }

    public static boolean holdCarrierBox(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state,com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity box) {
        if(box.entityType!=CARRIER.get() || !(level instanceof net.minecraft.server.level.ServerLevel world)
                || !state.getValue(com.atsuishio.superbwarfare.block.ContainerBlock.OPENED)
                || CubeFleet.in(world).canUnpack(box.entityTag))return false;
        box.setTick(0);box.setOpened(false);box.setChanged();
        world.setBlockAndUpdate(pos,state.setValue(com.atsuishio.superbwarfare.block.ContainerBlock.OPENED,false));
        var nearby=world.getNearestPlayer(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,8,false);
        if(nearby!=null)nearby.displayClientMessage(Component.literal("本维度母舰名额已占用，集装箱已保留"),true);
        return true;
    }

    private static int spawn(ServerPlayer player) {
        var world = player.serverLevel();
        CubeVehicle carrier = CARRIER.get().create(world);
        if (carrier == null || player.isPassenger()) {
            return result(player, false, "Spawn carrier");
        }
        if(!CubeFleet.in(world).admits(carrier.getUUID()))return result(player,false,"fleet_full");
        float yaw = CubeGeometry.cardinal(player.getYRot());
        Vec3 pos = player.position().add(CubeGeometry.forward(yaw).scale(BOARD_RANGE - .5)).add(0, .3, 0);
        if (!carrier.clear(CubeGeometry.hullEnvelope(pos), null)) {
            return result(player, false, "Spawn carrier: insufficient loaded clearance");
        }
        carrier.prepareFixture(pos, yaw);
        if (!world.addFreshEntity(carrier)) {
            return result(player, false, "Spawn carrier");
        }
        if (!player.startRiding(carrier, false)) {
            carrier.discard();
            return result(player, false, "Spawn carrier: pilot seat denied");
        }
        return result(player, true, "Spawn carrier");
    }

    private static int boardNearby(ServerPlayer player) {
        var carriers = player.serverLevel().getEntitiesOfClass(CubeVehicle.class,
                player.getBoundingBox().inflate(BOARD_RANGE),
                cube -> cube.carrier() && cube.isAlive() && player.distanceToSqr(cube) <= BOARD_RANGE * BOARD_RANGE);
        return result(player, !player.isPassenger() && carriers.size() == 1
                && player.startRiding(carriers.get(0), false), "Board nearby carrier");
    }

    static boolean selectFlightStation(ServerPlayer player) {
        if (!(player.getVehicle() instanceof CubeVehicle cube) || !cube.carrier()) {
            return false;
        }
        int seat = cube.getSeatIndex(player);
        return seat == 1 || (seat == 0 ? cube.selectSoloStation(player) : cube.changeSeat(player,1));
    }

    private static int result(ServerPlayer player, boolean accepted, String action) {
        if(!accepted) {
            String reason=switch(action) {
                case "fleet_full" -> "本维度已有两艘母舰";
                case "join_carrier" -> "需要独立战机，且同维度 256 格内有空机位、空仓格且甲板通道空闲";
                case "disband" -> "该机位已变更或战机正在甲板作业，请重新选择";
                case "return_to_carrier","return_key","capture" -> player.getVehicle() instanceof CubeVehicle v
                        ? v.returnProblem() : "请先进入舰载机驾驶席";
                case "launch","pilot_launch" -> "弹射需要锁定甲板、驾驶员就座并保持跑道净空";
                case "Board","board_aircraft" -> "请在二号席选择可用来源箱位，并检查驾驶席与出库通道";
                case "lower_aircraft","request_stow" -> "入库需要战机已停妥且驾驶员离座";
                default -> "当前座位或作业状态无法执行此操作";
            };
            player.sendSystemMessage(Component.literal(reason));
        } else if(action.equals("join_carrier"))player.sendSystemMessage(Component.literal("已连接最近的可用母舰"));
        else if(action.equals("disband"))player.sendSystemMessage(Component.literal("已解除编组，出动机位与仓位已释放"));
        return accepted ? 1 : 0;
    }

    @FunctionalInterface
    private interface Action {
        boolean apply(CubeVehicle vehicle, ServerPlayer player);
    }
}
