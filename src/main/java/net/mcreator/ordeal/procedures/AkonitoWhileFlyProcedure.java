package net.mcreator.ordeal.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;

import net.mcreator.ordeal.OrdealMod;

public class AkonitoWhileFlyProcedure {
	public static void execute(LevelAccessor world, Entity immediatesourceentity) {
		if (immediatesourceentity == null)
			return;
		ProjectileFlyProcedure.execute(immediatesourceentity);
		net.mcreator.ordeal.core.OrdealProjectile.tick(immediatesourceentity);
		OrdealMod.queueServerWork(100, () -> {
			if (!immediatesourceentity.level().isClientSide())
				immediatesourceentity.discard();
		});
	}
}