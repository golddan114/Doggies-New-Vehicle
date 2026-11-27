package com.liucodex.noise.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Runs the compiled entity's exact NBT entrance block; other vehicle/world state
 * is excluded. The captured setter value is what the entity synchronizes. */
public class NoiseEntranceBirthStateTest {
    private static final String ENTITY = "com/liucodex/noise/entity/NoiseVehicleEntity";

    @Test
    void summonNbtWithoutEntranceFieldsKeepsAirborneFirstFrameBeforeFirstServerTick() throws Exception {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "doggievehicle:noise");
        StateProbe entity = restore(tag);
        assertFalse(entity.entranceShown);
        assertAirborneFirstFrame(entity);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 73, 140, -1, Integer.MAX_VALUE})
    void notYetStartedEntranceCannotExposeGroundPoseDuringLoad(int savedTicks) throws Exception {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("NoiseEntranceShown", false);
        tag.putInt("NoiseEntranceTicks", savedTicks);
        assertAirborneFirstFrame(restore(tag));
    }

    @ParameterizedTest
    @CsvSource({"140,140", "95,95", "1,1", "0,0", "-1,0", "2147483647,140"})
    void startedEntranceRestoresProgressAndCompletedEntranceNeverReplays(int savedTicks, int expected) throws Exception {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("NoiseEntranceShown", true);
        tag.putInt("NoiseEntranceTicks", savedTicks);
        StateProbe entity = restore(tag);
        assertTrue(entity.entranceShown);
        assertEquals(expected, entity.remaining);
        double elapsed = NoiseDropTimeline.renderElapsedTicks(entity.remaining, 0);
        if (expected == 0) {
            assertEquals(0, NoiseDropTimeline.vehicleVerticalOffset(elapsed));
            assertFalse(NoiseDropTimeline.isRigVisible(elapsed));
        }
    }

    @Test
    void completedFlagWithoutTimerDoesNotReplayEntrance() throws Exception {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("NoiseEntranceShown", true);
        assertEquals(0, restore(tag).remaining);
    }

    @Test
    void carrierHeightSurvivesActualNbtReadWithoutReplayingCompletedEntrance() throws Exception {
        CompoundTag tag=new CompoundTag();
        assertEquals(-1,restore(tag).dropStart);
        tag.putFloat("NoiseMothershipDropStart",12);
        assertEquals(12,restore(tag).dropStart);
        assertEquals(140,restore(tag).remaining);
        tag.putBoolean("NoiseEntranceShown",true);tag.putInt("NoiseEntranceTicks",0);
        assertEquals(12,restore(tag).dropStart);assertEquals(0,restore(tag).remaining);
        tag.putFloat("NoiseMothershipDropStart",Float.NaN);
        assertEquals(-1,restore(tag).dropStart);
    }

    private static void assertAirborneFirstFrame(StateProbe entity) {
        assertEquals(NoiseDropTimeline.TOTAL_TICKS, entity.remaining,
                "Do not publish an idle ground pose before the first entrance tick");
        assertEquals(48, NoiseDropTimeline.vehicleVerticalOffset(
                NoiseDropTimeline.renderElapsedTicks(entity.remaining, 0)), 1.0E-6);
        for (float partial : new float[]{0, 0.5F, 0.999F}) {
            double elapsed = NoiseDropTimeline.renderElapsedTicks(entity.remaining, partial);
            assertTrue(NoiseDropTimeline.vehicleVerticalOffset(elapsed) > 40);
            assertTrue(NoiseDropTimeline.isRigVisible(elapsed));
        }
    }

    private static StateProbe restore(CompoundTag tag) throws Exception {
        ClassNode source = new ClassNode();
        try (var input = NoiseEntranceBirthStateTest.class.getClassLoader().getResourceAsStream(ENTITY + ".class")) {
            new ClassReader(java.util.Objects.requireNonNull(input)).accept(source, ClassReader.SKIP_FRAMES);
        }
        var read = source.methods.stream().filter(method -> method.name.equals("readAdditionalSaveData"))
                .findFirst().orElseThrow();
        AbstractInsnNode first = null, last = null;
        for (var instruction : read.instructions) {
            if (instruction instanceof LdcInsnNode constant && "NoiseEntranceShown".equals(constant.cst)) {
                first = instruction.getPrevious().getPrevious(); // this, tag, key
            }
            if (first != null && instruction instanceof MethodInsnNode call && call.name.equals("setMothershipDropStart")) {
                last = instruction;
                break;
            }
        }
        assertNotNull(first, "Missing entity entrance NBT read");
        assertNotNull(last, "Missing entity entrance state write");
        var body = new MethodNode(Opcodes.ACC_PUBLIC, "restore", "(Lnet/minecraft/nbt/CompoundTag;)V", null, null);
        Map<LabelNode, LabelNode> labels = new HashMap<>();
        for (var instruction : read.instructions) {
            if (instruction instanceof LabelNode label) labels.put(label, new LabelNode());
        }
        for (var instruction = first; ; instruction = instruction.getNext()) {
            if (instruction.getType() != AbstractInsnNode.LINE) body.instructions.add(instruction.clone(labels));
            if (instruction == last) break;
        }
        body.visitInsn(Opcodes.RETURN);
        ClassNode probe = new ClassNode();
        probe.version = Opcodes.V17;
        probe.access = Opcodes.ACC_PUBLIC;
        probe.name = ENTITY;
        probe.superName = Type.getInternalName(StateProbe.class);
        probe.methods.add(body);
        var constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, probe.superName, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        probe.methods.add(constructor);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        probe.accept(new ClassRemapper(writer, new SimpleRemapper(ENTITY, ENTITY + "BirthProbe")));
        byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader(NoiseEntranceBirthStateTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        StateProbe entity = (StateProbe) type.getConstructor().newInstance();
        type.getMethod("restore", CompoundTag.class).invoke(entity, tag);
        return entity;
    }

    public static class StateProbe {
        public boolean entranceShown;
        public int remaining = NoiseDropTimeline.TOTAL_TICKS;
        public float dropStart=-1;
        public void setEntranceTicks(int value) { remaining = value; }
        public void setMothershipDropStart(float value) { dropStart=NoiseDropTimeline.sanitizeStartOffset(value); }
    }
}
