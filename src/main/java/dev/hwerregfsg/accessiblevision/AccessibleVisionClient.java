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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath("accessiblevision", "pipeline/highlight"))
					.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
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
		if (client.level == null || client.player == null || (!oresEnabled && !playersEnabled)) return;
		PoseStack matrices = context.matrices();
		Vec3 camera = context.worldState().cameraRenderState.pos;
		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		buffer = new BufferBuilder(ALLOCATOR, THROUGH_WALLS.getVertexFormatMode(), THROUGH_WALLS.getVertexFormat());
		int markerCount = 0;
		if (oresEnabled) {
			float pulse = 0.72f + 0.20f * (float) ((Math.sin(System.nanoTime() / 420_000_000.0) + 1.0) * 0.5);
			for (Map.Entry<BlockPos, Integer> entry : oreMarkers.entrySet()) {
				BlockPos pos = entry.getKey();
				float inset = 0.08f;
				addBox(matrices.last().pose(), pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
						pos.getX() + 1f - inset, pos.getY() + 1f - inset, pos.getZ() + 1f - inset, entry.getValue(), 0.10f);
				addOutline(matrices.last().pose(), pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
						pos.getX() + 1f - inset, pos.getY() + 1f - inset, pos.getZ() + 1f - inset, entry.getValue(), pulse);
				markerCount++;
			}
		}
		if (playersEnabled) {
			float pulse = 0.72f + 0.20f * (float) ((Math.sin(System.nanoTime() / 420_000_000.0) + 1.0) * 0.5);
			for (Player player : client.level.players()) {
				if (player == client.player || player.isRemoved() || player.distanceToSqr(client.player) > 32 * 32) continue;
				AABB box = player.getBoundingBox();
				addBox(matrices.last().pose(), (float) box.minX, (float) box.minY, (float) box.minZ,
						(float) box.maxX, (float) box.maxY, (float) box.maxZ, 0xFFFF3030, 0.07f);
				addOutline(matrices.last().pose(), (float) box.minX, (float) box.minY, (float) box.minZ,
						(float) box.maxX, (float) box.maxY, (float) box.maxZ, 0xFFFF3030, pulse);
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
			draw(client, built, THROUGH_WALLS);
		} finally {
			buffer = null;
		}
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
		int y = 8;
		if (warmFilterEnabled) graphics.fill(0, 0, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(), 0x12FFB35C);
		graphics.fill(6, 6, 245, playersEnabled ? 24 + Math.min(4, nearbyPlayers(client).size()) * 28 : 25, 0xA0201717);
		graphics.drawString(client.font, "ACCESSIBLE VISION  |  O Erze  P Spieler  V Warm", 12, y, 0xFFFFD89A, true);
		y += 14;
		if (playersEnabled) {
			List<Player> nearby = nearbyPlayers(client);
			for (int i = 0; i < Math.min(4, nearby.size()); i++) {
				Player player = nearby.get(i);
				String hand = player.getMainHandItem().isEmpty() ? "leere Hand" : player.getMainHandItem().getHoverName().getString();
				String line = player.getName().getString() + "  " + Math.round(player.distanceTo(client.player)) + "m  |  " + hand;
				graphics.drawString(client.font, line, 12, y, 0xFFFF6A6A, true);
				y += 10;
				String armor = armorSummary(player);
				if (!armor.isEmpty()) {
					graphics.drawString(client.font, armor, 18, y, 0xFFFFC0A0, true);
					y += 10;
				}
			}
		}
	}

	private List<Player> nearbyPlayers(Minecraft client) {
		List<Player> result = new ArrayList<>();
		for (Player player : client.level.players()) {
			if (player != client.player && !player.isRemoved() && player.distanceToSqr(client.player) <= 32 * 32) result.add(player);
		}
		result.sort(Comparator.comparingDouble(player -> player.distanceToSqr(client.player)));
		return result;
	}

	private String armorSummary(Player player) {
		StringBuilder result = new StringBuilder("Rüstung:");
		int count = 0;
		for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty() || !stack.isDamageableItem()) continue;
			if (count == 4) break;
			int remaining = Math.max(0, 100 - Math.round(stack.getDamageValue() * 100f / stack.getMaxDamage()));
			result.append(' ').append(remaining).append('%');
			count++;
		}
		return count == 0 ? "" : result.toString();
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


