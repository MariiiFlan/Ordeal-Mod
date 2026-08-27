package net.mcreator.ordeal.procedures;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;

import net.mcreator.ordeal.init.OrdealModMobEffects;

public class DashEffectTicksProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		if (entity instanceof LivingEntity _livEnt0 && _livEnt0.hasEffect(OrdealModMobEffects.UP_DASH)) {
			entity.setDeltaMovement(new Vec3((entity.getDeltaMovement().x()), (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.UP_DASH) ? _livEnt.getEffect(OrdealModMobEffects.UP_DASH).getAmplifier() : 0),
					(entity.getDeltaMovement().z())));
		}
		if (entity instanceof LivingEntity _livEnt5 && _livEnt5.hasEffect(OrdealModMobEffects.DOWN_DASH)) {
			entity.setDeltaMovement(new Vec3((entity.getDeltaMovement().x()), (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.DOWN_DASH) ? _livEnt.getEffect(OrdealModMobEffects.DOWN_DASH).getAmplifier() : 0),
					(entity.getDeltaMovement().z())));
			if (entity.onGround()) {
				if (entity instanceof LivingEntity _entity)
					_entity.removeEffect(OrdealModMobEffects.DOWN_DASH);
			}
		}
		if (entity instanceof LivingEntity _livEnt12 && _livEnt12.hasEffect(OrdealModMobEffects.FORWARD_DASH)) {
			entity.setDeltaMovement(new Vec3((entity.getLookAngle().x * (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.FORWARD_DASH) ? _livEnt.getEffect(OrdealModMobEffects.FORWARD_DASH).getAmplifier() : 0)),
					(entity.getLookAngle().y * (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.FORWARD_DASH) ? _livEnt.getEffect(OrdealModMobEffects.FORWARD_DASH).getAmplifier() : 0)),
					(entity.getLookAngle().z * (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.FORWARD_DASH) ? _livEnt.getEffect(OrdealModMobEffects.FORWARD_DASH).getAmplifier() : 0))));
		}
		if (entity instanceof LivingEntity _livEnt20 && _livEnt20.hasEffect(OrdealModMobEffects.FORWARD_DASH_NO_AIR_TIME)) {
			entity.setDeltaMovement(new Vec3(
					(entity.getLookAngle().x * (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.FORWARD_DASH_NO_AIR_TIME) ? _livEnt.getEffect(OrdealModMobEffects.FORWARD_DASH_NO_AIR_TIME).getAmplifier() : 0)),
					(entity.getDeltaMovement().y()),
					(entity.getLookAngle().z * (entity instanceof LivingEntity _livEnt && _livEnt.hasEffect(OrdealModMobEffects.FORWARD_DASH_NO_AIR_TIME) ? _livEnt.getEffect(OrdealModMobEffects.FORWARD_DASH_NO_AIR_TIME).getAmplifier() : 0))));
		}
		if (world instanceof ServerLevel _level)
			_level.sendParticles(ParticleTypes.CLOUD, x, y, z, 5, 1, 1, 1, 1);
	}
}