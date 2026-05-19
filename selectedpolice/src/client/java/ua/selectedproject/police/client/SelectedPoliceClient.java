package ua.selectedproject.police.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.selectedproject.police.network.BindingSyncPayload;

import java.util.UUID;

public class SelectedPoliceClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("SelectedPoliceClient");

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BindingSyncPayload.PACKET_ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (payload.state() == BindingSyncPayload.State.NONE) {
                        BindingClientCache.remove(payload.player());
                    } else {
                        UUID holder = payload.holder();
                        BindingClientCache.set(payload.player(), payload.state(),
                                BindingSyncPayload.NO_HOLDER.equals(holder) ? null : holder);
                    }
                }));

        LOGGER.info("SelectedPolice client initialized");
    }
}