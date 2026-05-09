package ua.selectedproject.police.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.selectedproject.police.network.BindingSyncPayload;

public class SelectedPoliceClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("SelectedPoliceClient");

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BindingSyncPayload.PACKET_ID,
                (payload, ctx) -> {
                    // Run on render thread — cache reads/writes happen there too.
                    ctx.client().execute(() -> {
                        if (payload.state() == BindingSyncPayload.State.NONE) {
                            BindingClientCache.remove(payload.player());
                        } else {
                            BindingClientCache.set(payload.player(), payload.state());
                        }
                    });
                });

        LOGGER.info("SelectedPolice client initialized");
    }
}