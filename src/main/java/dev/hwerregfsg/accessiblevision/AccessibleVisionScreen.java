package dev.hwerregfsg.accessiblevision;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AccessibleVisionScreen extends Screen {
	private final AccessibleVisionClient settings;
	private Button oresButton;
	private Button playersButton;
	private Button warmButton;

	public AccessibleVisionScreen(AccessibleVisionClient settings) {
		super(Component.translatable("screen.accessiblevision.title"));
		this.settings = settings;
	}

	@Override
	protected void init() {
		int buttonWidth = Math.min(320, this.width - 40);
		int left = (this.width - buttonWidth) / 2;
		int top = this.height / 2 - 38;

		oresButton = this.addRenderableWidget(Button.builder(toggleLabel("ores", settings.areOresEnabled()), button -> {
			settings.toggleOres();
			button.setMessage(toggleLabel("ores", settings.areOresEnabled()));
		}).bounds(left, top, buttonWidth, 24).build());

		playersButton = this.addRenderableWidget(Button.builder(toggleLabel("players", settings.arePlayersEnabled()), button -> {
			settings.togglePlayers();
			button.setMessage(toggleLabel("players", settings.arePlayersEnabled()));
		}).bounds(left, top + 30, buttonWidth, 24).build());

		warmButton = this.addRenderableWidget(Button.builder(toggleLabel("warm", settings.isWarmFilterEnabled()), button -> {
			settings.toggleWarmFilter();
			button.setMessage(toggleLabel("warm", settings.isWarmFilterEnabled()));
		}).bounds(left, top + 60, buttonWidth, 24).build());

		this.addRenderableWidget(Button.builder(Component.translatable("screen.accessiblevision.close"), button -> this.onClose())
				.bounds(left, top + 98, buttonWidth, 24).build());
	}

	private static Component toggleLabel(String feature, boolean enabled) {
		return Component.translatable("screen.accessiblevision." + feature + (enabled ? ".on" : ".off"));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, this.width, this.height, 0xD0171210);
		super.render(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 82, 0xFFFFD89A);
		graphics.drawCenteredString(this.font, Component.translatable("screen.accessiblevision.hint"), this.width / 2, this.height / 2 + 75, 0xFFE8D5C0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
