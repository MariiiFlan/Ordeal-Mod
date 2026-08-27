package net.mcreator.ordeal.core;

import net.mcreator.ordeal.OrdealMod;
import net.mcreator.ordeal.network.OrdealModVariables;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.mcreator.ordeal.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Terminal button actions. Server validates everything; the client only asks. */
@EventBusSubscriber
public record OrdealActionMessage(String action, String arg, int value) implements CustomPacketPayload {

	public static final Type<OrdealActionMessage> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(OrdealMod.MODID, "terminal_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OrdealActionMessage> STREAM_CODEC =
			StreamCodec.of((RegistryFriendlyByteBuf buf, OrdealActionMessage msg) -> {
				buf.writeUtf(msg.action, 32);
				buf.writeUtf(msg.arg, 64);
				buf.writeInt(msg.value);
			}, (RegistryFriendlyByteBuf buf) ->
					new OrdealActionMessage(buf.readUtf(32), buf.readUtf(64), buf.readInt()));

	@Override
	public Type<OrdealActionMessage> type() {
		return TYPE;
	}

	public static void handleData(final OrdealActionMessage message, final IPayloadContext context) {
		if (context.flow() != PacketFlow.SERVERBOUND) return;
		context.enqueueWork(() -> apply(context.player(), message)).exceptionally(e -> {
			context.connection().disconnect(Component.literal(e.getMessage()));
			return null;
		});
	}

	private static void apply(Player p, OrdealActionMessage m) {
		if (p == null) return;
		OrdealModVariables.PlayerVariables v = p.getData(OrdealModVariables.PLAYER_VARIABLES);

		switch (m.action) {
			case "spend": {
				double cur = readStat(v, m.arg);
				if (v.sp <= 0 || cur >= 100 || cur >= v.level) return;
				v.sp -= 1;
				v.spLifetime += 1;
				writeStat(v, m.arg, cur + 1);
				break;
			}
			case "talentstr": {
				int slot = m.value;
				if (slot != 1 && slot != 2) return;
				String id = slot == 1 ? v.talent1_id : v.talent2_id;
				if (id == null || id.isEmpty() || id.equals("none")) return;

				double cur = slot == 1 ? v.talent1_strength : v.talent2_strength;
				if (v.talentSP <= 0 || cur >= 150) return;
				if (v.talent1_strength + v.talent2_strength >= v.chiLimit) return;

				v.talentSP -= 1;
				v.talentSP_Lifetime += 1;
				if (slot == 1) v.talent1_strength = cur + 1; else v.talent2_strength = cur + 1;
				break;
			}
			case "conceal": {
				int pct = Math.max(0, Math.min(100, m.value));
				v.ChiConcealed = pct / 100.0;
				break;
			}
			case "select":
				v.ability_select = m.arg;
				break;
			case "bind": {
				if (m.value < 1 || m.value > 10) return;
				writeSlot(v, m.value, m.arg);
				v.ability_select = "";
				break;
			}
			case "dash": {
				if (p instanceof net.minecraft.server.level.ServerPlayer sp)
					net.mcreator.ordeal.OrdealDash.execute(sp, m.arg);
				return;
			}
			case "heavy": {
				if (p instanceof net.minecraft.server.level.ServerPlayer sp)
					net.mcreator.ordeal.OrdealHeavy.execute(sp);
				return;
			}
			case "anim": {
				// animator "Test" button: play the named clip on yourself for everyone
				net.mcreator.ordeal.OrdealAnim.play(p, m.arg, Math.max(1, m.value));
				return;
			}
			case "animstop": {
				net.mcreator.ordeal.OrdealAnim.stop(p);
				return;
			}
			default: return;
		}
		v.markSyncDirty();
	}

	private static double readStat(OrdealModVariables.PlayerVariables v, String s) {
		switch (s) {
			case "strength":   return v.statStrength;
			case "durability": return v.statDurability;
			case "agility":    return v.statAgility;
			case "health":     return v.statHealth;
			case "chi":        return v.statChi;
			case "chicontrol": return v.statChiControl;
			case "perception": return v.statPerception;
			default:           return Double.MAX_VALUE;
		}
	}

	private static void writeStat(OrdealModVariables.PlayerVariables v, String s, double val) {
		switch (s) {
			case "strength":   v.statStrength = val; break;
			case "durability": v.statDurability = val; break;
			case "agility":    v.statAgility = val; break;
			case "health":     v.statHealth = val; break;
			case "chi":        v.statChi = val; break;
			case "chicontrol": v.statChiControl = val; break;
			case "perception": v.statPerception = val; break;
		}
	}

	private static void writeSlot(OrdealModVariables.PlayerVariables v, int slot, String val) {
		switch (slot) {
			case 1:  v.loadout_1 = val; break;
			case 2:  v.loadout_2 = val; break;
			case 3:  v.loadout_3 = val; break;
			case 4:  v.loadout_4 = val; break;
			case 5:  v.loadout_5 = val; break;
			case 6:  v.loadout_6 = val; break;
			case 7:  v.loadout_7 = val; break;
			case 8:  v.loadout_8 = val; break;
			case 9:  v.loadout_9 = val; break;
			case 10: v.loadout_10 = val; break;
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		OrdealMod.addNetworkMessage(TYPE, STREAM_CODEC, OrdealActionMessage::handleData);
	}
}