package art.arcane.wormholes.modded.mixin;

import art.arcane.wormholes.modded.WormholesModRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecart.class)
public abstract class MinecartPortalWaitingMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void kb$waitForPortalTrack(CallbackInfo callback) {
        AbstractMinecart cart = (AbstractMinecart) (Object) this;
        if (cart.level() instanceof ServerLevel level) {
            WormholesModRuntime runtime = WormholesModRuntime.forServer(level.getServer());
            if (runtime != null && runtime.portals().minecartWaiting(cart)) { callback.cancel(); }
        }
    }
}
