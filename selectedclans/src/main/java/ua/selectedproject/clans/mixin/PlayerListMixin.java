package ua.selectedproject.clans.mixin;

import ua.selectedproject.core.config.CoreConfig;
import ua.selectedproject.core.data.DatabaseManager;
import ua.selectedproject.core.data.Clan;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class PlayerListMixin {

    @Inject(method = "getPlayerListName", at = @At("RETURN"), cancellable = true)
    private void addClanTagToTab(CallbackInfoReturnable<Text> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        DatabaseManager db = DatabaseManager.getInstance();
        if (db == null) return;

        Clan clan = db.getClanByPlayer(player.getUuid());
        if (clan == null) return;

        CoreConfig config = CoreConfig.getInstance();
        String tagText = String.format(config.clanTagFormat, clan.getTag());

        MutableText result = Text.literal(config.clanTagColor + tagText + "§r ");
        Text original = cir.getReturnValue();
        if (original != null) {
            result.append(original);
        } else {
            result.append(player.getName());
        }

        cir.setReturnValue(result);
    }
}
