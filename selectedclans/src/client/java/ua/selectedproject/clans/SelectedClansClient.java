package ua.selectedproject.clans;

import ua.selectedproject.clans.gui.ClanCreationScreen;
import ua.selectedproject.clans.gui.ClanManagementScreen;
import ua.selectedproject.clans.network.NetworkHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class SelectedClansClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("SelectedClans/Client");

    // Cached clan data received from server. Mutated on the network thread
    // (in registerGlobalReceiver lambdas) and read on the client thread when
    // opening the screen — `volatile` guarantees publication.
    public static volatile ClanClientData cachedClanData = null;
    public static volatile List<MemberEntry> cachedMembers = new ArrayList<>();

    public record ClanClientData(int clanId, String name, String tag, String leaderUuid,
                                  long createdAt, int memberCount, int rank) {}
    public record MemberEntry(String uuid, String name) {}

    @Override
    public void onInitializeClient() {
        LOGGER.info("SelectedClans client initializing...");

        // Register S2C packet receivers
        ClientPlayNetworking.registerGlobalReceiver(
                NetworkHandler.OpenClanCreateScreenPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        MinecraftClient.getInstance().setScreen(new ClanCreationScreen());
                    });
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                NetworkHandler.ClanDataSyncPayload.ID,
                (payload, context) -> {
                    cachedClanData = new ClanClientData(
                            payload.clanId(), payload.name(), payload.tag(),
                            payload.leaderUuid(), payload.createdAt(),
                            payload.memberCount(), payload.rank()
                    );
                    // If the members payload arrived first (race), open the screen now.
                    tryOpenScreen(context.client());
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                NetworkHandler.ClanMembersSyncPayload.ID,
                (payload, context) -> {
                    List<MemberEntry> parsed = new ArrayList<>(payload.members().size());
                    for (var m : payload.members()) {
                        parsed.add(new MemberEntry(m.uuid(), m.name()));
                    }
                    cachedMembers = parsed;
                    tryOpenScreen(context.client());
                }
        );

        LOGGER.info("SelectedClans client initialized!");
    }

    /**
     * Open / refresh the management screen once both the clan-data and members
     * payloads have arrived. Either may land first; the second arrival is what
     * actually opens the screen.
     */
    private static void tryOpenScreen(MinecraftClient client) {
        ClanClientData data = cachedClanData;
        List<MemberEntry> members = cachedMembers;
        if (data == null) return;
        List<MemberEntry> snapshot = List.copyOf(members);
        client.execute(() -> client.setScreen(new ClanManagementScreen(data, snapshot)));
    }
}
