package net.mcreator.ordeal.network;

import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;

import net.mcreator.ordeal.OrdealMod;
import net.mcreator.ordeal.CustomPacketPayload;

import java.util.function.Supplier;

@EventBusSubscriber
public class OrdealModVariables {
	public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OrdealMod.MODID);
	public static final Supplier<AttachmentType<PlayerVariables>> PLAYER_VARIABLES = ATTACHMENT_TYPES.register("player_variables", () -> AttachmentType.serializable(() -> new PlayerVariables()).build());

	@SubscribeEvent
	public static void init(FMLCommonSetupEvent event) {
		OrdealMod.addNetworkMessage(PlayerVariablesSyncMessage.TYPE, PlayerVariablesSyncMessage.STREAM_CODEC, PlayerVariablesSyncMessage::handleData);
	}

	@SubscribeEvent
	public static void onPlayerLoggedInSyncPlayerVariables(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			for (Entity entityiterator : player.level().players())
				if (entityiterator != player && entityiterator instanceof ServerPlayer playeriterator)
					PacketDistributor.sendToPlayer(player, new PlayerVariablesSyncMessage(playeriterator.getData(PLAYER_VARIABLES), playeriterator.getId()));
			PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), new PlayerVariablesSyncMessage(player.getData(PLAYER_VARIABLES), player.getId()));
		}
	}

	@SubscribeEvent
	public static void onPlayerRespawnedSyncPlayerVariables(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			for (Entity entityiterator : player.level().players())
				if (entityiterator != player && entityiterator instanceof ServerPlayer playeriterator)
					PacketDistributor.sendToPlayer(player, new PlayerVariablesSyncMessage(playeriterator.getData(PLAYER_VARIABLES), playeriterator.getId()));
			PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), new PlayerVariablesSyncMessage(player.getData(PLAYER_VARIABLES), player.getId()));
		}
	}

	@SubscribeEvent
	public static void onPlayerChangedDimensionSyncPlayerVariables(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			for (Entity entityiterator : player.level().players())
				if (entityiterator != player && entityiterator instanceof ServerPlayer playeriterator)
					PacketDistributor.sendToPlayer(player, new PlayerVariablesSyncMessage(playeriterator.getData(PLAYER_VARIABLES), playeriterator.getId()));
			PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), new PlayerVariablesSyncMessage(player.getData(PLAYER_VARIABLES), player.getId()));
		}
	}

	@SubscribeEvent
	public static void onPlayerTickUpdateSyncPlayerVariables(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer player && player.getData(PLAYER_VARIABLES)._syncDirty) {
			PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), new PlayerVariablesSyncMessage(player.getData(PLAYER_VARIABLES), player.getId()));
			player.getData(PLAYER_VARIABLES)._syncDirty = false;
		}
	}

	@SubscribeEvent
	public static void clonePlayer(PlayerEvent.Clone event) {
		PlayerVariables original = event.getOriginal().getData(PLAYER_VARIABLES);
		PlayerVariables clone = new PlayerVariables();
		clone.level = original.level;
		clone.xp = original.xp;
		clone.xpCap = original.xpCap;
		clone.sp = original.sp;
		clone.spLifetime = original.spLifetime;
		clone.spLifetime_Cap = original.spLifetime_Cap;
		clone.talentSP = original.talentSP;
		clone.talentSP_Lifetime = original.talentSP_Lifetime;
		clone.talentSp_Lifetime_Cap = original.talentSp_Lifetime_Cap;
		clone.statStrength = original.statStrength;
		clone.statDurability = original.statDurability;
		clone.statAgility = original.statAgility;
		clone.statHealth = original.statHealth;
		clone.statChi = original.statChi;
		clone.statChiControl = original.statChiControl;
		clone.statPerception = original.statPerception;
		clone.chiLimit = original.chiLimit;
		clone.bloodConsumed = original.bloodConsumed;
		clone.talent1_id = original.talent1_id;
		clone.talent1_strength = original.talent1_strength;
		clone.talent1_source = original.talent1_source;
		clone.talent2_id = original.talent2_id;
		clone.talent2_strength = original.talent2_strength;
		clone.talent2_source = original.talent2_source;
		clone.ownedBasics = original.ownedBasics;
		clone.loadout_1 = original.loadout_1;
		clone.loadout_2 = original.loadout_2;
		clone.loadout_3 = original.loadout_3;
		clone.loadout_4 = original.loadout_4;
		clone.loadout_5 = original.loadout_5;
		clone.loadout_6 = original.loadout_6;
		clone.loadout_7 = original.loadout_7;
		clone.loadout_8 = original.loadout_8;
		clone.loadout_9 = original.loadout_9;
		clone.loadout_10 = original.loadout_10;
		clone.ability_Row = original.ability_Row;
		clone.family = original.family;
		clone.clan = original.clan;
		clone.chiColor = original.chiColor;
		clone.race = original.race;
		clone.impactFrames = original.impactFrames;
		clone.hit_VFX = original.hit_VFX;
		if (!event.isWasDeath()) {
			clone.chi = original.chi;
			clone.chiMax = original.chiMax;
			clone.chiCharging = original.chiCharging;
			clone.ChiConcealed = original.ChiConcealed;
			clone.spawnRandom = original.spawnRandom;
			clone.limiterPct = original.limiterPct;
			clone.ability_select = original.ability_select;
			clone.combatMode = original.combatMode;
			clone.key_pressed = original.key_pressed;
			clone.abilityName = original.abilityName;
			clone.damage = original.damage;
			clone.knockback = original.knockback;
			clone.guard = original.guard;
			clone.guardMax = original.guardMax;
			clone.damageReduction = original.damageReduction;
			clone.attackPower = original.attackPower;
			clone.guardRegenTick = original.guardRegenTick;
			clone.inCombatWith = original.inCombatWith;
			clone.chargePower = original.chargePower;
		}
		event.getEntity().setData(PLAYER_VARIABLES, clone);
	}

	public static class PlayerVariables implements INBTSerializable<CompoundTag> {
		boolean _syncDirty = false;
		public double level = 0;
		public double xp = 0;
		public double xpCap = 0;
		public double sp = 0;
		public double spLifetime = 0;
		public double spLifetime_Cap = 450.0;
		public double talentSP = 0;
		public double talentSP_Lifetime = 0;
		public double talentSp_Lifetime_Cap = 150.0;
		public double statStrength = 0;
		public double statDurability = 0;
		public double statAgility = 0;
		public double statHealth = 0.0;
		public double statChi = 0;
		public double statChiControl = 0;
		public double statPerception = 0;
		public double chi = 0;
		public double chiMax = 0;
		public double chiCharging = 0;
		public double ChiConcealed = 0;
		public double chiLimit = 0;
		public double spawnRandom = 0;
		public double bloodConsumed = 0;
		public String talent1_id = "none";
		public double talent1_strength = 0;
		public String talent1_source = "";
		public String talent2_id = "none";
		public double talent2_strength = 0;
		public String talent2_source = "";
		public double limiterPct = 0;
		public String ownedBasics = "";
		public String ability_select = "";
		public String loadout_1 = "";
		public String loadout_2 = "";
		public String loadout_3 = "";
		public String loadout_4 = "";
		public String loadout_5 = "";
		public String loadout_6 = "";
		public String loadout_7 = "";
		public String loadout_8 = "";
		public String loadout_9 = "";
		public String loadout_10 = "";
		public double ability_Row = 1.0;
		public String family = "";
		public String clan = "";
		public boolean combatMode = false;
		public boolean key_pressed = false;
		public String abilityName = "\"\"";
		public double damage = 0;
		public double knockback = 0;
		public double guard = 0;
		public double guardMax = 0;
		public double damageReduction = 0;
		public double attackPower = 0;
		public double guardRegenTick = 0;
		public String inCombatWith = "none";
		public String chiColor = "none";
		public String race = "human";
		public boolean impactFrames = true;
		public boolean hit_VFX = true;
		public double chargePower = 1.0;

		@Override
		public CompoundTag serializeNBT(HolderLookup.Provider lookupProvider) {
			CompoundTag nbt = new CompoundTag();
			nbt.putDouble("level", level);
			nbt.putDouble("xp", xp);
			nbt.putDouble("xpCap", xpCap);
			nbt.putDouble("sp", sp);
			nbt.putDouble("spLifetime", spLifetime);
			nbt.putDouble("spLifetime_Cap", spLifetime_Cap);
			nbt.putDouble("talentSP", talentSP);
			nbt.putDouble("talentSP_Lifetime", talentSP_Lifetime);
			nbt.putDouble("talentSp_Lifetime_Cap", talentSp_Lifetime_Cap);
			nbt.putDouble("statStrength", statStrength);
			nbt.putDouble("statDurability", statDurability);
			nbt.putDouble("statAgility", statAgility);
			nbt.putDouble("statHealth", statHealth);
			nbt.putDouble("statChi", statChi);
			nbt.putDouble("statChiControl", statChiControl);
			nbt.putDouble("statPerception", statPerception);
			nbt.putDouble("chi", chi);
			nbt.putDouble("chiMax", chiMax);
			nbt.putDouble("chiCharging", chiCharging);
			nbt.putDouble("ChiConcealed", ChiConcealed);
			nbt.putDouble("chiLimit", chiLimit);
			nbt.putDouble("spawnRandom", spawnRandom);
			nbt.putDouble("bloodConsumed", bloodConsumed);
			nbt.putString("talent1_id", talent1_id);
			nbt.putDouble("talent1_strength", talent1_strength);
			nbt.putString("talent1_source", talent1_source);
			nbt.putString("talent2_id", talent2_id);
			nbt.putDouble("talent2_strength", talent2_strength);
			nbt.putString("talent2_source", talent2_source);
			nbt.putDouble("limiterPct", limiterPct);
			nbt.putString("ownedBasics", ownedBasics);
			nbt.putString("ability_select", ability_select);
			nbt.putString("loadout_1", loadout_1);
			nbt.putString("loadout_2", loadout_2);
			nbt.putString("loadout_3", loadout_3);
			nbt.putString("loadout_4", loadout_4);
			nbt.putString("loadout_5", loadout_5);
			nbt.putString("loadout_6", loadout_6);
			nbt.putString("loadout_7", loadout_7);
			nbt.putString("loadout_8", loadout_8);
			nbt.putString("loadout_9", loadout_9);
			nbt.putString("loadout_10", loadout_10);
			nbt.putDouble("ability_Row", ability_Row);
			nbt.putString("family", family);
			nbt.putString("clan", clan);
			nbt.putBoolean("combatMode", combatMode);
			nbt.putBoolean("key_pressed", key_pressed);
			nbt.putString("abilityName", abilityName);
			nbt.putDouble("damage", damage);
			nbt.putDouble("knockback", knockback);
			nbt.putDouble("guard", guard);
			nbt.putDouble("guardMax", guardMax);
			nbt.putDouble("damageReduction", damageReduction);
			nbt.putDouble("attackPower", attackPower);
			nbt.putDouble("guardRegenTick", guardRegenTick);
			nbt.putString("inCombatWith", inCombatWith);
			nbt.putString("chiColor", chiColor);
			nbt.putString("race", race);
			nbt.putBoolean("impactFrames", impactFrames);
			nbt.putBoolean("hit_VFX", hit_VFX);
			nbt.putDouble("chargePower", chargePower);
			return nbt;
		}

		@Override
		public void deserializeNBT(HolderLookup.Provider lookupProvider, CompoundTag nbt) {
			level = nbt.getDouble("level");
			xp = nbt.getDouble("xp");
			xpCap = nbt.getDouble("xpCap");
			sp = nbt.getDouble("sp");
			spLifetime = nbt.getDouble("spLifetime");
			spLifetime_Cap = nbt.getDouble("spLifetime_Cap");
			talentSP = nbt.getDouble("talentSP");
			talentSP_Lifetime = nbt.getDouble("talentSP_Lifetime");
			talentSp_Lifetime_Cap = nbt.getDouble("talentSp_Lifetime_Cap");
			statStrength = nbt.getDouble("statStrength");
			statDurability = nbt.getDouble("statDurability");
			statAgility = nbt.getDouble("statAgility");
			statHealth = nbt.getDouble("statHealth");
			statChi = nbt.getDouble("statChi");
			statChiControl = nbt.getDouble("statChiControl");
			statPerception = nbt.getDouble("statPerception");
			chi = nbt.getDouble("chi");
			chiMax = nbt.getDouble("chiMax");
			chiCharging = nbt.getDouble("chiCharging");
			ChiConcealed = nbt.getDouble("ChiConcealed");
			chiLimit = nbt.getDouble("chiLimit");
			spawnRandom = nbt.getDouble("spawnRandom");
			bloodConsumed = nbt.getDouble("bloodConsumed");
			talent1_id = nbt.getString("talent1_id");
			talent1_strength = nbt.getDouble("talent1_strength");
			talent1_source = nbt.getString("talent1_source");
			talent2_id = nbt.getString("talent2_id");
			talent2_strength = nbt.getDouble("talent2_strength");
			talent2_source = nbt.getString("talent2_source");
			limiterPct = nbt.getDouble("limiterPct");
			ownedBasics = nbt.getString("ownedBasics");
			ability_select = nbt.getString("ability_select");
			loadout_1 = nbt.getString("loadout_1");
			loadout_2 = nbt.getString("loadout_2");
			loadout_3 = nbt.getString("loadout_3");
			loadout_4 = nbt.getString("loadout_4");
			loadout_5 = nbt.getString("loadout_5");
			loadout_6 = nbt.getString("loadout_6");
			loadout_7 = nbt.getString("loadout_7");
			loadout_8 = nbt.getString("loadout_8");
			loadout_9 = nbt.getString("loadout_9");
			loadout_10 = nbt.getString("loadout_10");
			ability_Row = nbt.getDouble("ability_Row");
			family = nbt.getString("family");
			clan = nbt.getString("clan");
			combatMode = nbt.getBoolean("combatMode");
			key_pressed = nbt.getBoolean("key_pressed");
			abilityName = nbt.getString("abilityName");
			damage = nbt.getDouble("damage");
			knockback = nbt.getDouble("knockback");
			guard = nbt.getDouble("guard");
			guardMax = nbt.getDouble("guardMax");
			damageReduction = nbt.getDouble("damageReduction");
			attackPower = nbt.getDouble("attackPower");
			guardRegenTick = nbt.getDouble("guardRegenTick");
			inCombatWith = nbt.getString("inCombatWith");
			chiColor = nbt.getString("chiColor");
			race = nbt.getString("race");
			impactFrames = nbt.getBoolean("impactFrames");
			hit_VFX = nbt.getBoolean("hit_VFX");
			chargePower = nbt.getDouble("chargePower");
		}

		public void markSyncDirty() {
			_syncDirty = true;
		}
	}

	public record PlayerVariablesSyncMessage(PlayerVariables data, int player) implements CustomPacketPayload {
		public static final Type<PlayerVariablesSyncMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(OrdealMod.MODID, "player_variables_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PlayerVariablesSyncMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, PlayerVariablesSyncMessage message) -> {
			buffer.writeInt(message.player());
			buffer.writeNbt(message.data().serializeNBT(buffer.registryAccess()));
		}, (RegistryFriendlyByteBuf buffer) -> {
			PlayerVariablesSyncMessage message = new PlayerVariablesSyncMessage(new PlayerVariables(), buffer.readInt());
			message.data.deserializeNBT(buffer.registryAccess(), buffer.readNbt());
			return message;
		});

		@Override
		public Type<PlayerVariablesSyncMessage> type() {
			return TYPE;
		}

		public static void handleData(final PlayerVariablesSyncMessage message, final IPayloadContext context) {
			if (context.flow() == PacketFlow.CLIENTBOUND && message.data != null) {
				context.enqueueWork(() -> {
					Entity player = context.player().level().getEntity(message.player);
					if (player == null)
						return;
					player.getData(PLAYER_VARIABLES).deserializeNBT(context.player().registryAccess(), message.data.serializeNBT(context.player().registryAccess()));
				}).exceptionally(e -> {
					context.connection().disconnect(Component.literal(e.getMessage()));
					return null;
				});
			}
		}
	}
}