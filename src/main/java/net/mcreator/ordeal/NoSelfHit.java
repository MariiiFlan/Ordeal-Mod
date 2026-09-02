package net.mcreator.ordeal;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

@EventBusSubscriber(modid = "ordeal")
public final class NoSelfHit {

	public static boolean ENABLED = OrdealTuning.i("combat.no_self_hit", 1) != 0;

	private NoSelfHit() {}

	@SubscribeEvent
	public static void onImpact(ProjectileImpactEvent event) {
		if (!ENABLED) return;
		if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
		Projectile proj = event.getProjectile();
		var key = BuiltInRegistries.ENTITY_TYPE.getKey(proj.getType());
		if (key == null || !key.getNamespace().equals("ordeal")) return;
		Entity owner = proj.getOwner();
		if (owner == null) return;
		Entity victim = hit.getEntity();
		if (victim == owner || victim.isPassengerOfSameVehicle(owner)) event.setCanceled(true);
	}
}
