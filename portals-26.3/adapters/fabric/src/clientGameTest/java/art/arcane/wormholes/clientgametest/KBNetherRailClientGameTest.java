package art.arcane.wormholes.clientgametest;

import art.arcane.wormholes.modded.MinecraftPortal;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.UUID;
import static art.arcane.wormholes.clientgametest.NativeClientViewAssertions.runtime;
import static art.arcane.wormholes.clientgametest.SeamlessScenario.assertTrue;

/** Places rails through player interactions, then crosses genuine Nether portals. */
public final class KBNetherRailClientGameTest implements FabricClientGameTest {
    private static final BlockPos SOURCE=new BlockPos(0,70,20);
    @Override public void runTest(ClientGameTestContext context) {
        ClientViewTestConfig.enableSeamless(true);
        try(TestSingleplayerContext world=context.worldBuilder().adjustSettings(s->s.setAllowCommands(true)).create()) {
            scenario(context,world.getServer(),world.getConnection(),"solo");
        }
        try(TestDedicatedServerContext server=context.worldBuilder().createServer(ClientViewTestConfig.serverProperties());
            TestDedicatedServerConnection connection=server.connect()) {
            scenario(context,server,connection,"dedicated");
        }
    }
    private static void scenario(ClientGameTestContext context,TestServerContext world,TestServerConnection connection,String label) {
        SeamlessScenario.join(new FabricSeamlessClient(context,connection));
        world.runOnServer(s->{
            runtime(s).access().register((actor,node)->art.arcane.wormholes.modded.MinecraftAccessService.Decision.ALLOW);
            connection.getServerPlayer().setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            SeamlessWalkThrough.light(s,connection.getServerPlayer(),SOURCE);
        });
        boolean lit=false;
        for(int t=0;t<600;t++) {
            if(world.computeOnServer(s->SeamlessWalkThrough.lit(s,SOURCE))){lit=true;break;}
            context.waitTicks(1);
        }
        assertTrue(lit,"real Nether ignition failed");
        UUID[] pair=world.computeOnServer(s->{
            MinecraftPortal a=runtime(s).portals().at(s.overworld(),SOURCE);
            MinecraftPortal b=runtime(s).portals().get(a.getDestinationId());
            track(s,a);track(s,b);
            placeThreshold(s,connection.getServerPlayer(),a);
            placeThreshold(s,connection.getServerPlayer(),b);
            return new UUID[]{a.getId(),b.getId()};
        });
        UUID cargo=world.computeOnServer(s->{
            MinecartChest cart=new MinecartChest(EntityTypes.CHEST_MINECART,s.overworld());
            cart.setItem(0,new ItemStack(Items.DIAMOND,42));
            launch(s,cart,pair[0]);s.overworld().addFreshEntity(cart);return cart.getUUID();
        });
        for(int trip=0;trip<4;trip++) {
            boolean outbound=trip%2==0;
            if(trip>0)world.runOnServer(s->launch(s,find(s,cargo),pair[outbound?0:1]));
            await(context,world,cargo,outbound);
            world.runOnServer(s->{
                MinecartChest cart=(MinecartChest)find(s,cargo);
                assertTrue(cart.getItem(0).getCount()==42 && cart.getItem(0).is(Items.DIAMOND),"cargo changed");
                assertTrue(cart.getDeltaMovement().horizontalDistance()>0.01,"cart stopped at exit");
            });
        }
        world.runOnServer(s->find(s,cargo).discard());
        UUID ridden=world.computeOnServer(s->{
            ServerPlayer player=connection.getServerPlayer();
            Minecart cart=new Minecart(EntityTypes.MINECART,s.overworld());launch(s,cart,pair[0]);
            player.teleportTo(s.overworld(),cart.getX(),cart.getY()+1,cart.getZ(),Set.of(),180,0,false);
            s.overworld().addFreshEntity(cart);assertTrue(player.startRiding(cart,true,false),"mount failed");
            return cart.getUUID();
        });
        for(int trip=0;trip<2;trip++) {
            boolean outbound=trip==0;
            if(!outbound)world.runOnServer(s->launch(s,find(s,ridden),pair[1]));
            await(context,world,ridden,outbound);
            context.waitFor(c->c.player!=null && c.player.getVehicle()!=null && c.player.getVehicle().getUUID().equals(ridden)
                && c.level.dimension()==(outbound?Level.NETHER:Level.OVERWORLD),200);
            world.runOnServer(s->assertTrue(connection.getServerPlayer().getRootVehicle().getUUID().equals(ridden),"rider detached"));
        }
        world.runOnServer(s->{connection.getServerPlayer().stopRiding();find(s,ridden).discard();});
        world.runOnServer(s->{
            MinecraftPortal portal=runtime(s).portals().get(pair[0]);BlockPos p=rail(portal);var n=portal.getFrame().getNormal();
            connection.getServerPlayer().teleportTo(s.overworld(),p.getX()+0.5+n.x()*4,p.getY(),p.getZ()+0.5+n.z()*4,
                Set.of(),(float)Math.toDegrees(Math.atan2(n.x(),-n.z())),0,false);
        });
        context.waitTicks(30);
        context.takeScreenshot("kb-real-nether-rails-"+label);
        world.runOnServer(s->runtime(s).portals().remove(pair[0]));
        System.out.println("KB_REAL_NETHER_RAIL_PASS "+label+" real ignition, physical player rail placement, powered rails, cargo and rider round trips");
    }
    private static BlockPos rail(MinecraftPortal portal) {
        var area=portal.getGeometry().getArea();var n=portal.getFrame().getNormal();var origin=portal.getOrigin();
        return BlockPos.containing(n.x()!=0?origin.x():area.getXa()+0.5,area.getYa(),n.z()!=0?origin.z():area.getZa()+0.5);
    }
    private static void track(MinecraftServer server,MinecraftPortal portal) {
        ServerLevel level=runtime(server).portals().resolveLevel(portal);
        BlockPos center=rail(portal);var n=portal.getFrame().getNormal();boolean x=n.x()!=0;
        for(int step=-8;step<=8;step++) {
            BlockPos p=center.offset(x?step:0,0,x?0:step);
            for(int y=0;y<3;y++)level.setBlock(p.above(y),Blocks.AIR.defaultBlockState(),2);
            if(step!=0)level.setBlock(p.below(),Blocks.REDSTONE_BLOCK.defaultBlockState(),2);
            level.setBlock(p,Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.POWERED,true)
                .setValue(PoweredRailBlock.SHAPE,(step==0)==x?RailShape.NORTH_SOUTH:RailShape.EAST_WEST),2);
        }
    }
    private static void launch(MinecraftServer s,AbstractMinecart cart,UUID portalId) {
        MinecraftPortal portal=runtime(s).portals().get(portalId);BlockPos p=rail(portal);var normal=portal.getFrame().getNormal();
        Vec3 n=new Vec3(normal.x(),0,normal.z());
        cart.setInitialPos(p.getX()+0.5+n.x*4,p.getY()+(cart.getBehavior() instanceof NewMinecartBehavior?0.1:0.0625),p.getZ()+0.5+n.z*4);
        cart.setOnRails(true);cart.setDeltaMovement(n.scale(-0.35));
        for(var rider:cart.getPassengers())if(rider instanceof ServerPlayer player){
            player.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(cart));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(cart));
        }
    }
    private static void placeThreshold(MinecraftServer s,ServerPlayer player,MinecraftPortal portal) {
        ServerLevel level=runtime(s).portals().resolveLevel(portal);BlockPos p=rail(portal);var n=portal.getFrame().getNormal();
        level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
        player.teleportTo(level,p.getX()+0.5+n.x()*2,p.getY(),p.getZ()+0.5+n.z()*2,Set.of(),0,0,false);
        Vec3 look=Vec3.atCenterOf(p).subtract(player.getEyePosition());
        player.setYRot((float)Math.toDegrees(Math.atan2(-look.x,look.z)));
        player.setXRot((float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.POWERED_RAIL));
        player.gameMode.useItemOn(player,level,player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND,
            new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(p.below()).add(0,0.5,0),net.minecraft.core.Direction.UP,p.below(),false));
        assertTrue(level.getBlockState(p).getBlock() instanceof BaseRailBlock,
            "RAIL_PLACEMENT_INTERCEPTED: threshold="+level.getBlockState(p)+" portal skin="+portal.getSurfaceSkin());
        assertTrue(portal.getSurfaceSkin().isEmpty(),"rail placement painted the portal");
        assertTrue(level.getBlockState(p).getValue(PoweredRailBlock.SHAPE)==(n.x()!=0?RailShape.EAST_WEST:RailShape.NORTH_SOUTH),"placed rail points across the aperture");
    }
    private static AbstractMinecart find(MinecraftServer s,UUID id) {
        for(ServerLevel level:s.getAllLevels())if(level.getEntity(id) instanceof AbstractMinecart cart)return cart;
        throw new AssertionError("cart lost "+id);
    }
    private static void await(ClientGameTestContext c,TestServerContext world,UUID id,boolean outbound) {
        for(int t=0;t<240;t++) {
            if(world.computeOnServer(s->find(s,id).level().dimension()==(outbound?Level.NETHER:Level.OVERWORLD))){c.waitTicks(5);return;}
            c.waitTicks(1);
        }
        throw new AssertionError(world.computeOnServer(s->{var cart=find(s,id);return "REAL_NETHER_CART_BLOCKED position="+cart.position()+" velocity="+cart.getDeltaMovement()+" block="+cart.level().getBlockState(cart.blockPosition());}));
    }
}
