package dev.hwerregfsg.accessiblevision;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Random;

public final class AccessibleVisionScreen extends Screen {
	private enum Page { HOME, ORES, PLAYER, ITEMS, SKY, HOTKEYS }

	private final AccessibleVisionClient settings;
	private Page page = Page.HOME;

	public AccessibleVisionScreen(AccessibleVisionClient settings) {
		super(Component.translatable("screen.accessiblevision.title"));
		this.settings = settings;
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(860, this.width - 32);
		int panelHeight = Math.min(420, this.height - 20);
		int left = (this.width - panelWidth) / 2;
		int top = (this.height - panelHeight) / 2;
		int contentLeft = left + 22;
		int contentWidth = panelWidth - 44;
		if (page == Page.HOME) {
			int gap = 14;
			int cardWidth = (contentWidth - gap) / 2;
			addCategory("Erz-Highlights", "Jede Erzart einzeln auswählen", Page.ORES, contentLeft, top + 68, cardWidth, 82);
			addCategory("Spieler", "Umrandung, Rüstung, Hand und Abstand", Page.PLAYER, contentLeft + cardWidth + gap, top + 68, cardWidth, 82);
			addCategory("Items & Mobs", "Bodenitems filtern und Leben anzeigen", Page.ITEMS, contentLeft, top + 164, cardWidth, 82);
			addCategory("Himmel & Look", "Sky-Vorschauen und Bildschirmfilter", Page.SKY, contentLeft + cardWidth + gap, top + 164, cardWidth, 82);
			this.addRenderableWidget(Button.builder(Component.literal("Hotkeys ansehen"), b -> openPage(Page.HOTKEYS))
					.bounds(contentLeft, top + panelHeight - 68, contentWidth / 2 - 7, 28).build());
			this.addRenderableWidget(Button.builder(Component.translatable("screen.accessiblevision.close"), b -> this.onClose())
					.bounds(contentLeft + contentWidth / 2 + 7, top + panelHeight - 68, contentWidth / 2 - 7, 28).build());
			return;
		}

		int buttonWidth = (contentWidth - 14) / 2;
		int rowY = top + 62;
		switch (page) {
			case ORES -> {
				String[][] ores = {
					{"coal", "Kohle"}, {"iron", "Eisen"}, {"copper", "Kupfer"}, {"gold", "Gold"},
					{"redstone", "Redstone"}, {"lapis", "Lapislazuli"}, {"diamond", "Diamant"},
					{"emerald", "Smaragd"}, {"nether_gold", "Nethergold"}, {"quartz", "Netherquarz"},
					{"ancient_debris", "Ancient Debris"}
				};
				addToggle("Erz-Highlights global", settings.areOresEnabled(), settings::toggleOres, contentLeft, rowY, contentWidth, 25);
				for (int i = 0; i < ores.length; i++) {
					String id = ores[i][0];
					int x = contentLeft + (i % 2) * (buttonWidth + 14);
					int y = rowY + 32 + (i / 2) * 29;
					addToggle(ores[i][1], settings.isOreTypeEnabled(id), () -> settings.toggleOreType(id), x, y, buttonWidth, 25);
				}
			}
			case PLAYER -> {
				addToggle("Spieleranzeige", settings.arePlayersEnabled(), settings::togglePlayers, contentLeft, rowY, buttonWidth, 26);
				addToggle("Rote Umrandung", settings.isPlayerOutlineEnabled(), settings::togglePlayerOutline, contentLeft + buttonWidth + 14, rowY, buttonWidth, 26);
				addToggle("Abstands-Streifen", settings.isPlayerDistanceLine(), settings::togglePlayerDistanceLine, contentLeft, rowY + 34, buttonWidth, 26);
				addToggle("Hand-Item über Spieler", settings.isPlayerShowHand(), settings::togglePlayerShowHand, contentLeft + buttonWidth + 14, rowY + 34, buttonWidth, 26);
				addToggle("Rüstungsart anzeigen", settings.isPlayerShowArmor(), settings::togglePlayerShowArmor, contentLeft, rowY + 68, buttonWidth, 26);
				addToggle("Rüstungs-Haltbarkeit", settings.isPlayerShowDurability(), settings::togglePlayerShowDurability, contentLeft + buttonWidth + 14, rowY + 68, buttonWidth, 26);
				this.addRenderableWidget(Button.builder(Component.literal("Konturfarbe wechseln"), b -> { settings.cyclePlayerColor(); openPage(Page.PLAYER); })
						.bounds(contentLeft, rowY + 102, buttonWidth, 26).build());
			}
			case ITEMS -> {
				addToggle("Bodenitem-Umrandung", settings.areGroundItemsEnabled(), settings::toggleGroundItems, contentLeft, rowY, buttonWidth, 26);
				addToggle("Itemname und Anzahl", settings.isGroundItemLabels(), settings::toggleGroundItemLabels, contentLeft + buttonWidth + 14, rowY, buttonWidth, 26);
				addToggle("Mob-Umrandung", settings.areMobsEnabled(), settings::toggleMobs, contentLeft, rowY + 34, buttonWidth, 26);
				addToggle("Mob-Leben anzeigen", settings.isMobHealthLabels(), settings::toggleMobHealthLabels, contentLeft + buttonWidth + 14, rowY + 34, buttonWidth, 26);
				EditBox filter = new EditBox(this.font, contentLeft, rowY + 82, contentWidth, 22, Component.literal("Itemnamen-Filter (leer = alle)"));
				filter.setMaxLength(48);
				filter.setValue(settings.getItemFilter());
				filter.setResponder(settings::setItemFilter);
				this.addRenderableWidget(filter);
			}
			case SKY -> {
				int cardGap = 12;
				int cardWidth = (contentWidth - cardGap * 2) / 3;
				String[] ids = {"aurora", "sunset", "starlight"};
				String[] names = {"Aurora", "Pastell-Sonnenuntergang", "Sternennacht"};
				for (int i = 0; i < ids.length; i++) {
					int x = contentLeft + i * (cardWidth + cardGap);
					int imageY = rowY + 4;
					String theme = ids[i];
					this.addRenderableWidget(Button.builder(Component.literal(names[i] + " aktivieren"), b -> {
						boolean activated = settings.activateSkyPack(Minecraft.getInstance(), theme);
						Minecraft mc = Minecraft.getInstance();
						if (mc.player != null) mc.player.displayClientMessage(Component.literal(activated ? "Sky-Pack aktiviert. Skyboxify muss installiert sein." : "Pack konnte nicht aktiviert werden."), true);
					}).bounds(x, imageY + 120, cardWidth, 28).build());
				}
				this.addRenderableWidget(Button.builder(Component.literal("Resourcepack-Auswahl öffnen"), b -> openResourcePacks())
						.bounds(contentLeft, rowY + 168, contentWidth, 26).build());
				addToggle("Neutralen warmen Filter", settings.isWarmFilterEnabled(), settings::toggleWarmFilter, contentLeft, rowY + 204, buttonWidth, 26);
				addToggle("Abbaufortschritt anzeigen", settings.isMiningFeedbackEnabled(), settings::toggleMiningFeedback, contentLeft + buttonWidth + 14, rowY + 204, buttonWidth, 26);
			}
			case HOTKEYS -> {
				addInfo("Erz-Highlights", settings.getHotkeyOres(), contentLeft, rowY, buttonWidth);
				addInfo("Spieleranzeige", settings.getHotkeyPlayers(), contentLeft + buttonWidth + 14, rowY, buttonWidth);
				addInfo("Warmer Filter", settings.getHotkeyWarm(), contentLeft, rowY + 44, buttonWidth);
				addInfo("Menü öffnen", settings.getHotkeyMenu(), contentLeft + buttonWidth + 14, rowY + 44, buttonWidth);
				this.addRenderableWidget(Button.builder(Component.literal("Steuerung → Tastenbelegung zum Ändern"), b -> openControls())
						.bounds(contentLeft, rowY + 108, contentWidth, 28).build());
			}
			default -> { }
		}
		this.addRenderableWidget(Button.builder(Component.literal("← Zurück"), b -> openPage(Page.HOME))
				.bounds(contentLeft, top + panelHeight - 66, buttonWidth, 28).build());
		this.addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> this.onClose())
				.bounds(contentLeft + buttonWidth + 14, top + panelHeight - 66, buttonWidth, 28).build());
	}

	private void addCategory(String title, String subtitle, Page target, int x, int y, int w, int h) {
		this.addRenderableWidget(Button.builder(Component.literal(title + "  ·  " + subtitle), b -> openPage(target))
				.bounds(x, y, w, h).build());
	}

	private void addToggle(String label, boolean enabled, Runnable toggle, int x, int y, int w, int h) {
		this.addRenderableWidget(Button.builder(Component.literal((enabled ? "●  " : "○  ") + label + (enabled ? "   AN" : "   AUS")), b -> {
			toggle.run();
			openPage(page);
		}).bounds(x, y, w, h).build());
	}

	private void addInfo(String label, String key, int x, int y, int w) {
		this.addRenderableWidget(Button.builder(Component.literal(label + "     [ " + key + " ]"), b -> { })
				.bounds(x, y, w, 30).build());
	}

	private void openPage(Page target) {
		this.page = target;
		this.clearWidgets();
		this.init();
	}

	private void openResourcePacks() {
		Minecraft client = Minecraft.getInstance();
		client.setScreen(new net.minecraft.client.gui.screens.packs.PackSelectionScreen(
				client.getResourcePackRepository(), repository -> {
					client.options.updateResourcePacks(repository);
					client.setScreen(this);
				}, client.getResourcePackDirectory(),
				Component.literal("Ressourcenpakete – eigene Himmel")));
	}

	private void openControls() {
		Minecraft client = Minecraft.getInstance();
		client.setScreen(new net.minecraft.client.gui.screens.options.controls.ControlsScreen(this, client.options));
	}

	private static void drawSkyPreview(GuiGraphics graphics, String theme, int x, int y, int w, int h) {
		graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFFEC74D8);
		int top = theme.equals("starlight") ? 0xFF10163D : 0xFF535A91;
		int bottom = theme.equals("sunset") ? 0xFFFFB77E : theme.equals("starlight") ? 0xFF7A4C89 : 0xFFF7C4C5;
		graphics.fillGradient(x, y, x + w, y + h, top, bottom);
		Random random = new Random(theme.hashCode());
		for (int i = 0; i < (theme.equals("starlight") ? 80 : 30); i++) {
			int sx = x + random.nextInt(Math.max(1, w));
			int sy = y + random.nextInt(Math.max(1, h * 2 / 3));
			graphics.fill(sx, sy, sx + 1, sy + 1, 0xDDF9F3FF);
		}
		for (int i = 0; i < 10; i++) {
			int cloudY = y + h / 2 + random.nextInt(Math.max(1, h / 2));
			int cloudX = x + random.nextInt(Math.max(1, w));
			int cloudWidth = 24 + random.nextInt(Math.max(1, w / 3));
			graphics.fillGradient(cloudX, cloudY, Math.min(x + w, cloudX + cloudWidth), Math.min(y + h, cloudY + 5), 0x35FFE1E7, 0x00FFE1E7);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fillGradient(0, 0, this.width, this.height, 0xFF45416E, 0xFF12121E);
		graphics.fill(0, 0, this.width, this.height, 0xAA070913);
		int panelWidth = Math.min(860, this.width - 32);
		int panelHeight = Math.min(420, this.height - 20);
		int left = (this.width - panelWidth) / 2;
		int top = (this.height - panelHeight) / 2;
		graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xE8111019);
		graphics.fill(left, top, left + panelWidth, top + 3, 0xFFE26BD5);
		graphics.fill(left, top + 3, left + panelWidth, top + 36, 0xE40E0A17);
		String pageTitle = switch (page) {
			case HOME -> "Accessible Vision";
			case ORES -> "Erz-Highlights";
			case PLAYER -> "Spieler-Optionen";
			case ITEMS -> "Items & Mobs";
			case SKY -> "Himmel & Look";
			case HOTKEYS -> "Hotkeys";
		};
		graphics.drawString(this.font, pageTitle, left + 18, top + 13, 0xFFFF8AE8);
		graphics.drawString(this.font, page == Page.HOME ? "Klicke eine Kategorie für einzelne Einstellungen" : "Accessible Vision  •  1.21.11", left + panelWidth - this.font.width(page == Page.HOME ? "Klicke eine Kategorie für einzelne Einstellungen" : "Accessible Vision  •  1.21.11") - 18, top + 13, 0xFFBDB6C9);
		if (page == Page.SKY) {
			int contentLeft = left + 22;
			int contentWidth = panelWidth - 44;
			int cardGap = 12;
			int cardWidth = (contentWidth - cardGap * 2) / 3;
			int imageY = top + 62 + 4;
			String[] themes = {"aurora", "sunset", "starlight"};
			for (int i = 0; i < themes.length; i++) drawSkyPreview(graphics, themes[i], contentLeft + i * (cardWidth + cardGap), imageY, cardWidth, 112);
		}
		if (page == Page.HOME) {
			graphics.drawCenteredString(this.font, "Klare Farben · ruhige Übergänge · große, anklickbare Einstellungen", this.width / 2, top + 45, 0xFFE7E0EF);
			graphics.drawString(this.font, "Hotkeys:  Erze  " + settings.getHotkeyOres() + "   Spieler  " + settings.getHotkeyPlayers() + "   Menü  " + settings.getHotkeyMenu(), left + 22, top + panelHeight - 90, 0xFFC7C1D0);
		} else if (page == Page.SKY) {
			graphics.drawString(this.font, "Wähle ein eigenes Himmel-Pack. Es wird in Ressourcenpakete installiert und aktiviert.", left + 22, top + 43, 0xFFD8D0E4);
			graphics.drawString(this.font, "Für die Himmelanzeige wird Skyboxify für Fabric benötigt.", left + 22, top + panelHeight - 91, 0xFFFFCA8B);
		} else if (page != Page.HOTKEYS) {
			graphics.drawString(this.font, "Hotkey: " + (page == Page.ORES ? settings.getHotkeyOres() : page == Page.PLAYER ? settings.getHotkeyPlayers() : "Menü " + settings.getHotkeyMenu()), left + 22, top + panelHeight - 91, 0xFFC7C1D0);
		}
		super.render(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

