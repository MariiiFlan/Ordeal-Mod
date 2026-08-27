package net.mcreator.ordeal.procedures;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;

import net.mcreator.ordeal.network.OrdealModVariables;
import net.mcreator.ordeal.init.OrdealModMobEffects;

import java.util.Comparator;

public class PhoenixSpearOnTickProcedure {
	public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
		if (entity == null)
			return;
		if (!world.isClientSide()) {
			if (world instanceof Level _level) {
				if (!_level.isClientSide()) {
					_level.playSound(null, BlockPos.containing(x, y, z), BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("item.firecharge.use")), SoundSource.PLAYERS, 1, (float) 0.5);
				} else {
					_level.playLocalSound(x, y, z, BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("item.firecharge.use")), SoundSource.PLAYERS, 1, (float) 0.5, false);
				}
			}
			{
				final Vec3 _center = new Vec3((entity.getX() + 1 * entity.getLookAngle().x), (entity.getY() + 1.6 + 1 * entity.getLookAngle().x), (entity.getZ() + 1 * entity.getLookAngle().z));
				for (Entity entityiterator : world.getEntitiesOfClass(Entity.class, new AABB(_center, _center).inflate(6 / 2d), e -> true).stream().sorted(Comparator.comparingDouble(_entcnd -> _entcnd.distanceToSqr(_center))).toList()) {
					if (!(entityiterator == entity)) {
						{
							OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
							_vars.knockback = (entity.getData(OrdealModVariables.PLAYER_VARIABLES).statStrength - entityiterator.getData(OrdealModVariables.PLAYER_VARIABLES).statDurability)
									* entityiterator.getData(OrdealModVariables.PLAYER_VARIABLES).chargePower;
							_vars.knockback = entity.getData(OrdealModVariables.PLAYER_VARIABLES).knockback * 0.1;
							_vars.markSyncDirty();
						}
						if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).knockback <= 0) {
							{
								OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
								_vars.knockback = 0.2;
								_vars.markSyncDirty();
							}
						}
						if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).knockback >= 2 * entity.getData(OrdealModVariables.PLAYER_VARIABLES).chargePower) {
							{
								OrdealModVariables.PlayerVariables _vars = entity.getData(OrdealModVariables.PLAYER_VARIABLES);
								_vars.knockback = 2 * entity.getData(OrdealModVariables.PLAYER_VARIABLES).chargePower;
								_vars.markSyncDirty();
							}
						}
						if (entity.getData(OrdealModVariables.PLAYER_VARIABLES).knockback >= 2) {
							if (entityiterator instanceof LivingEntity _entity && !_entity.level().isClientSide())
								_entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 1, false, false));
						}
						entityiterator.setDeltaMovement(new Vec3((entity.getLookAngle().x * entity.getData(OrdealModVariables.PLAYER_VARIABLES).knockback), (entity.getDeltaMovement().y()),
								(entity.getLookAngle().z * entity.getData(OrdealModVariables.PLAYER_VARIABLES).knockback)));
						entityiterator.hurt(new DamageSource(world.holderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("ordeal:talent"))), entity), (float) entity.getData(OrdealModVariables.PLAYER_VARIABLES).damage);
						if (entity instanceof LivingEntity _entity)
							_entity.removeEffect(OrdealModMobEffects.PHOENIX_SPEAR);
					}
					{
						Entity _ent = entity;
						if (!_ent.level().isClientSide() && _ent.getServer() != null) {
							_ent.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, _ent.position(), _ent.getRotationVector(), _ent.level() instanceof ServerLevel ? (ServerLevel) _ent.level() : null, 4,
									_ent.getName().getString(), _ent.getDisplayName(), _ent.level().getServer(), _ent), "photon fx photon:ilios_phonixspear entity Dev 0 -0.2 0 0 0 0 1 1 1 0 false true xrot");
						}
					}
					if (entityiterator instanceof LivingEntity _entity && !_entity.level().isClientSide())
						_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.SCREEN_SHAKE, 3, 1, false, false));
					if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
						_entity.addEffect(new MobEffectInstance(OrdealModMobEffects.FORWARD_DASH, 2, 2, false, false));
				}
			}
		}
	}
}