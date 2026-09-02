/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.ordeal.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.Registries;

import net.mcreator.ordeal.entity.PhoenixFlameProjectileEntity;
import net.mcreator.ordeal.entity.BreathofPhoenixProjectileEntity;
import net.mcreator.ordeal.entity.AkonitoProjectileEntity;
import net.mcreator.ordeal.OrdealMod;

public class OrdealModEntities {
	public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(Registries.ENTITY_TYPE, OrdealMod.MODID);
	public static final DeferredHolder<EntityType<?>, EntityType<PhoenixFlameProjectileEntity>> PHOENIX_FLAME_PROJECTILE = register("phoenix_flame_projectile",
			EntityType.Builder.<PhoenixFlameProjectileEntity>of(PhoenixFlameProjectileEntity::new, MobCategory.MISC).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(1).sized(0.5f, 0.5f));
	public static final DeferredHolder<EntityType<?>, EntityType<AkonitoProjectileEntity>> AKONITO_PROJECTILE = register("akonito_projectile",
			EntityType.Builder.<AkonitoProjectileEntity>of(AkonitoProjectileEntity::new, MobCategory.MISC).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(1).sized(0.5f, 0.5f));
	public static final DeferredHolder<EntityType<?>, EntityType<BreathofPhoenixProjectileEntity>> BREATHOF_PHOENIX_PROJECTILE = register("breathof_phoenix_projectile",
			EntityType.Builder.<BreathofPhoenixProjectileEntity>of(BreathofPhoenixProjectileEntity::new, MobCategory.MISC).setShouldReceiveVelocityUpdates(true).setTrackingRange(64).setUpdateInterval(1).sized(0.5f, 0.5f));

	// Start of user code block custom entities
	// End of user code block custom entities
	private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> register(String registryname, EntityType.Builder<T> entityTypeBuilder) {
		return REGISTRY.register(registryname, () -> (EntityType<T>) entityTypeBuilder.build(registryname));
	}
}