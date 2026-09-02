package net.mcreator.ordeal.procedures;

import net.minecraft.world.entity.Entity;

public class BreathofPhoenixProjectileHitProcedure {
	public static void execute(Entity entity, Entity immediatesourceentity) {
		if (entity == null || immediatesourceentity == null)
			return;
		IliosProjectileHitsEntProcedure.execute(entity);
		if (!immediatesourceentity.level().isClientSide())
			immediatesourceentity.discard();
	}
}