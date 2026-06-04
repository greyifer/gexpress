package dev.mapselect.client;

import dev.doctor4t.wathe.block.property.OrnamentShape;
import dev.doctor4t.wathe.index.WatheProperties;
import dev.mapselect.block.FusedOrnamentBlockEntity;
import dev.mapselect.block.FusedOrnamentGeometry;
import dev.mapselect.registry.MapSelectBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.FacingBlock;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class FusedOrnamentBlockEntityRenderer implements BlockEntityRenderer<FusedOrnamentBlockEntity> {
	private static final int RENDER_DISTANCE = 64;

	private final BlockRenderManager renderManager;
	private final Random random = Random.create();
	private final Map<Identifier, BlockState> defaultStateCache = new HashMap<>();
	private final Map<DecorationStateKey, BlockState> decorationStateCache = new HashMap<>();
	private final Map<Direction, BlockState> ledgeStateCache = new EnumMap<>(Direction.class);
	private final Map<FusedOrnamentBlockEntity, RenderPlan> renderPlanCache = new WeakHashMap<>();
	private BlockState ledgeDefaultState;

	public FusedOrnamentBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.renderManager = context.getRenderManager();
	}

	@Override
	public void render(FusedOrnamentBlockEntity entity, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider consumers, int light, int overlay) {
		RenderPlan plan = renderPlan(entity);
		BlockRenderView world = entity.getWorld();
		if (world == null) {
			renderStateAsEntity(plan.baseState(), matrices, consumers, light, overlay);
			return;
		}

		BlockPos pos = entity.getPos();
		renderWorldState(plan.baseState(), pos, world, matrices, consumers);
		for (RenderEntry entry : plan.decorations()) {
			renderEntry(entry, pos, world, matrices, consumers);
		}
	}

	private RenderPlan renderPlan(FusedOrnamentBlockEntity entity) {
		RenderPlan cached = renderPlanCache.get(entity);
		if (cached != null && cached.version() == entity.getRenderVersion()) return cached;

		List<RenderEntry> decorations = new ArrayList<>(entity.getDecorations().size() * 2);
		for (Map.Entry<Direction, FusedOrnamentBlockEntity.Decoration> entry : entity.getDecorations().entrySet()) {
			addDecorationEntries(decorations, entry.getKey(), entry.getValue());
		}

		RenderPlan plan = new RenderPlan(entity.getRenderVersion(), entity.getBaseState(), List.copyOf(decorations));
		renderPlanCache.put(entity, plan);
		return plan;
	}

	private void addDecorationEntries(List<RenderEntry> entries, Direction facing,
			FusedOrnamentBlockEntity.Decoration decoration) {
		Identifier ornamentId = decoration.ornamentId();
		OrnamentShape shape = decoration.shape();
		if (MapSelectBlocks.WATHE_GOLD_LEDGE_ID.equals(ornamentId)) {
			addGoldLedgeEntries(entries, facing, shape);
			return;
		}

		BlockState ornamentState = decorationState(ornamentId, facing, shape);
		if (ornamentState.isAir()) return;
		entries.add(new RenderEntry(
			ornamentState,
			facing.getOffsetX(),
			facing.getOffsetY(),
			facing.getOffsetZ()
		));
	}

	private void addGoldLedgeEntries(List<RenderEntry> entries, Direction facing, OrnamentShape shape) {
		if (facing == Direction.UP || facing == Direction.DOWN) {
			if (FusedOrnamentGeometry.hasLedgePart(shape, FusedOrnamentGeometry.LedgePart.TOP)) {
				addGoldLedgeEntry(entries, Direction.NORTH);
			}
			if (FusedOrnamentGeometry.hasLedgePart(shape, FusedOrnamentGeometry.LedgePart.RIGHT)) {
				addGoldLedgeEntry(entries, Direction.EAST);
			}
			if (FusedOrnamentGeometry.hasLedgePart(shape, FusedOrnamentGeometry.LedgePart.BOTTOM)) {
				addGoldLedgeEntry(entries, Direction.SOUTH);
			}
			if (FusedOrnamentGeometry.hasLedgePart(shape, FusedOrnamentGeometry.LedgePart.LEFT)) {
				addGoldLedgeEntry(entries, Direction.WEST);
			}
			return;
		}
		addGoldLedgeEntry(entries, facing);
	}

	private void addGoldLedgeEntry(List<RenderEntry> entries, Direction facing) {
		BlockState ledgeState = ledgeState(facing);
		if (!ledgeState.isAir()) {
			entries.add(new RenderEntry(ledgeState, 0, 0, 0));
		}
	}

	private void renderEntry(RenderEntry entry, BlockPos pos, BlockRenderView world, MatrixStack matrices,
			VertexConsumerProvider consumers) {
		if (entry.offsetX() == 0 && entry.offsetY() == 0 && entry.offsetZ() == 0) {
			renderWorldState(entry.state(), pos, world, matrices, consumers);
			return;
		}

		BlockPos renderPos = pos.add(entry.offsetX(), entry.offsetY(), entry.offsetZ());
		matrices.push();
		matrices.translate(entry.offsetX(), entry.offsetY(), entry.offsetZ());
		renderWorldState(entry.state(), renderPos, world, matrices, consumers);
		matrices.pop();
	}

	@Override
	public int getRenderDistance() {
		return RENDER_DISTANCE;
	}

	@Override
	public boolean rendersOutsideBoundingBox(FusedOrnamentBlockEntity blockEntity) {
		return true;
	}

	private BlockState decorationState(Identifier id, Direction facing, OrnamentShape shape) {
		return decorationStateCache.computeIfAbsent(
			new DecorationStateKey(id, facing, shape),
			key -> {
				BlockState state = defaultState(key.id());
				if (state.isAir()) return state;
				if (state.contains(FacingBlock.FACING)) {
					state = state.with(FacingBlock.FACING, key.facing());
				}
				if (state.contains(WatheProperties.ORNAMENT_SHAPE)) {
					state = state.with(WatheProperties.ORNAMENT_SHAPE, key.shape());
				}
				return state;
			}
		);
	}

	private BlockState defaultState(Identifier id) {
		return defaultStateCache.computeIfAbsent(id, key -> Registries.BLOCK.get(key).getDefaultState());
	}

	private BlockState ledgeState(Direction facing) {
		return ledgeStateCache.computeIfAbsent(facing, key -> {
			BlockState state = ledgeDefaultState();
			return state.isAir() || !state.contains(FacingBlock.FACING)
				? state
				: state.with(FacingBlock.FACING, key);
		});
	}

	private BlockState ledgeDefaultState() {
		if (ledgeDefaultState == null) {
			ledgeDefaultState = Registries.BLOCK.get(MapSelectBlocks.WATHE_GOLD_LEDGE_ID).getDefaultState();
		}
		return ledgeDefaultState;
	}

	private void renderWorldState(BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices,
			VertexConsumerProvider consumers) {
		random.setSeed(pos.asLong());
		this.renderManager.renderBlock(
			state,
			pos,
			world,
			matrices,
			consumers.getBuffer(RenderLayers.getBlockLayer(state)),
			false,
			random
		);
	}

	private void renderStateAsEntity(BlockState state, MatrixStack matrices, VertexConsumerProvider consumers,
			int light, int overlay) {
		this.renderManager.getModelRenderer().render(
			matrices.peek(),
			consumers.getBuffer(RenderLayers.getEntityBlockLayer(state, false)),
			state,
			this.renderManager.getModel(state),
			1.0F,
			1.0F,
			1.0F,
			light,
			overlay
		);
	}

	private record DecorationStateKey(Identifier id, Direction facing, OrnamentShape shape) {}

	private record RenderPlan(long version, BlockState baseState, List<RenderEntry> decorations) {}

	private record RenderEntry(BlockState state, int offsetX, int offsetY, int offsetZ) {}
}
