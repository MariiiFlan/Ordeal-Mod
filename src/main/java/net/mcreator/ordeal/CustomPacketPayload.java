package net.mcreator.ordeal;

/**
 * Bridge. MCreator's regen keeps importing net.mcreator.ordeal.CustomPacketPayload
 * into the generated network classes; this makes that import mean the real thing.
 * Keep this as a code element so regeneration never breaks the build again.
 */
public interface CustomPacketPayload extends net.minecraft.network.protocol.common.custom.CustomPacketPayload {
}
