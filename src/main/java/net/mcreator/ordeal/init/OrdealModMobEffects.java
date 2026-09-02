/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.ordeal.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.registries.Registries;

import net.mcreator.ordeal.procedures.StunnedExpiresProcedure;
import net.mcreator.ordeal.procedures.InCombatEffectExpiresProcedure;
import net.mcreator.ordeal.potion.*;
import net.mcreator.ordeal.OrdealMod;

@EventBusSubscriber
public class OrdealModMobEffects {
	public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(Registries.MOB_EFFECT, OrdealMod.MODID);
	public static final DeferredHolder<MobEffect, MobEffect> CD_1 = REGISTRY.register("cd_1", () -> new Cd1MobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> CD_2 = REGISTRY.register("cd_2", () -> new Cd2MobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> CD_3 = REGISTRY.register("cd_3", () -> new Cd3MobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> CD_4 = REGISTRY.register("cd_4", () -> new Cd4MobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> CD_5 = REGISTRY.register("cd_5", () -> new Cd5MobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> CD_6 = REGISTRY.register("cd_6", () -> new Cd6MobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> CD_7 = REGISTRY.register("cd_7", () -> new Cd7MobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> CD_8 = REGISTRY.register("cd_8", () -> new Cd8MobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> CD_9 = REGISTRY.register("cd_9", () -> new Cd9MobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> CD_10 = REGISTRY.register("cd_10", () -> new Cd10MobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> STUNNED = REGISTRY.register("stunned", () -> new StunnedMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> UP_DASH = REGISTRY.register("up_dash", () -> new UpDashMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> DOWN_DASH = REGISTRY.register("down_dash", () -> new DownDashMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> FORWARD_DASH = REGISTRY.register("forward_dash", () -> new ForwardDashMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> FORWARD_DASH_NO_AIR_TIME = REGISTRY.register("forward_dash_no_air_time", () -> new ForwardDashNoAirTimeMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> SCREEN_SHAKE = REGISTRY.register("screen_shake", () -> new ScreenShakeMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> PHOENIX_SPEAR = REGISTRY.register("phoenix_spear", () -> new PhoenixSpearMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> IN_COMBAT = REGISTRY.register("in_combat", () -> new InCombatMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> MOVEMENT_STUNNED = REGISTRY.register("movement_stunned", () -> new MovementStunnedMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> DESCENDING_METEOR = REGISTRY.register("descending_meteor", () -> new DescendingMeteorMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> TROCHOS_ARMATON_FORWARD = REGISTRY.register("trochos_armaton_forward", () -> new TrochosArmatonForwardMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> BACK_DASH = REGISTRY.register("back_dash", () -> new BackDashMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> TROCHOS_ARMATON_BACK = REGISTRY.register("trochos_armaton_back", () -> new TrochosArmatonBackMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> NEGLET_FALL_DMG = REGISTRY.register("neglet_fall_dmg", () -> new NegletFallDmgMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> BREATHOF_PHOENIX = REGISTRY.register("breathof_phoenix", () -> new BreathofPhoenixMobEffect());
	public static final DeferredHolder<MobEffect, MobEffect> CAMERA_LOCK_POTION = REGISTRY.register("camera_lock_potion", () -> new CameraLockPotionMobEffect());

	@SubscribeEvent
	public static void onEffectRemoved(MobEffectEvent.Remove event) {
		MobEffectInstance effectInstance = event.getEffectInstance();
		if (effectInstance != null) {
			expireEffects(event.getEntity(), effectInstance);
		}
	}

	@SubscribeEvent
	public static void onEffectExpired(MobEffectEvent.Expired event) {
		MobEffectInstance effectInstance = event.getEffectInstance();
		if (effectInstance != null) {
			expireEffects(event.getEntity(), effectInstance);
		}
	}

	private static void expireEffects(Entity entity, MobEffectInstance effectInstance) {
		if (effectInstance.getEffect().is(STUNNED)) {
			StunnedExpiresProcedure.execute(entity);
		} else if (effectInstance.getEffect().is(IN_COMBAT)) {
			InCombatEffectExpiresProcedure.execute(entity);
		} else if (effectInstance.getEffect().is(MOVEMENT_STUNNED)) {
			StunnedExpiresProcedure.execute(entity);
		}
	}
}