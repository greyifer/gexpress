package dev.mapselect.client.render;

import dev.mapselect.entity.TwinBodyEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

import java.util.UUID;

public final class TwinBodyEntityRenderer
		extends MobEntityRenderer<TwinBodyEntity, PlayerEntityModel<TwinBodyEntity>> {
	public TwinBodyEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5F);
		addFeature(new ArmorFeatureRenderer<>(this,
			new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
			new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
			context.getModelManager()));
		addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
	}

	@Override
	public Identifier getTexture(TwinBodyEntity entity) {
		UUID ownerId = entity.ownerId().orElse(entity.getUuid());
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayNetworkHandler network = client == null ? null : client.getNetworkHandler();
		if (network != null) {
			PlayerListEntry entry = network.getPlayerListEntry(ownerId);
			if (entry != null) return entry.getSkinTextures().texture();
		}
		return DefaultSkinHelper.getSkinTextures(ownerId).texture();
	}
}
