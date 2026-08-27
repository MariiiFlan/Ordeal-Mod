/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.ordeal.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import net.mcreator.ordeal.OrdealMod;

public class OrdealModSounds {
	public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister.create(Registries.SOUND_EVENT, OrdealMod.MODID);
	public static final DeferredHolder<SoundEvent, SoundEvent> GAURD_BREAK = REGISTRY.register("gaurd_break", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("ordeal", "gaurd_break")));
}