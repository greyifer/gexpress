package dev.mapselect.client.render;

import dev.mapselect.entity.TutorialPassengerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

public final class TutorialPassengerEntityRenderer
		extends MobEntityRenderer<TutorialPassengerEntity, PlayerEntityModel<TutorialPassengerEntity>> {
	public TutorialPassengerEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5F);
	}

	@Override
	public Identifier getTexture(TutorialPassengerEntity entity) {
		return DefaultSkinHelper.getSkinTextures(entity.getUuid()).texture();
	}
}
