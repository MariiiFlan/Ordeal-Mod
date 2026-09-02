/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.ordeal.init;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;

@EventBusSubscriber(Dist.CLIENT)
public class OrdealModEntityRenderers {
	@SubscribeEvent
	public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(OrdealModEntities.PHOENIX_FLAME_PROJECTILE.get(), ThrownItemRenderer::new);
		event.registerEntityRenderer(OrdealModEntities.AKONITO_PROJECTILE.get(), ThrownItemRenderer::new);
		event.registerEntityRenderer(OrdealModEntities.BREATHOF_PHOENIX_PROJECTILE.get(), ThrownItemRenderer::new);
	}
}