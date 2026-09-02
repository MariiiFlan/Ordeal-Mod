package net.mcreator.ordeal.procedures;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;

import net.mcreator.ordeal.network.PlayPlayerAnimationMessage;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.mcreator.ordeal.init.OrdealModEntities;
import net.mcreator.ordeal.entity.PhoenixFlameProjectileEntity;
import net.mcreator.ordeal.OrdealMod;

public class PhoenixFlames1Procedure {
	public static void execute(LevelAccessor world, Entity entity) {
		if (entity == null)
			return;
		String talent_id = "";
		double levelneeded = 0;
		double cooldown = 0;
		double cooldownTicks = 0;
		double Talent_STR_Req = 0;
		double Chi_Cost = 0;
		double BaseDMG = 0;
		double ExtraDMG = 0;
		double reqStat_Agil = 0;
		double reqStat_Dura = 0;
		double reqStat_Str = 0;
		double talent_Str = 0;
		if (!world.isClientSide()) {
			OrdealMod.queueServerWork(3, () -> {
				if (entity instanceof ServerPlayer || entity instanceof Player) {
					{
						Entity _ent = entity;
						if (!_ent.level().isClientSide() && _ent.getServer() != null) {
							_ent.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, _ent.position(), _ent.getRotationVector(), _ent.level() instanceof ServerLevel ? (ServerLevel) _ent.level() : null, 4,
									_ent.getName().getString(), _ent.getDisplayName(), _ent.level().getServer(), _ent), "ordealanimations play @s shoot1_fire");
						}
					}
					if (entity instanceof Player) {
						if (entity.level().isClientSide()) {
							CompoundTag data = entity.getPersistentData();
							data.putString("PlayerCurrentAnimation", "ordeal:phoenix_flames_shoot_ground");
							data.putBoolean("OverrideCurrentAnimation", true);
							data.putBoolean("FirstPersonAnimation", false);
						} else {
							PacketDistributor.sendToPlayersInDimension((ServerLevel) entity.level(), new PlayPlayerAnimationMessage(entity.getId(), "ordeal:phoenix_flames_shoot_ground", true, false));
						}
					}
				}
				{
					Entity _ent = entity;
					if (!_ent.level().isClientSide() && _ent.getServer() != null) {
						_ent.getServer().getCommands().performPrefixedCommand(new CommandSourceStack(CommandSource.NULL, _ent.position(), _ent.getRotationVector(), _ent.level() instanceof ServerLevel ? (ServerLevel) _ent.level() : null, 4,
								_ent.getName().getString(), _ent.getDisplayName(), _ent.level().getServer(), _ent), "photon fx photon:ilios_phoenixflames_startup entity @s 0 -0.2 0 0 0 0 1 1 1 0 false false xrot");
					}
				}
				{
					Entity _shootFrom = entity;
					Level projectileLevel = _shootFrom.level();
					if (!projectileLevel.isClientSide()) {
						Projectile _entityToSpawn = initArrowProjectile(new PhoenixFlameProjectileEntity(OrdealModEntities.PHOENIX_FLAME_PROJECTILE.get(), 0, 0, 0, projectileLevel, createArrowWeaponItemStack(projectileLevel, 1, (byte) 1)), entity,
								(float) entity.getData(OrdealModVariables.PLAYER_VARIABLES).damage, true, false, false, AbstractArrow.Pickup.DISALLOWED);
						_entityToSpawn.setPos(_shootFrom.getX(), _shootFrom.getEyeY() - 0.1, _shootFrom.getZ());
						_entityToSpawn.shoot(_shootFrom.getLookAngle().x, _shootFrom.getLookAngle().y, _shootFrom.getLookAngle().z, 2, 0);
						projectileLevel.addFreshEntity(_entityToSpawn);
					}
				}
			});
		}
	}

	private static AbstractArrow initArrowProjectile(AbstractArrow entityToSpawn, Entity shooter, float damage, boolean silent, boolean fire, boolean particles, AbstractArrow.Pickup pickup) {
		entityToSpawn.setOwner(shooter);
		entityToSpawn.setBaseDamage(damage);
		if (silent)
			entityToSpawn.setSilent(true);
		if (fire)
			entityToSpawn.igniteForSeconds(100);
		if (particles)
			entityToSpawn.setCritArrow(true);
		entityToSpawn.pickup = pickup;
		return entityToSpawn;
	}

	private static ItemStack createArrowWeaponItemStack(Level level, int knockback, byte piercing) {
		ItemStack weapon = new ItemStack(Items.ARROW);
		if (knockback > 0)
			weapon.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.KNOCKBACK), knockback);
		if (piercing > 0)
			weapon.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PIERCING), piercing);
		return weapon;
	}
}