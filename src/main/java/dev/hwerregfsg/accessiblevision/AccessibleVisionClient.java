package dev.hwerregfsg.accessiblevision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Properties;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

public final class AccessibleVisionClient implements ClientModInitializer {
	private static AccessibleVisionClient instance;
	private static final RenderPipeline HIGHLIGHT_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath("accessiblevision", "pipeline/highlight"))
					.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
					.build());
	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath("accessiblevision", "accessibility"));
	private final Map<BlockPos, Integer> oreMarkers = new HashMap<>();
	private BufferBuilder buffer;
	private MappableRingBuffer vertexBuffer;
	private KeyMapping toggleOres;
	private KeyMapping togglePlayers;
	private KeyMapping toggleWarmFilter;
	private KeyMapping openMenu;
	private boolean oresEnabled = true;
	private boolean playersEnabled = true;
	private boolean warmFilterEnabled = false;
	private boolean groundItemsEnabled = true;
	private boolean mobsEnabled = true;
	private boolean miningFeedbackEnabled = true;
	private boolean playerOutlineEnabled = true;
	private boolean playerShowHand = true;
	private boolean playerShowArmor = true;
	private boolean playerShowDurability = true;
	private boolean playerDistanceLine = true;
	private boolean groundItemLabels = true;
	private boolean mobHealthLabels = true;
	private int playerColorIndex = 0;
	private final Map<String, Boolean> oreTypeEnabled = new HashMap<>();
	private String itemFilter = "";
	private int ticksSinceScan;
	private static final int[] PLAYER_COLORS = {0xFFFF4B5C, 0xFFFF55C8, 0xFF5AD8FF, 0xFFFFC14A};

	public AccessibleVisionClient() {
		for (String ore : List.of("coal", "iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald", "nether_gold", "quartz", "ancient_debris")) {
			oreTypeEnabled.put(ore, true);
		}
	}

	public static AccessibleVisionClient getInstance() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		loadConfig();
		toggleOres = registerKey("key.accessiblevision.toggle_ores", GLFW.GLFW_KEY_O);
		togglePlayers = registerKey("key.accessiblevision.toggle_players", GLFW.GLFW_KEY_P);
		toggleWarmFilter = registerKey("key.accessiblevision.toggle_warm_filter", GLFW.GLFW_KEY_V);
		openMenu = registerKey("key.accessiblevision.open_menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(this::renderWorld);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("accessiblevision", "accessibility_hud"), this::renderHud);
	}

	private static KeyMapping registerKey(String translationKey, int key) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping(translationKey, InputConstants.Type.KEYSYM, key, CATEGORY));
	}

	private void tick(Minecraft client) {
		while (openMenu.consumeClick()) {
			client.setScreen(new AccessibleVisionScreen(this));
		}
		while (toggleOres.consumeClick()) {
			 oresEnabled = !oresEnabled;
			 if (client.player != null) client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Erzmarkierungen: " + (oresEnabled ? "An" : "Aus")), true);
			 saveConfig();
		}
		while (togglePlayers.consumeClick()) {
			playersEnabled = !playersEnabled;
			if (client.player != null) client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Spieleranzeige: " + (playersEnabled ? "An" : "Aus")), true);
			saveConfig();
		}
		while (toggleWarmFilter.consumeClick()) {
			warmFilterEnabled = !warmFilterEnabled;
			if (client.player != null) client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Warmer Bildschirmfilter: " + (warmFilterEnabled ? "An" : "Aus")), true);
			saveConfig();
		}

		if (client.level == null || client.player == null) {
			oreMarkers.clear();
			return;
		}
		if (++ticksSinceScan >= 10) {
			ticksSinceScan = 0;
			scanNearbyOres(client);
		}
	}

	public boolean areOresEnabled() {
		return oresEnabled;
	}

	public boolean arePlayersEnabled() {
		return playersEnabled;
	}

	public boolean isWarmFilterEnabled() {
		return warmFilterEnabled;
	}

	public boolean areGroundItemsEnabled() {
		return groundItemsEnabled;
	}

	public boolean areMobsEnabled() {
		return mobsEnabled;
	}

	public boolean isMiningFeedbackEnabled() {
		return miningFeedbackEnabled;
	}

	public boolean isPlayerOutlineEnabled() { return playerOutlineEnabled; }
	public boolean isPlayerShowHand() { return playerShowHand; }
	public boolean isPlayerShowArmor() { return playerShowArmor; }
	public boolean isPlayerShowDurability() { return playerShowDurability; }
	public boolean isPlayerDistanceLine() { return playerDistanceLine; }
	public boolean isGroundItemLabels() { return groundItemLabels; }
	public boolean isMobHealthLabels() { return mobHealthLabels; }
	public int getPlayerColor() { return PLAYER_COLORS[playerColorIndex]; }
	public String getHotkeyOres() { return toggleOres.getTranslatedKeyMessage().getString(); }
	public String getHotkeyPlayers() { return togglePlayers.getTranslatedKeyMessage().getString(); }
	public String getHotkeyWarm() { return toggleWarmFilter.getTranslatedKeyMessage().getString(); }
	public String getHotkeyMenu() { return openMenu.getTranslatedKeyMessage().getString(); }
	public boolean isOreTypeEnabled(String type) { return oreTypeEnabled.getOrDefault(type, true); }
	public void toggleOreType(String type) { oreTypeEnabled.put(type, !isOreTypeEnabled(type)); saveConfig(); }
	public void togglePlayerOutline() { playerOutlineEnabled = !playerOutlineEnabled; saveConfig(); }
	public void togglePlayerShowHand() { playerShowHand = !playerShowHand; saveConfig(); }
	public void togglePlayerShowArmor() { playerShowArmor = !playerShowArmor; saveConfig(); }
	public void togglePlayerShowDurability() { playerShowDurability = !playerShowDurability; saveConfig(); }
	public void togglePlayerDistanceLine() { playerDistanceLine = !playerDistanceLine; saveConfig(); }
	public void toggleGroundItemLabels() { groundItemLabels = !groundItemLabels; saveConfig(); }
	public void toggleMobHealthLabels() { mobHealthLabels = !mobHealthLabels; saveConfig(); }
	public void cyclePlayerColor() { playerColorIndex = (playerColorIndex + 1) % PLAYER_COLORS.length; saveConfig(); }

	public String getItemFilter() {
		return itemFilter;
	}

	public void setItemFilter(String filter) {
		itemFilter = filter == null ? "" : filter.trim();
		saveConfig();
	}

	public void toggleOres() {
		 oresEnabled = !oresEnabled;
		 if (!oresEnabled) oreMarkers.clear();
		saveConfig();
	}

	public void togglePlayers() {
		playersEnabled = !playersEnabled;
		saveConfig();
	}

	public void toggleWarmFilter() {
		warmFilterEnabled = !warmFilterEnabled;
		saveConfig();
	}

	public void toggleGroundItems() {
		groundItemsEnabled = !groundItemsEnabled;
		saveConfig();
	}

	public void toggleMobs() {
		mobsEnabled = !mobsEnabled;
		saveConfig();
	}

	public void toggleMiningFeedback() {
		miningFeedbackEnabled = !miningFeedbackEnabled;
		saveConfig();
	}

	private Path configPath() {
		return Minecraft.getInstance().getResourcePackDirectory().getParent().resolve("config").resolve("accessiblevision.properties");
	}

	private void loadConfig() {
		Properties properties = new Properties();
		try {
			Path path = configPath();
			if (!Files.exists(path)) return;
			try (var input = Files.newInputStream(path)) { properties.load(input); }
			 oresEnabled = Boolean.parseBoolean(properties.getProperty("ores", Boolean.toString(oresEnabled)));
			playersEnabled = Boolean.parseBoolean(properties.getProperty("players", Boolean.toString(playersEnabled)));
			warmFilterEnabled = Boolean.parseBoolean(properties.getProperty("warm", Boolean.toString(warmFilterEnabled)));
			groundItemsEnabled = Boolean.parseBoolean(properties.getProperty("groundItems", Boolean.toString(groundItemsEnabled)));
			mobsEnabled = Boolean.parseBoolean(properties.getProperty("mobs", Boolean.toString(mobsEnabled)));
			miningFeedbackEnabled = Boolean.parseBoolean(properties.getProperty("miningFeedback", Boolean.toString(miningFeedbackEnabled)));
			playerOutlineEnabled = Boolean.parseBoolean(properties.getProperty("playerOutline", Boolean.toString(playerOutlineEnabled)));
			playerShowHand = Boolean.parseBoolean(properties.getProperty("playerShowHand", Boolean.toString(playerShowHand)));
			playerShowArmor = Boolean.parseBoolean(properties.getProperty("playerShowArmor", Boolean.toString(playerShowArmor)));
			playerShowDurability = Boolean.parseBoolean(properties.getProperty("playerShowDurability", Boolean.toString(playerShowDurability)));
			playerDistanceLine = Boolean.parseBoolean(properties.getProperty("playerDistanceLine", Boolean.toString(playerDistanceLine)));
			groundItemLabels = Boolean.parseBoolean(properties.getProperty("groundItemLabels", Boolean.toString(groundItemLabels)));
			mobHealthLabels = Boolean.parseBoolean(properties.getProperty("mobHealthLabels", Boolean.toString(mobHealthLabels)));
			itemFilter = properties.getProperty("itemFilter", itemFilter);
			playerColorIndex = Math.max(0, Math.min(PLAYER_COLORS.length - 1, Integer.parseInt(properties.getProperty("playerColor", Integer.toString(playerColorIndex)))));
			for (String ore : oreTypeEnabled.keySet()) oreTypeEnabled.put(ore, Boolean.parseBoolean(properties.getProperty("ore." + ore, "true")));
		} catch (IOException | NumberFormatException exception) {
			// Keep the in-memory defaults if the config is missing or malformed.
		}
	}

	private void saveConfig() {
		Properties properties = new Properties();
		properties.setProperty("ores", Boolean.toString(oresEnabled));
		properties.setProperty("players", Boolean.toString(playersEnabled));
		properties.setProperty("warm", Boolean.toString(warmFilterEnabled));
		properties.setProperty("groundItems", Boolean.toString(groundItemsEnabled));
		properties.setProperty("mobs", Boolean.toString(mobsEnabled));
		properties.setProperty("miningFeedback", Boolean.toString(miningFeedbackEnabled));
		properties.setProperty("playerOutline", Boolean.toString(playerOutlineEnabled));
		properties.setProperty("playerShowHand", Boolean.toString(playerShowHand));
		properties.setProperty("playerShowArmor", Boolean.toString(playerShowArmor));
		properties.setProperty("playerShowDurability", Boolean.toString(playerShowDurability));
		properties.setProperty("playerDistanceLine", Boolean.toString(playerDistanceLine));
		properties.setProperty("groundItemLabels", Boolean.toString(groundItemLabels));
		properties.setProperty("mobHealthLabels", Boolean.toString(mobHealthLabels));
		properties.setProperty("itemFilter", itemFilter);
		properties.setProperty("playerColor", Integer.toString(playerColorIndex));
		for (Map.Entry<String, Boolean> entry : oreTypeEnabled.entrySet()) properties.setProperty("ore." + entry.getKey(), Boolean.toString(entry.getValue()));
		try {
			Path path = configPath();
			Files.createDirectories(path.getParent());
			try (var output = Files.newOutputStream(path)) { properties.store(output, "Accessible Vision settings"); }
		} catch (IOException exception) {
			// The UI remains usable even when the config cannot be written.
		}
	}

	public boolean activateSkyPack(Minecraft client, String theme) {
		String normalized = theme.toLowerCase();
		String packName = "AccessibleVision-" + normalized;
		Path packPath = client.getResourcePackDirectory().resolve(packName + ".zip");
		try {
			Files.createDirectories(packPath.getParent());
			writeSkyPack(packPath, normalized);
			var repository = client.getResourcePackRepository();
			repository.reload();
			String packId = repository.getAvailableIds().stream()
					.filter(id -> id.toLowerCase().contains(packName.toLowerCase()))
					.findFirst().orElse(null);
			if (packId == null) return false;
			List<String> selected = new ArrayList<>((Collection<String>) repository.getSelectedIds());
			selected.removeIf(id -> id.toLowerCase().startsWith("file/accessiblevision-"));
			selected.add(packId);
			repository.setSelected(selected);
			client.options.updateResourcePacks(repository);
			return true;
		} catch (IOException exception) {
			return false;
		}
	}

	private void writeSkyPack(Path destination, String theme) throws IOException {
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destination))) {
			writeZipText(zip, "pack.mcmeta", "{\"pack\":{\"min_format\":[75,0],\"max_format\":[75,0],\"description\":\"Accessible Vision sky: " + theme + "\"}}\n");
			writeZipText(zip, "assets/minecraft/optifine/sky/world0/sky1.properties", "source=./sky1.png\nblend=replace\nrotate=false\n\n");
			BufferedImage sky = createSkyImage(theme, 512, 256);
			ByteArrayOutputStream skyBytes = new ByteArrayOutputStream();
			ImageIO.write(sky, "png", skyBytes);
			writeZipBytes(zip, "assets/minecraft/optifine/sky/world0/sky1.png", skyBytes.toByteArray());
			BufferedImage icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics = icon.createGraphics();
			graphics.drawImage(sky, 0, 0, 64, 64, 0, 52, sky.getWidth(), 204, null);
			graphics.dispose();
			ByteArrayOutputStream iconBytes = new ByteArrayOutputStream();
			ImageIO.write(icon, "png", iconBytes);
			writeZipBytes(zip, "pack.png", iconBytes.toByteArray());
		}
	}

	private void writeZipText(ZipOutputStream zip, String name, String text) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	private void writeZipBytes(ZipOutputStream zip, String name, byte[] data) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(data);
		zip.closeEntry();
	}

	private BufferedImage createSkyImage(String theme, int width, int height) {
		Color top = theme.equals("starlight") ? new Color(14, 18, 53) : new Color(75, 79, 139);
		Color middle = theme.equals("sunset") ? new Color(180, 132, 167) : theme.equals("starlight") ? new Color(59, 47, 112) : new Color(152, 136, 183);
		Color horizon = theme.equals("sunset") ? new Color(255, 181, 133) : theme.equals("starlight") ? new Color(121, 73, 142) : new Color(249, 197, 190);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			float t = y / (float) (height - 1);
			Color color = t < 0.55f ? mix(top, middle, t / 0.55f) : mix(middle, horizon, (t - 0.55f) / 0.45f);
			for (int x = 0; x < width; x++) image.setRGB(x, y, color.getRGB());
		}
		Random random = new Random(theme.hashCode());
		for (int i = 0; i < 28; i++) {
			double cx = random.nextDouble() * width;
			double cy = height * (0.52 + random.nextDouble() * 0.33);
			double rx = width * (0.035 + random.nextDouble() * 0.10);
			double ry = height * (0.025 + random.nextDouble() * 0.07);
			Color cloud = (i % 2 == 0) ? new Color(255, 204, 218) : new Color(210, 205, 239);
			for (int y = Math.max(0, (int) (cy - ry * 2)); y < Math.min(height, cy + ry * 2); y++) {
				for (int x = Math.max(0, (int) (cx - rx * 2)); x < Math.min(width, cx + rx * 2); x++) {
					double q = Math.pow((x - cx) / rx, 2) + Math.pow((y - cy) / ry, 2);
					if (q < 5) blendSkyPixel(image, x, y, cloud, (int) (32 * Math.exp(-q * 1.15)));
				}
			}
		}
		int stars = theme.equals("starlight") ? 320 : 95;
		for (int i = 0; i < stars; i++) {
			int x = random.nextInt(width), y = random.nextInt(height / 2);
			image.setRGB(x, y, new Color(255, 246, 240).getRGB());
		}
		return image;
	}

	private static Color mix(Color a, Color b, float t) {
		return new Color((int) (a.getRed() * (1 - t) + b.getRed() * t), (int) (a.getGreen() * (1 - t) + b.getGreen() * t), (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
	}

	private static void blendSkyPixel(BufferedImage image, int x, int y, Color color, int alpha) {
		int old = image.getRGB(x, y);
		Color base = new Color(old);
		float a = Math.min(255, alpha) / 255.0f;
		image.setRGB(x, y, new Color((int) (base.getRed() * (1 - a) + color.getRed() * a),
				(int) (base.getGreen() * (1 - a) + color.getGreen() * a), (int) (base.getBlue() * (1 - a) + color.getBlue() * a)).getRGB());
	}

	private void scanNearbyOres(Minecraft client) {
		oreMarkers.clear();
		if (!oresEnabled || client.level == null || client.player == null) return;
		BlockPos center = client.player.blockPosition();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int radius = 16;
		for (int x = -radius; x <= radius; x++) {
			for (int y = -radius; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
					if (x * x + y * y + z * z > radius * radius) continue;
					cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
					if (!client.level.hasChunkAt(cursor)) continue;
					BlockState state = client.level.getBlockState(cursor);
					int color = oreColor(state);
					if (color != 0) oreMarkers.put(cursor.immutable(), color);
				}
			}
		}
	}

	private int oreColor(BlockState state) {
		if (state.is(Blocks.ANCIENT_DEBRIS) && isOreTypeEnabled("ancient_debris")) return 0xFFFF403A;
		if (state.is(BlockTags.DIAMOND_ORES) && isOreTypeEnabled("diamond")) return 0xFF40E8FF;
		if (state.is(BlockTags.EMERALD_ORES) && isOreTypeEnabled("emerald")) return 0xFF47FF87;
		if (state.is(Blocks.NETHER_GOLD_ORE) && isOreTypeEnabled("nether_gold")) return 0xFFFFC62E;
		if (state.is(Blocks.NETHER_QUARTZ_ORE) && isOreTypeEnabled("quartz")) return 0xFFFFA98D;
		if (state.is(BlockTags.GOLD_ORES) && isOreTypeEnabled("gold")) return 0xFFFFD43B;
		if (state.is(BlockTags.IRON_ORES) && isOreTypeEnabled("iron")) return 0xFFFF9654;
		if (state.is(BlockTags.COPPER_ORES) && isOreTypeEnabled("copper")) return 0xFFFF815E;
		if (state.is(BlockTags.LAPIS_ORES) && isOreTypeEnabled("lapis")) return 0xFF6294FF;
		if (state.is(BlockTags.REDSTONE_ORES) && isOreTypeEnabled("redstone")) return 0xFFFF4D62;
		if (state.is(BlockTags.COAL_ORES) && isOreTypeEnabled("coal")) return 0xFFE4E8F2;
		return 0;
	}

	private void renderWorld(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null || (!oresEnabled && !playersEnabled && !groundItemsEnabled && !mobsEnabled)) return;
		PoseStack matrices = context.matrices();
		Vec3 camera = context.worldState().cameraRenderState.pos;
		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		buffer = new BufferBuilder(ALLOCATOR, HIGHLIGHT_PIPELINE.getVertexFormatMode(), HIGHLIGHT_PIPELINE.getVertexFormat());
		int markerCount = 0;
		float pulse = 0.72f + 0.20f * (float) ((Math.sin(System.nanoTime() / 420_000_000.0) + 1.0) * 0.5);
		if (oresEnabled) {
			for (Map.Entry<BlockPos, Integer> entry : oreMarkers.entrySet()) {
				BlockPos pos = entry.getKey();
				if (!isBlockVisible(client, pos)) continue;
				float inset = 0.08f;
				addBox(matrices.last().pose(), pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
						pos.getX() + 1f - inset, pos.getY() + 1f - inset, pos.getZ() + 1f - inset, entry.getValue(), 0.10f);
				addOutline(matrices.last().pose(), pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
						pos.getX() + 1f - inset, pos.getY() + 1f - inset, pos.getZ() + 1f - inset, entry.getValue(), pulse);
				markerCount++;
			}
		}
		if (playersEnabled && playerOutlineEnabled) {
			for (Player player : client.level.players()) {
				if (player == client.player || player.isRemoved() || player.distanceToSqr(client.player) > 32 * 32
						|| !client.player.hasLineOfSight(player)) continue;
				AABB box = player.getBoundingBox();
				addBox(matrices.last().pose(), (float) box.minX, (float) box.minY, (float) box.minZ,
						(float) box.maxX, (float) box.maxY, (float) box.maxZ, getPlayerColor(), 0.07f);
				addOutline(matrices.last().pose(), (float) box.minX, (float) box.minY, (float) box.minZ,
						(float) box.maxX, (float) box.maxY, (float) box.maxZ, getPlayerColor(), pulse);
				markerCount++;
			}
		}
		if (groundItemsEnabled) {
			for (ItemEntity item : client.level.getEntitiesOfClass(ItemEntity.class, client.player.getBoundingBox().inflate(32),
					entity -> !entity.isRemoved() && client.player.hasLineOfSight(entity) && matchesItemFilter(entity.getItem()))) {
				AABB box = item.getBoundingBox();
				addOutline(matrices.last().pose(), (float) box.minX, (float) box.minY, (float) box.minZ,
						(float) box.maxX, (float) box.maxY, (float) box.maxZ, 0xFFFFD75E, pulse);
				markerCount++;
			}
		}
		if (mobsEnabled) {
			for (Mob mob : client.level.getEntitiesOfClass(Mob.class, client.player.getBoundingBox().inflate(32),
					entity -> !entity.isRemoved() && entity.isAlive() && client.player.hasLineOfSight(entity))) {
				AABB box = mob.getBoundingBox();
				addOutline(matrices.last().pose(), (float) box.minX, (float) box.minY, (float) box.minZ,
						(float) box.maxX, (float) box.maxY, (float) box.maxZ, 0xFF55E6C1, pulse);
				markerCount++;
			}
		}
		matrices.popPose();
		if (markerCount == 0) {
			buffer = null;
			return;
		}
		MeshData built = buffer.buildOrThrow();
		try {
			draw(client, built, HIGHLIGHT_PIPELINE);
		} finally {
			buffer = null;
		}
	}

	private boolean isBlockVisible(Minecraft client, BlockPos pos) {
		Vec3 center = Vec3.atCenterOf(pos);
		Vec3 eye = client.player.getEyePosition();
		for (Vec3 offset : List.of(new Vec3(0, .48, 0), new Vec3(0, -.48, 0), new Vec3(.48, 0, 0),
				new Vec3(-.48, 0, 0), new Vec3(0, 0, .48), new Vec3(0, 0, -.48))) {
			BlockHitResult hit = client.level.clip(new ClipContext(eye, center.add(offset),
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
			if (hit.getType() == BlockHitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) return true;
		}
		return false;
	}

	private static void addBox(Matrix4fc matrix, float x1, float y1, float z1, float x2, float y2, float z2, int argb, float alpha) {
		float r = ((argb >> 16) & 255) / 255f;
		float g = ((argb >> 8) & 255) / 255f;
		float b = (argb & 255) / 255f;
		face(matrix, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, r,g,b,alpha);
		face(matrix, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, r,g,b,alpha);
		face(matrix, x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, r,g,b,alpha);
		face(matrix, x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, r,g,b,alpha);
		face(matrix, x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, r,g,b,alpha);
		face(matrix, x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, r,g,b,alpha);
	}

	private static void addOutline(Matrix4fc matrix, float x1, float y1, float z1, float x2, float y2, float z2, int argb, float alpha) {
		float w = 0.035f;
		addBox(matrix, x1, y1, z1, x1 + w, y2, z1 + w, argb, alpha);
		addBox(matrix, x2 - w, y1, z1, x2, y2, z1 + w, argb, alpha);
		addBox(matrix, x1, y1, z2 - w, x1 + w, y2, z2, argb, alpha);
		addBox(matrix, x2 - w, y1, z2 - w, x2, y2, z2, argb, alpha);
		addBox(matrix, x1, y1, z1, x2, y1 + w, z1 + w, argb, alpha);
		addBox(matrix, x1, y2 - w, z1, x2, y2, z1 + w, argb, alpha);
		addBox(matrix, x1, y1, z2 - w, x2, y1 + w, z2, argb, alpha);
		addBox(matrix, x1, y2 - w, z2 - w, x2, y2, z2, argb, alpha);
		addBox(matrix, x1, y1, z1, x1 + w, y1 + w, z2, argb, alpha);
		addBox(matrix, x2 - w, y1, z1, x2, y1 + w, z2, argb, alpha);
		addBox(matrix, x1, y2 - w, z1, x1 + w, y2, z2, argb, alpha);
		addBox(matrix, x2 - w, y2 - w, z1, x2, y2, z2, argb, alpha);
	}

	private static void face(Matrix4fc m, float ax,float ay,float az, float bx,float by,float bz, float cx,float cy,float cz, float dx,float dy,float dz, float r,float g,float b,float a) {
		BufferBuilder out = instance.buffer;
		out.addVertex(m, ax, ay, az).setColor(r,g,b,a);
		out.addVertex(m, bx, by, bz).setColor(r,g,b,a);
		out.addVertex(m, cx, cy, cz).setColor(r,g,b,a);
		out.addVertex(m, dx, dy, dz).setColor(r,g,b,a);
	}

	private void renderHud(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) return;
		if (warmFilterEnabled) graphics.fill(0, 0, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), 0x12FFB35C);
		if (miningFeedbackEnabled && client.gameMode != null && client.gameMode.isDestroying()) {
			int stage = Math.max(0, Math.min(9, client.gameMode.getDestroyStage()));
			drawMiningFeedback(graphics, client, (stage + 1) / 10.0f);
		}
		if (playersEnabled && (playerShowHand || playerShowArmor || playerDistanceLine)) {
			for (Player player : nearbyPlayers(client)) drawPlayerLabel(graphics, client, player);
		}
		if (groundItemsEnabled) {
			for (ItemEntity item : client.level.getEntitiesOfClass(ItemEntity.class, client.player.getBoundingBox().inflate(32),
					entity -> !entity.isRemoved() && client.player.hasLineOfSight(entity) && matchesItemFilter(entity.getItem()))) {
				if (groundItemLabels) drawEntityLabel(graphics, client, item, item.getItem().getHoverName().getString() + " ×" + item.getItem().getCount(), 0xFFFFE17A);
			}
		}
		if (mobsEnabled) {
			for (Mob mob : client.level.getEntitiesOfClass(Mob.class, client.player.getBoundingBox().inflate(32),
					entity -> !entity.isRemoved() && entity.isAlive() && client.player.hasLineOfSight(entity))) {
				if (mobHealthLabels) drawEntityLabel(graphics, client, mob, mob.getName().getString() + "  " + Math.round(mob.getHealth()) + "/" + Math.round(mob.getMaxHealth()) + " HP", 0xFF8FFFF0);
			}
		}
	}

	private void drawMiningFeedback(GuiGraphics graphics, Minecraft client, float progress) {
		int centerX = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 + 22;
		int width = 112;
		int left = centerX - width / 2;
		graphics.fill(left - 2, y - 2, left + width + 2, y + 8, 0xD0101720);
		graphics.fill(left, y, left + width, y + 4, 0xFF27333E);
		int fill = Math.max(2, Math.round(width * progress));
		graphics.fill(left, y, left + fill, y + 4, 0xFF55C7D9);
		graphics.fill(left, y - 1, left + fill, y, 0xAA9AF5FF);
		graphics.drawCenteredString(client.font, Component.literal("Abbau  " + Math.round(progress * 100) + "%"), centerX, y + 9, 0xFFE8F7FA);
	}

	private List<Player> nearbyPlayers(Minecraft client) {
		List<Player> result = new ArrayList<>();
		for (Player player : client.level.players()) {
			if (player != client.player && !player.isRemoved() && player.distanceToSqr(client.player) <= 32 * 32 && client.player.hasLineOfSight(player)) result.add(player);
		}
		result.sort(Comparator.comparingDouble(player -> player.distanceToSqr(client.player)));
		return result;
	}

	private boolean matchesItemFilter(ItemStack stack) {
		return itemFilter.isEmpty() || stack.getHoverName().getString().toLowerCase().contains(itemFilter.toLowerCase());
	}

	private void drawPlayerLabel(GuiGraphics graphics, Minecraft client, Player player) {
		List<String> lines = new ArrayList<>();
		float distance = player.distanceTo(client.player);
		lines.add(player.getName().getString() + (playerDistanceLine ? "  •  " + Math.round(distance) + " m" : ""));
		if (playerShowHand) {
			ItemStack held = player.getMainHandItem();
			lines.add("Hand: " + (held.isEmpty() ? "leer" : held.getHoverName().getString()));
		}
		if (playerShowArmor) {
			for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
				ItemStack armor = player.getItemBySlot(slot);
				if (armor.isEmpty()) continue;
				String name = armor.getHoverName().getString();
				String durability = armor.isDamageableItem() && playerShowDurability
						? " " + Math.max(0, 100 - Math.round(armor.getDamageValue() * 100f / armor.getMaxDamage())) + "%" : "";
				lines.add(name + durability);
			}
		}
		float stripe = playerDistanceLine ? Math.max(0.12f, 1.0f - distance / 32.0f) : 0.0f;
		drawLabel(graphics, client, player, lines, getPlayerColor(), stripe);
	}

	private void drawEntityLabel(GuiGraphics graphics, Minecraft client, net.minecraft.world.entity.Entity entity, String text, int color) {
		drawLabel(graphics, client, entity, List.of(text), color, 0.0f);
	}

	private void drawLabel(GuiGraphics graphics, Minecraft client, net.minecraft.world.entity.Entity entity, List<String> lines, int color, float stripeProgress) {
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		Vec3 target = entity.position().add(0, entity.getBbHeight() + 0.25, 0);
		Vec3 relative = target.subtract(camera);
		double horizontal = Math.sqrt(relative.x * relative.x + relative.z * relative.z);
		if (horizontal < 0.01) return;
		double targetYaw = Math.toDegrees(Math.atan2(-relative.x, relative.z));
		double yawDelta = Math.toRadians(net.minecraft.util.Mth.wrapDegrees((float) (targetYaw - client.player.getYRot())));
		if (Math.abs(yawDelta) > Math.toRadians(80)) return;
		double pitch = Math.toRadians(client.player.getXRot());
		double elevation = Math.atan2(relative.y, horizontal) + pitch;
		double fov = Math.toRadians(client.options.fov().get());
		double focal = graphics.guiWidth() / (2.0 * Math.tan(fov / 2.0));
		int centerX = graphics.guiWidth() / 2;
		int centerY = graphics.guiHeight() / 2;
		int x = centerX + (int) Math.round(Math.tan(yawDelta) * focal);
		int y = centerY - (int) Math.round(Math.tan(elevation) * focal);
		int width = Math.min(260, lines.stream().mapToInt(client.font::width).max().orElse(0) + 10);
		int height = lines.size() * 10 + 8;
		int left = x - width / 2;
		int top = y - height;
		if (left < 0 || left + width > graphics.guiWidth() || top < 0 || top + height > graphics.guiHeight()) return;
		graphics.fill(left, top, left + width, top + height, 0xD0101720);
		if (stripeProgress > 0) graphics.fill(left, top, left + Math.round(width * stripeProgress), top + 2, 0xFFFF4057);
		else graphics.fill(left, top, left + width, top + 1, color);
		for (int i = 0; i < lines.size(); i++) {
			graphics.drawCenteredString(client.font, lines.get(i), x, top + 5 + i * 10, i == 0 ? color : 0xFFF2F6FA);
		}
	}

	private static void draw(Minecraft client, MeshData built, RenderPipeline pipeline) {
		MeshData.DrawState state = built.drawState();
		VertexFormat format = state.format();
		int size = state.vertexCount() * format.getVertexSize();
		if (instance.vertexBuffer == null || instance.vertexBuffer.size() < size) {
			if (instance.vertexBuffer != null) instance.vertexBuffer.close();
			instance.vertexBuffer = new MappableRingBuffer(() -> "accessiblevision highlights", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
		}
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView mapped = encoder.mapBuffer(instance.vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(built.vertexBuffer(), mapped.data());
		}
		built.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
		GpuBuffer indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(built.indexBuffer());
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "accessiblevision highlights", client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
					client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, instance.vertexBuffer.currentBuffer());
			pass.setIndexBuffer(indices, state.indexType());
			pass.drawIndexed(0, 0, state.indexCount(), 1);
		}
		built.close();
		instance.vertexBuffer.rotate();
	}

	public void close() {
		ALLOCATOR.close();
		if (vertexBuffer != null) {
			vertexBuffer.close();
			vertexBuffer = null;
		}
	}
}


