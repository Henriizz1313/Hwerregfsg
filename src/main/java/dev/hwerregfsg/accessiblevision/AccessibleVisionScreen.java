package dev.hwerregfsg.accessiblevision;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;

public final class AccessibleVisionScreen extends Screen {
	private final AccessibleVisionClient settings;

	public AccessibleVisionScreen(AccessibleVisionClient settings) {
		super(Component.translatable("screen.accessiblevision.title"));
		this.settings = settings;
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(380, this.width - 32);
		int left = (this.width - panelWidth) / 2;
		int top = Math.max(8, (this.height - 370) / 2);
		int buttonWidth = panelWidth - 40;
		int buttonLeft = left + 20;
		int row = top + 58;

		this.addRenderableWidget(Button.builder(toggleLabel("ores", settings.areOresEnabled()), button -> {
			settings.toggleOres();
			button.setMessage(toggleLabel("ores", settings.areOresEnabled()));
		}).bounds(buttonLeft, row, buttonWidth, 24).build());

		this.addRenderableWidget(Button.builder(toggleLabel("players", settings.arePlayersEnabled()), button -> {
			settings.togglePlayers();
			button.setMessage(toggleLabel("players", settings.arePlayersEnabled()));
		}).bounds(buttonLeft, row + 28, buttonWidth, 24).build());

		this.addRenderableWidget(Button.builder(toggleLabel("warm", settings.isWarmFilterEnabled()), button -> {
			settings.toggleWarmFilter();
			button.setMessage(toggleLabel("warm", settings.isWarmFilterEnabled()));
		}).bounds(buttonLeft, row + 56, buttonWidth, 24).build());

		this.addRenderableWidget(Button.builder(toggleLabel("items", settings.areGroundItemsEnabled()), button -> {
			settings.toggleGroundItems();
			button.setMessage(toggleLabel("items", settings.areGroundItemsEnabled()));
		}).bounds(buttonLeft, row + 84, buttonWidth, 24).build());

		this.addRenderableWidget(Button.builder(toggleLabel("mobs", settings.areMobsEnabled()), button -> {
			settings.toggleMobs();
			button.setMessage(toggleLabel("mobs", settings.areMobsEnabled()));
		}).bounds(buttonLeft, row + 112, buttonWidth, 24).build());

		this.addRenderableWidget(Button.builder(toggleLabel("mining", settings.isMiningFeedbackEnabled()), button -> {
			settings.toggleMiningFeedback();
			button.setMessage(toggleLabel("mining", settings.isMiningFeedbackEnabled()));
		}).bounds(buttonLeft, row + 140, buttonWidth, 24).build());

		EditBox filter = new EditBox(this.font, buttonLeft, row + 191, buttonWidth, 20,
				Component.translatable("screen.accessiblevision.item_filter"));
		filter.setMaxLength(48);
		filter.setValue(settings.getItemFilter());
		filter.setResponder(settings::setItemFilter);
		this.addRenderableWidget(filter);

		this.addRenderableWidget(Button.builder(Component.translatable("screen.accessiblevision.packs"), button -> openPacks())
				.bounds(buttonLeft, row + 220, buttonWidth, 24).build());
		this.addRenderableWidget(Button.builder(Component.translatable("screen.accessiblevision.close"), button -> this.onClose())
				.bounds(buttonLeft, row + 250, buttonWidth, 24).build());
	}

	private void openPacks() {
		Minecraft client = Minecraft.getInstance();
		client.setScreen(new PackSelectionScreen(
				client.getResourcePackRepository(),
				repository -> {
					client.options.updateResourcePacks(repository);
					client.setScreen(this);
				},
				client.getResourcePackDirectory(),
				Component.translatable("screen.accessiblevision.packs.title")));
	}

	private static Component toggleLabel(String feature, boolean enabled) {
		return Component.translatable("screen.accessiblevision." + feature + (enabled ? ".on" : ".off"));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, this.width, this.height, 0xC00B1018);
		int panelWidth = Math.min(400, this.width - 24);
		int left = (this.width - panelWidth) / 2;
		int top = Math.max(8, (this.height - 370) / 2);
		graphics.fill(left, top, left + panelWidth, top + 370, 0xF018202B);
		graphics.fill(left, top, left + panelWidth, top + 2, 0xFF55C7D9);
		graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 17, 0xFFF2F6FA);
		graphics.drawCenteredString(this.font, Component.translatable("screen.accessiblevision.subtitle"), this.width / 2, top + 38, 0xFFB8C6D3);
		graphics.drawString(this.font, Component.translatable("screen.accessiblevision.item_filter"), left + 20, top + 229, 0xFFB8C6D3);
		graphics.drawCenteredString(this.font, Component.translatable("screen.accessiblevision.hint"), this.width / 2, top + 352, 0xFFA8B6C4);
		super.render(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

