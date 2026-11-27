package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class DnvCombatCommandsTest {
  @Test
  void registeredServerCommandsKeepDesignationAndCancelWithoutRetiredFireEntries() {
    var dispatcher = new CommandDispatcher<CommandSourceStack>();
    DnvCombatCommands.register(dispatcher);
    var combat = dispatcher.getRoot().getChild("dnv").getChild("combat");
    assertNotNull(combat.getCommand());
    var vehicle = combat.getChild("vehicle");
    assertNotNull(vehicle.getChild("status").getCommand());
    assertNotNull(vehicle.getChild("targets").getCommand());
    assertNotNull(vehicle.getChild("cancel").getCommand());
    assertNotNull(vehicle.getChild("designate").getChild("target").getCommand());
    for (String unsupported : new String[]{"point", "indicator", "hold", "resume", "area", "fire"}) {
      assertNull(vehicle.getChild(unsupported));
    }
  }

  @Test
  void chatChoicesFillAnExplicitCommandRatherThanExecutingAnUnreviewedAction() {
    var result = DnvCombatCommands.suggestion(Component.literal("Target"), "/dnv combat example");
    assertEquals(ClickEvent.Action.SUGGEST_COMMAND, result.getStyle().getClickEvent().getAction());
    assertEquals("/dnv combat example", result.getStyle().getClickEvent().getValue());
  }
}
