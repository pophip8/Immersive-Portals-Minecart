package art.arcane.wormholes.modded;

import art.arcane.optics.frame.Frame;
import art.arcane.wormholes.modded.mixin.ProjectionEntityMapAccess;
import art.arcane.wormholes.modded.mixin.RemoteTrackedEntityAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/** Safe joins between ordinary, level rails at the two ends of a vertical portal. */
public final class MinecartPortalRails {
    static boolean removeLegacyRailSkin(MinecraftPortal portal) {
        String block = portal.getSurfaceSkin().split("\\[", 2)[0];
        if (!java.util.Set.of("minecraft:rail", "minecraft:powered_rail", "minecraft:detector_rail",
                "minecraft:activator_rail").contains(block)) { return false; }
        portal.setSurfaceSkin("");
        return true;
    }
    private MinecartPortalRails() { }

    static boolean alignedRail(BlockState state, boolean xAxis) {
        return state.getBlock() instanceof BaseRailBlock rail
            && state.getValue(rail.getShapeProperty()) == (xAxis ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH);
    }

    static Landing plan(AbstractMinecart cart, ServerLevel level, Frame frame, Vec3 position, Vec3 velocity) {
        if (frame.getNormal().y() != 0 || frame.getUp().y() != 1 || !Double.isFinite(velocity.lengthSqr())) { return null; }
        BlockPos block = BlockPos.containing(position);
        for (BlockPos rail : new BlockPos[] {block, block.below()}) {
            BlockState state = level.getBlockState(rail);
            if (!(state.getBlock() instanceof BaseRailBlock base)) { continue; }
            boolean xAxis = frame.getNormal().x() != 0;
            if (!alignedRail(state,xAxis)) { continue; }
            double height = cart.getBehavior() instanceof NewMinecartBehavior ? NewMinecartBehavior.ON_RAIL_Y_OFFSET : 0.0625;
            Vec3 aligned = new Vec3(xAxis ? position.x : rail.getX() + 0.5, rail.getY() + height,
                xAxis ? rail.getZ() + 0.5 : position.z);
            double speed = Math.hypot(velocity.x, velocity.z);
            double direction = xAxis ? velocity.x : velocity.z;
            if (speed > 1.0e-8 && Math.abs(direction) < 1.0e-8) { return null; }
            Vec3 motion = xAxis ? new Vec3(Math.copySign(speed, direction), 0, 0)
                : new Vec3(0, 0, Math.copySign(speed, direction));
            if (!level.noCollision(cart, cart.getBoundingBox().move(aligned.subtract(cart.position())))) { return null; }
            for (var passenger : cart.getSelfAndPassengers().toList()) {
                if (passenger != cart) {
                    var box=passenger.getBoundingBox().move(aligned.subtract(cart.position()));
                    // Vanilla seats a player's feet below rail height. That lower volume is
                    // intentionally tolerated while riding; validate the body above the wagon.
                    double bottom=Math.max(box.minY,aligned.y+cart.getBbHeight());
                    if (bottom < box.maxY && !level.noCollision(passenger,new net.minecraft.world.phys.AABB(
                        box.minX,bottom,box.minZ,box.maxX,box.maxY,box.maxZ))) {return null;}
                }
            }
            return new Landing(aligned, motion);
        }
        return null;
    }

    static void apply(AbstractMinecart cart, Landing landing) {
        cart.setPos(landing.position());
        cart.setDeltaMovement(landing.velocity());
        cart.setOnRails(true);
        cart.getInterpolation().cancel();
        if (cart.getBehavior() instanceof NewMinecartBehavior behavior) {
            behavior.lerpSteps.clear();
            behavior.currentLerpSteps.clear();
            behavior.currentLerpStepsTotalWeight = 0;
            behavior.setOldLerpValues();
        }
    }

    static void synchronizeRiders(WormholesModRuntime runtime, AbstractMinecart cart) {
        // A remote portal viewer may already be tracking the target dimension. Its respawn
        // clears the client entity map, so normal tracker membership cannot prove it has the cart.
        runtime.schedule(() -> {
            if (!cart.isAlive() || !(cart.level() instanceof ServerLevel level)) { return; }
            var entities = ((ProjectionEntityMapAccess)level.getChunkSource().chunkMap).wormholesEntityMap();
            for (var rider : cart.getSelfAndPassengers().toList()) {
                if (!(rider instanceof ServerPlayer player) || player.level()!=level || player.getRootVehicle()!=cart) { continue; }
                for (var member : cart.getSelfAndPassengers().toList()) {
                    if (member!=player && entities.get(member.getId()) instanceof RemoteTrackedEntityAccess tracker) {
                        tracker.wormholesServerEntity().sendPairingData(player,player.connection::send);
                    }
                }
                for (var member : cart.getSelfAndPassengers().toList()) {
                    if (!member.getPassengers().isEmpty()) {player.connection.send(new ClientboundSetPassengersPacket(member));}
                }
            }
        },2);
    }

    record Landing(Vec3 position, Vec3 velocity) { }
}
