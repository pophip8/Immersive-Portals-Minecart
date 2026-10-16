package art.arcane.wormholes.clientgametest;

import art.arcane.wormholes.modded.MinecraftAccessService;
import art.arcane.wormholes.modded.MinecraftPortal;
import art.arcane.wormholes.transit.OrientationPolicy;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.LoggerFactory;
import java.util.Set;

import static art.arcane.wormholes.clientgametest.NativeClientViewAssertions.runtime;
import static art.arcane.wormholes.clientgametest.SeamlessScenario.assertTrue;

/** Exercises the real command dispatcher, then a physical cross-dimension return trip. */
public final class KBPortalClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        ClientViewTestConfig.enableSeamless(true);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            SeamlessScenario.join(new FabricSeamlessClient(context, world.getConnection()));
            world.getServer().runOnServer(server -> {
                ServerPlayer player = world.getConnection().getServerPlayer();
                player.setGameMode(GameType.CREATIVE);
                for (int x = -6; x <= 38; x++) {
                    for (int z = -9; z <= 7; z++) {
                        server.overworld().setBlockAndUpdate(new BlockPos(x, 69, z), Blocks.STONE.defaultBlockState());
                        for (int y = 70; y <= 77; y++) {
                            server.overworld().setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                try (AutoCloseable permission = runtime(server).access().register((actor, node) -> MinecraftAccessService.Decision.ALLOW)) {
                    player.teleportTo(server.overworld(), 0.5, 70, 0.5, Set.of(), 180.0F, 0.0F, false);
                    assertTrue(server.getCommands().getDispatcher().execute("portail creer maison", player.createCommandSourceStack()) == 1,
                        "real /portail creer command failed");
                    player.teleportTo(server.overworld(), 32.5, 70, 0.5, Set.of(), 180.0F, 0.0F, false);
                    assertTrue(server.getCommands().getDispatcher().execute("portail creer mine", player.createCommandSourceStack()) == 1,
                        "second real portal command failed");
                    assertTrue(server.getCommands().getDispatcher().execute("portail relier maison mine", player.createCommandSourceStack()) == 1,
                        "real /portail relier command failed");
                    MinecraftPortal a = runtime(server).portals().snapshot().stream().filter(p -> p.getName().equals("maison")).findFirst().orElseThrow();
                    MinecraftPortal b = runtime(server).portals().snapshot().stream().filter(p -> p.getName().equals("mine")).findFirst().orElseThrow();
                    assertTrue(b.getId().equals(a.getDestinationId()) && a.getId().equals(b.getDestinationId()), "two-way links were not retained");
                    player.teleportTo(server.overworld(), 0.5, 70, 0.5, Set.of(), 180.0F, 0.0F, false);
                } catch (Exception error) {
                    throw new IllegalStateException("KB command acceptance failed", error);
                }
            });
            world.getConnection().waitForChunksRender();
            context.waitTicks(60);
            context.takeScreenshot("kb-command-portals");
            LoggerFactory.getLogger("KBPortalAcceptance").info("KB_COMMANDS_IN_GAME_PASS");
        }
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            SeamlessCrossDimension.seamless(new FabricSeamlessClient(context, world.getConnection()),
                new FabricSeamlessServer(world.getServer(), world.getConnection()), "kb-cross-dimension", OrientationPolicy.FRAME);
            context.takeScreenshot("kb-cross-dimension-return");
            LoggerFactory.getLogger("KBPortalAcceptance").info("KB_CROSS_DIMENSION_PASS");
        }
    }
}
