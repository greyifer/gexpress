package dev.mapselect.item;

import dev.mapselect.MapSelect;
import dev.mapselect.client.GexpressCaseItemRenderer;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class GexpressCaseItem extends Item implements GeoItem {
	public static final Identifier MODEL_ID = Identifier.of(MapSelect.MOD_ID, "harpy_crate");
	private static final String ANIMATION_KEY = "GexpressCaseAnimation";
	private static final RawAnimation STATIC = RawAnimation.begin().thenLoop("animation.harpy_crate.static");
	private static final RawAnimation FALL = RawAnimation.begin()
		.thenPlay("animation.harpy_crate.fall")
		.thenLoop("animation.harpy_crate.static");
	private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("animation.harpy_crate.open");
	private static final RawAnimation OPENED = RawAnimation.begin().thenLoop("animation.harpy_crate.opened");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public GexpressCaseItem(Settings settings) {
		super(settings);
	}

	public static ItemStack stack(String animation) {
		ItemStack stack = new ItemStack(dev.mapselect.registry.MapSelectItems.GEXPRESS_CASE);
		NbtCompound tag = new NbtCompound();
		tag.putString(ANIMATION_KEY, animation == null ? "static" : animation);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
		return stack;
	}

	@Override
	public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		consumer.accept(new GeoRenderProvider() {
			private GeoItemRenderer<GexpressCaseItem> renderer;

			@Override
			public BuiltinModelItemRenderer getGeoItemRenderer() {
				if (renderer == null) {
					renderer = new GexpressCaseItemRenderer(new DefaultedItemGeoModel<GexpressCaseItem>(MODEL_ID)
						.withAltTexture(Identifier.of(MapSelect.MOD_ID, "harpy_case")));
				}
				return renderer;
			}
		});
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "case", 0, state -> {
			ItemStack stack = state.getData(DataTickets.ITEMSTACK);
			String animation = "static";
			NbtComponent customData = stack == null ? null : stack.get(DataComponentTypes.CUSTOM_DATA);
			if (customData != null) {
				animation = customData.copyNbt().getString(ANIMATION_KEY);
			}
			if ("open".equals(animation)) {
				return state.setAndContinue(OPEN);
			}
			if ("opened".equals(animation)) {
				return state.setAndContinue(OPENED);
			}
			if ("fall".equals(animation)) {
				return state.setAndContinue(FALL);
			}
			return state.setAndContinue(STATIC);
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}
