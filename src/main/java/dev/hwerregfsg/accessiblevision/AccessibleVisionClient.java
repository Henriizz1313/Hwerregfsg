package dev.hwerregfsg.accessiblevision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

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
	private String itemFilter = "";
	private int ticksSinceScan;

	public static AccessibleVisionClient getInstance() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		instance = this;
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
		}
		while (togglePlayers.consumeClick()) {
			playersEnabled = !playersEnabled;
			if (client.player != null) client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Spieleranzeige: " + (playersEnabled ? "An" : "Aus")), true);
		}
		while (toggleWarmFilter.consumeClick()) {
			warmFilterEnabled = !warmFilterEnabled;
			if (client.player != null) client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Warmer Bildschirmfilter: " + (warmFilterEnabled ? "An" : "Aus")), true);
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

	public String getItemFilter() {
		return itemFilter;
	}

	public void setItemFilter(String filter) {
		itemFilter = filter == null ? "" : filter.trim();
	}

	public void toggleOres() {
		 oresEnabled = !oresEnabled;
		 if (!oresEnabled) oreMarkers.clear();
	}

	public void togglePlayers() {
		playersEnabled = !playersEnabled;
	}

	public void toggleWarmFilter() {
		warmFilterEnabled = !warmFilterEnabled;
	}

	public void toggleGroundItems() {
		groundItemsEnabled = !groundItemsEnabled;
	}

	public void toggleMobs() {
		mobsEnabled = !mobsEnabled;
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
		if (state.is(Blocks.ANCIENT_DEBRIS)) return 0xFFFF403A;
		if (state.is(BlockTags.DIAMOND_ORES)) return 0xFF40E8FF;
		if (state.is(BlockTags.EMERALD_ORES)) return 0xFF47FF87;
		if (state.is(BlockTags.GOLD_ORES)) return 0xFFFFD43B;
		if (state.is(BlockTags.IRON_ORES)) return 0xFFFF9654;
		if (state.is(BlockTags.COPPER_ORES)) return 0xFFFF815E;
		if (state.is(BlockTags.LAPIS_ORES)) return 0xFF6294FF;
		if (state.is(BlockTags.REDSTONE_ORES)) return 0xFFFF4D62;
		if (state.is(BlockTags.COAL_ORES)) return 0xFFE4E8F2;
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
		if (playersEnabled) {
			for (Player player : client.level.players()) {
				if (player == client.player || player.isRemoved() || player.distanceToSqr(client.player) > 32 * 32
						|| !client.player.hasLineOfSight(player)) continue;
				AABB box = player.getBoundingBox();
				addBox(matrices.last().pose(), (float) box.minX, (float) box.minY, (float) box.minZ,
						(float) box.maxX, (float) box.maxY, (float) box.maxZ, 0xFFFF3030, 0.07f);
				addOutline(matrices.last().pose(), (float) box.minX, (float) box.minY, (float) box.minZ,
						(float) box.maxX, (float) box.maxY, (float) box.maxZ, 0xFFFF3030, pulse);
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
		BlockHitResult hit = client.level.clip(new ClipContext(
				client.player.getEyePosition(), Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
		return hit.getType() == BlockHitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
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
		if (playersEnabled) {
			for (Player player : nearbyPlayers(client)) drawPlayerLabel(graphics, client, player);
		}
		if (groundItemsEnabled) {
			for (ItemEntity item : client.level.getEntitiesOfClass(ItemEntity.class, client.player.getBoundingBox().inflate(32),
					entity -> !entity.isRemoved() && client.player.hasLineOfSight(entity) && matchesItemFilter(entity.getItem()))) {
				drawEntityLabel(graphics, client, item, item.getItem().getHoverName().getString() + " ×" + item.getItem().getCount(), 0xFFFFE17A);
			}
		}
		if (mobsEnabled) {
			for (Mob mob : client.level.getEntitiesOfClass(Mob.class, client.player.getBoundingBox().inflate(32),
					entity -> !entity.isRemoved() && entity.isAlive() && client.player.hasLineOfSight(entity))) {
				drawEntityLabel(graphics, client, mob, mob.getName().getString() + "  " + Math.round(mob.getHealth()) + "/" + Math.round(mob.getMaxHealth()) + " HP", 0xFF8FFFF0);
			}
		}
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
		lines.add(player.getName().getString() + "  •  " + Math.round(player.distanceTo(client.player)) + " m");
		ItemStack held = player.getMainHandItem();
		lines.add("Hand: " + (held.isEmpty() ? "leer" : held.getHoverName().getString()));
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			ItemStack armor = player.getItemBySlot(slot);
			if (armor.isEmpty()) continue;
			String name = armor.getHoverName().getString();
			String durability = armor.isDamageableItem()
					? " " + Math.max(0, 100 - Math.round(armor.getDamageValue() * 100f / armor.getMaxDamage())) + "%" : "";
			lines.add(name + durability);
		}
		drawLabel(graphics, client, player, lines, 0xFFFF7777);
	}

	private void drawEntityLabel(GuiGraphics graphics, Minecraft client, net.minecraft.world.entity.Entity entity, String text, int color) {
		drawLabel(graphics, client, entity, List.of(text), color);
	}

	private void drawLabel(GuiGraphics graphics, Minecraft client, net.minecraft.world.entity.Entity entity, List<String> lines, int color) {
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
		graphics.fill(left, top, left + width, top + 1, color);
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


