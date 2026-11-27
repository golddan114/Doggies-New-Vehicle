package com.liucodex.doggiesnewvehicle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** DNV editor appearance with vanilla button input, narration and tooltip behavior. */
public final class DnvButton extends Button {
  private DnvButton(Builder builder) { super(builder); }

  public static Builder builder(Component label, OnPress onPress) {
    return new Builder(label, onPress) {
      @Override public Button build() { return super.build(DnvButton::new); }
    };
  }

  @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    int width = getWidth();
    int height = getHeight();
    if (width <= 0 || height <= 0) return;
    int x = getX();
    int y = getY();
    boolean focused = active && isFocused();
    graphics.fill(x, y, x + width, y + height, focused ? DnvUiColors.ACCENT : DnvUiColors.BORDER);
    if (width > 2 && height > 2) {
      int fill = !active ? DnvUiColors.DISABLED : focused ? DnvUiColors.ACCENT
          : isHovered ? DnvUiColors.HOVER : DnvUiColors.SECTION;
      graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }
    var font = Minecraft.getInstance().font;
    if (width <= 12 || height < font.lineHeight + 2) return;
    graphics.enableScissor(x + 4, y + 1, x + width - 4, y + height - 1);
    try {
      renderScrollingString(graphics, font, getMessage(), x + 6, y,
          x + width - 6, y + height, focused ? DnvUiColors.ON_ACCENT
              : active ? DnvUiColors.TEXT : DnvUiColors.MUTED);
    } finally {
      graphics.disableScissor();
    }
  }
}
