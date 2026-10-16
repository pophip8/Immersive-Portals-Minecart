package art.arcane.wormholes.clientgametest;

import art.arcane.wormholes.modded.MinecraftPortal;
import art.arcane.wormholes.portal.PortalType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static art.arcane.wormholes.clientgametest.NativeClientViewAssertions.runtime;
import static art.arcane.wormholes.clientgametest.SeamlessScenario.assertTrue;

/** Physical rail travel through a rotated Overworld/Nether pair, with cargo and passengers. */
public final class KBMinecartClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        ClientViewTestConfig.enableSeamless(true);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            scenario(context,world.getServer(),world.getConnection(),"solo");
        }
        try (TestDedicatedServerContext server=context.worldBuilder().createServer(ClientViewTestConfig.serverProperties());
             TestDedicatedServerConnection connection=server.connect()) {
            scenario(context,server,connection,"dedicated");
        }
    }
    private static void scenario(ClientGameTestContext context,TestServerContext serverContext,TestServerConnection connection,String label) {
            SeamlessScenario.join(new FabricSeamlessClient(context, connection));
            serverContext.runOnServer(server -> build(server,connection.getServerPlayer()));
            UUID cargo = serverContext.computeOnServer(server -> {
                MinecartChest cart = new MinecartChest(EntityTypes.CHEST_MINECART,server.overworld());
                cart.setItem(0,new ItemStack(Items.DIAMOND,42));
                cart.setItem(17,new ItemStack(Items.RAIL,23));
                launch(cart,true);
                assertTrue(server.overworld().addFreshEntity(cart),"cargo spawn failed");
                return cart.getUUID();
            });
            for (int trip=0;trip<4;trip++) {
                boolean outbound = trip % 2 == 0;
                if (trip>0) { serverContext.runOnServer(server -> launch(find(server,cargo),outbound)); }
                await(context,serverContext,cargo,outbound);
                serverContext.runOnServer(server -> {
                    AbstractMinecart arrived=find(server,cargo);
                    assertTrue(arrived instanceof MinecartChest,"cargo cart type changed");
                    MinecartChest chest=(MinecartChest)arrived;
                    assertTrue(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount()==42,"cargo lost or duplicated");
                    assertTrue(chest.getItem(17).is(Items.RAIL) && chest.getItem(17).getCount()==23,"second slot changed");
                    assertTrue(Math.hypot(chest.getDeltaMovement().x,chest.getDeltaMovement().z)>0.01,"cart lost its momentum");
                    assertTrue(count(server,cargo)==1,"cart UUID duplicated across dimensions");
                });
            }
            serverContext.runOnServer(server -> find(server,cargo).discard());
            LoggerFactory.getLogger("KBMinecartAcceptance").info("KB_MINECART_CARGO_PASS 4 crossings, rotated rails, UUID and two inventory slots retained");

            UUID[] occupied = serverContext.computeOnServer(server -> {
                Minecart cart=new Minecart(EntityTypes.MINECART,server.overworld());
                launch(cart,true);
                server.overworld().addFreshEntity(cart);
                Pig pig=new Pig(EntityTypes.PIG,server.overworld());
                pig.setNoAi(true);
                pig.setPos(cart.position());
                server.overworld().addFreshEntity(pig);
                assertTrue(pig.startRiding(cart,true,false),"pig could not mount");
                return new UUID[]{cart.getUUID(),pig.getUUID()};
            });
            await(context,serverContext,occupied[0],true);
            serverContext.runOnServer(server -> {
                assertTrue(find(server,occupied[0]).getPassengers().stream().anyMatch(e->e.getUUID().equals(occupied[1])),"mob passenger detached");
                assertTrue(count(server,occupied[1])==1,"passenger duplicated");
                launch(find(server,occupied[0]),false);
            });
            await(context,serverContext,occupied[0],false);
            serverContext.runOnServer(server -> {
                assertTrue(find(server,occupied[0]).getPassengers().stream().anyMatch(e->e.getUUID().equals(occupied[1])),"return passenger detached");
                for(Entity passenger:find(server,occupied[0]).getPassengers()) {passenger.discard();}
                find(server,occupied[0]).discard();
            });
            LoggerFactory.getLogger("KBMinecartAcceptance").info("KB_MINECART_MOB_PASS round trip with the same passenger UUID");

            UUID ridden=serverContext.computeOnServer(server -> {
                ServerPlayer player=connection.getServerPlayer();
                player.teleportTo(server.overworld(),0.5,71,24.5,Set.of(),180,0,false);
                Minecart cart=new Minecart(EntityTypes.MINECART,server.overworld());
                launch(cart,true);
                server.overworld().addFreshEntity(cart);
                assertTrue(player.startRiding(cart,true,false),"player could not mount");
                return cart.getUUID();
            });
            await(context,serverContext,ridden,true);
            context.waitFor(minecraft -> minecraft.player!=null && minecraft.player.getVehicle()!=null
                && minecraft.player.getVehicle().getUUID().equals(ridden),200);
            serverContext.runOnServer(server -> {
                ServerPlayer player=connection.getServerPlayer();
                assertTrue(player.level().dimension()==Level.NETHER && player.getVehicle()!=null && player.getVehicle().getUUID().equals(ridden),"player did not arrive seated");
                launch(find(server,ridden),false);
            });
            await(context,serverContext,ridden,false);
            context.waitFor(minecraft -> minecraft.player!=null && minecraft.level!=null
                && minecraft.level.dimension()==Level.OVERWORLD && minecraft.player.getVehicle()!=null
                && minecraft.player.getVehicle().getUUID().equals(ridden),200);
            serverContext.runOnServer(server -> {
                ServerPlayer player=connection.getServerPlayer();
                assertTrue(player.getVehicle()!=null && player.getVehicle().getUUID().equals(ridden),"player detached on return");
                player.stopRiding();
                find(server,ridden).discard();
            });
            LoggerFactory.getLogger("KBMinecartAcceptance").info("KB_MINECART_PLAYER_PASS seated round trip");

            UUID blocked=serverContext.computeOnServer(server -> {
                ServerLevel nether=server.getLevel(Level.NETHER);
                for(int x=69;x<=92;x++)for(int z=0;z<=1;z++)nether.setBlock(new BlockPos(x,70,z),Blocks.AIR.defaultBlockState(),2);
                Minecart cart=new Minecart(EntityTypes.MINECART,server.overworld());
                launch(cart,true); server.overworld().addFreshEntity(cart); return cart.getUUID();
            });
            context.waitTicks(80);
            serverContext.runOnServer(server -> {
                AbstractMinecart cart=find(server,blocked);
                assertTrue(cart.level().dimension()==Level.OVERWORLD && cart.isAlive(),"unsafe destination lost/transferred the wagon");
                assertTrue(cart.getDeltaMovement().lengthSqr()<1e-8,"unsafe destination did not brake the wagon");
                assertTrue(count(server,blocked)==1,"unsafe destination duplicated the wagon");
                cart.discard();
            });
            LoggerFactory.getLogger("KBMinecartAcceptance").info("KB_MINECART_MISSING_RAIL_PASS source retained and braked");
            context.takeScreenshot("kb-minecart-rails-"+label);
            LoggerFactory.getLogger("KBMinecartAcceptance").info("KB_MINECART_SCENARIO_PASS {}",label);
    }

    private static void await(ClientGameTestContext context,TestServerContext serverContext,UUID id,boolean outbound) {
        for (int tick=0;tick<600;tick++) {
            boolean present=serverContext.computeOnServer(server -> {
                ServerLevel level=outbound?server.getLevel(Level.NETHER):server.overworld();
                return level.getEntity(id) instanceof AbstractMinecart;
            });
            if (present) {context.waitTicks(3);return;}
            context.waitTicks(1);
        }
        String state=serverContext.computeOnServer(server -> {
            AbstractMinecart cart=find(server,id);
            return cart.level().dimension()+" pos="+cart.position()+" motion="+cart.getDeltaMovement()+" riders="+cart.getPassengers().size()
                +" waiting="+runtime(server).portals().minecartWaiting(cart);
        });
        throw new AssertionError("minecart did not cross in 600 ticks: "+id+" "+state);
    }
    private static AbstractMinecart find(MinecraftServer server,UUID id) {
        for(ServerLevel level:server.getAllLevels())if(level.getEntity(id) instanceof AbstractMinecart cart)return cart;
        throw new AssertionError("minecart lost: "+id);
    }
    private static int count(MinecraftServer server,UUID id) {
        int count=0; for(ServerLevel level:server.getAllLevels())if(level.getEntity(id)!=null)count++; return count;
    }
    private static void launch(AbstractMinecart cart,boolean outbound) {
        double offset=cart.getBehavior() instanceof NewMinecartBehavior?0.1:0.0625;
        cart.setInitialPos(outbound?0.5:76.5,70+offset,outbound?24.5:0.5);
        cart.setOnRails(true);
        cart.setDeltaMovement(outbound?new Vec3(0,0,-0.35):new Vec3(0.35,0,0));
        for(Entity rider:cart.getPassengers())if(rider instanceof ServerPlayer player){
            player.connection.send(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket.fromEntity(cart));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(cart));
        }
    }
    private static void build(MinecraftServer server,ServerPlayer player) {
        ServerLevel overworld=server.overworld(),nether=server.getLevel(Level.NETHER);
        for(int x=-3;x<=4;x++)for(int z=8;z<=32;z++) {
            overworld.setBlock(new BlockPos(x,69,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=70;y<=75;y++)overworld.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        for(int x=68;x<=93;x++)for(int z=-3;z<=4;z++) {
            nether.setBlock(new BlockPos(x,69,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=70;y<=75;y++)nether.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        for(int pass=0;pass<2;pass++) {
            for(int z=8;z<=32;z++)for(int x=0;x<=1;x++)overworld.setBlock(new BlockPos(x,70,z),Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,RailShape.NORTH_SOUTH),2);
            for(int x=68;x<=93;x++)for(int z=0;z<=1;z++)nether.setBlock(new BlockPos(x,70,z),Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE,RailShape.EAST_WEST),2);
        }
        List<BlockPos> a=new ArrayList<>(),b=new ArrayList<>();
        for(int lane=0;lane<2;lane++)for(int y=70;y<=73;y++){a.add(new BlockPos(lane,y,20));b.add(new BlockPos(80,y,lane));}
        MinecraftPortal source=runtime(server).portals().create(player.getUUID(),overworld,a,PortalType.PORTAL,new Vec3(0,0,1));
        MinecraftPortal exit=runtime(server).portals().create(player.getUUID(),nether,b,PortalType.PORTAL,new Vec3(-1,0,0));
        assertTrue(runtime(server).portals().link(player,source.getId(),exit.getId()),"source rail link refused");
        assertTrue(runtime(server).portals().link(player,exit.getId(),source.getId()),"return rail link refused");
    }
}
