package art.arcane.wormholes.modded;

import art.arcane.optics.frame.Frame;
import art.arcane.optics.math.Face;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class MinecartPortalRailsTest extends MinecraftTestBase {
    private AbstractMinecart cart;
    private ServerLevel level;
    @Before public void setup() {
        cart = mock(AbstractMinecart.class);
        level = mock(ServerLevel.class);
        when(cart.position()).thenReturn(new Vec3(0.5, 70.0625, 20.5));
        when(cart.getBoundingBox()).thenReturn(new AABB(0, 70.0625, 20, 1, 70.7625, 21));
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.noCollision(eq(cart), any())).thenReturn(true);
    }
    @Test public void rotatedTrackAlignsCartAndPreservesHorizontalSpeed() {
        when(level.getBlockState(new BlockPos(80, 70, 0))).thenReturn(Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
        var landing = MinecartPortalRails.plan(cart, level, Frame.canonical(Face.W), new Vec3(80.3,70.2,0.3), new Vec3(-0.4, 0.01, 0));
        assertNotNull(landing);
        assertEquals(0.5, landing.position().z, 0);
        assertEquals(70.0625, landing.position().y, 0);
        assertEquals(80.3, landing.position().x, 0);
        assertEquals(new Vec3(-0.4, 0, 0), landing.velocity());
    }
    @Test public void legacyRailDisplaysAreRemovedWithoutChangingOtherSkins() {
        MinecraftPortal portal=mock(MinecraftPortal.class);
        for(String block:java.util.List.of("rail","powered_rail","detector_rail","activator_rail")) {
            when(portal.getSurfaceSkin()).thenReturn("minecraft:"+block+"[shape=north_south]");
            assertTrue(MinecartPortalRails.removeLegacyRailSkin(portal));
        }
        verify(portal,times(4)).setSurfaceSkin("");
        for(String skin:java.util.List.of("", "minecraft:glass", "minecraft:stone", "minecraft:water")) {
            when(portal.getSurfaceSkin()).thenReturn(skin);
            assertFalse(MinecartPortalRails.removeLegacyRailSkin(portal));
        }
        verify(portal,times(4)).setSurfaceSkin("");
    }
    @Test public void missingMisalignedSlopedAndBlockedTracksAreRejected() {
        var point = new Vec3(80.3,70.1,0.5);
        var velocity = new Vec3(-0.4,0,0);
        assertNull(MinecartPortalRails.plan(cart,level,Frame.canonical(Face.W),point,velocity));
        for (RailShape shape : new RailShape[] {RailShape.NORTH_SOUTH, RailShape.ASCENDING_EAST, RailShape.NORTH_EAST}) {
            when(level.getBlockState(new BlockPos(80,70,0))).thenReturn(Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,shape));
            assertNull(MinecartPortalRails.plan(cart,level,Frame.canonical(Face.W),point,velocity));
        }
        when(level.getBlockState(new BlockPos(80,70,0))).thenReturn(Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST));
        when(level.noCollision(eq(cart), any())).thenReturn(false);
        assertNull(MinecartPortalRails.plan(cart,level,Frame.canonical(Face.W),point,velocity));
    }
    @Test public void horizontalPortalsAndNonFiniteMovementAreRejected() {
        assertNull(MinecartPortalRails.plan(cart,level,Frame.canonical(Face.U),new Vec3(0,70,0),Vec3.ZERO));
        assertNull(MinecartPortalRails.plan(cart,level,Frame.canonical(Face.W),new Vec3(0,70,0),new Vec3(Double.NaN,0,0)));
    }
    @Test public void railOneBlockBelowIsFound() {
        when(level.getBlockState(new BlockPos(0,70,20))).thenReturn(Blocks.RAIL.defaultBlockState());
        assertNotNull(MinecartPortalRails.plan(cart,level,Frame.canonical(Face.S),new Vec3(0.2,71,20.2),new Vec3(0,0,0.35)));
    }

    @Test public void seatedPlayerFeetMayOverlapTheSupportButHeadroomMustBeClear() {
        var rider=mock(net.minecraft.server.level.ServerPlayer.class);
        when(cart.getBbHeight()).thenReturn(0.7F);
        when(rider.getBoundingBox()).thenReturn(new AABB(0.2,69.65,20.2,0.8,71.45,20.8));
        when(cart.getSelfAndPassengers()).thenAnswer(call -> java.util.stream.Stream.of(cart,rider));
        when(level.getBlockState(new BlockPos(0,70,20))).thenReturn(Blocks.RAIL.defaultBlockState());
        when(level.noCollision(eq(rider),any())).thenAnswer(call -> ((AABB)call.getArgument(1)).minY>=70);
        var point=new Vec3(0.5,70.0625,20.5);
        assertNotNull(MinecartPortalRails.plan(cart,level,Frame.canonical(Face.S),point,new Vec3(0,0,0.3)));
        when(level.noCollision(eq(rider),any())).thenReturn(false);
        assertNull(MinecartPortalRails.plan(cart,level,Frame.canonical(Face.S),point,new Vec3(0,0,0.3)));
    }
}
