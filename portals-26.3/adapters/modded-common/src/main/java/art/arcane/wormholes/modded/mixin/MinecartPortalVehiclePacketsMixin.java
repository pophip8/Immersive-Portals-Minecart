package art.arcane.wormholes.modded.mixin;

import art.arcane.wormholes.modded.WormholesModRuntime;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MinecartPortalVehiclePacketsMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleMoveVehicle",at=@At("HEAD"),cancellable=true)
    private void kb$holdAuthoritativeVehicle(ServerboundMoveVehiclePacket packet,CallbackInfo callback) {
        var server=player.level().getServer();
        if (!server.isSameThread() || !(player.getRootVehicle() instanceof AbstractMinecart cart)) { return; }
        var runtime=WormholesModRuntime.forServer(server);
        if (runtime!=null && runtime.portals().minecartWaiting(cart)) {
            player.connection.send(ClientboundMoveVehiclePacket.fromEntity(cart));
            callback.cancel();
        }
    }
}
