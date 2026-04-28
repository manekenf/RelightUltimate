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

    // Cached clan data received from server
    public static ClanClientData cachedClanData = null;
    public static List<MemberEntry> cachedMembers = new ArrayList<>();

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
                    // Don't open screen yet — wait for member data
                }
        );

        ClientPlayNetworking.registerGlobalReceiver(
                NetworkHandler.ClanMembersSyncPayload.ID,
                (payload, context) -> {
                    // Parse members: "uuid|name;uuid|name;..."
                    cachedMembers.clear();
                    if (!payload.membersData().isEmpty()) {
                        String[] entries = payload.membersData().split(";");
                        for (String entry : entries) {
                            String[] parts = entry.split("\\|", 2);
                            if (parts.length == 2) {
                                cachedMembers.add(new MemberEntry(parts[0], parts[1]));
                            }
                        }
                    }

                    // Now open the management screen
                    if (cachedClanData != null) {
                        context.client().execute(() -> {
                            MinecraftClient.getInstance().setScreen(
                                    new ClanManagementScreen(cachedClanData, cachedMembers)
                            );
                        });
                    }
                }
        );

        LOGGER.info("SelectedClans client initialized!");
    }
}
