package net.mcreator.ordeal.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

import net.mcreator.ordeal.init.OrdealModMobEffects;
import net.mcreator.ordeal.OrdealMod;

public class NegletFallDmgOnEffectActiveTickProcedure {
	public static void execute(LevelAccessor world, Entity entity) {
		if (entity == null)
			return;
		entity.fallDistance = 0;
		if (entity.onGround()) {
			OrdealMod.queueServerWork(3, () -> {
				if (entity instanceof LivingEntity _entity)
					_entity.removeEffect(OrdealModMobEffects.NEGLET_FALL_DMG);
			});
		}
	}
}