package art.arcane.wormholes.modded;

import art.arcane.wormholes.portal.PortalType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ImmersivePortalCommandsTest extends MinecraftTestBase {
    private WormholesModRuntime runtime;
    private MinecraftPortalRegistry registry;
    private MinecraftAccessService access;
    private ServerPlayer player;
    private ServerLevel level;
    private CommandSourceStack source;
    private ImmersivePortalCommands commands;
    private UUID owner;

    @Before
    public void setup() throws Exception {
        runtime = mock(WormholesModRuntime.class);
        registry = mock(MinecraftPortalRegistry.class);
        access = mock(MinecraftAccessService.class);
        player = mock(ServerPlayer.class);
        level = mock(ServerLevel.class);
        source = mock(CommandSourceStack.class);
        owner = UUID.randomUUID();
        when(runtime.running()).thenReturn(true);
        when(runtime.portals()).thenReturn(registry);
        when(runtime.access()).thenReturn(access);
        when(runtime.effects()).thenReturn(mock(MinecraftPortalEffects.class));
        when(source.getPlayerOrException()).thenReturn(player);
        when(source.getPlayer()).thenReturn(player);
        when(player.getUUID()).thenReturn(owner);
        when(player.level()).thenReturn(level);
        when(player.blockPosition()).thenReturn(new BlockPos(0, 64, 0));
        when(player.getDirection()).thenReturn(Direction.NORTH);
        when(level.getMinY()).thenReturn(-64);
        when(level.getMaxY()).thenReturn(320);
        when(level.getWorldBorder()).thenReturn(new WorldBorder());
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        when(access.canConstruct(player)).thenReturn(true);
        when(access.permission(player, PortalType.PORTAL.permission())).thenReturn(true);
        when(access.canPlace(any())).thenReturn(true);
        when(registry.snapshot()).thenReturn(List.of());
        commands = new ImmersivePortalCommands(runtime);
    }

    @Test
    public void apertureIsFlatUniqueAndThreeBlocksAheadInEachDirection() {
        BlockPos feet = new BlockPos(10, 64, -20);
        for (Direction direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            List<BlockPos> cells = ImmersivePortalCommands.aperture(feet, direction, 4, 3);
            assertEquals(12, new HashSet<>(cells).size());
            for (BlockPos cell : cells) {
                assertEquals(3, (cell.getX() - feet.getX()) * direction.getStepX()
                    + (cell.getZ() - feet.getZ()) * direction.getStepZ());
                assertTrue(cell.getY() >= 64 && cell.getY() <= 66);
            }
        }
    }

    @Test
    public void createsNamedPortalWithoutChangingBlocks() throws Exception {
        MinecraftPortal portal = portal("temporary", owner);
        when(registry.create(eq(owner), eq(level), anyCollection(), eq(PortalType.PORTAL), any())).thenReturn(portal);
        assertEquals(1, commands.create(source, "maison", 2, 3));
        verify(portal).setName("maison");
        verify(registry).save(portal);
        verify(level, never()).setBlock(any(), any(), anyInt());
    }

    @Test
    public void occupiedApertureAndProtectionDenyCreation() throws Exception {
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.STONE.defaultBlockState());
        assertEquals(0, commands.create(source, "maison", 2, 3));
        verify(registry, never()).create(any(), any(), any(), any(), any());
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        when(access.canPlace(any())).thenReturn(false);
        assertEquals(0, commands.create(source, "maison", 2, 3));
        verify(registry, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    public void existingAlignedTrackCanRunThroughTheBottomOfTheOpening() throws Exception {
        MinecraftPortal portal = portal("temporary", owner);
        when(registry.create(eq(owner),eq(level),anyCollection(),eq(PortalType.PORTAL),any())).thenReturn(portal);
        when(level.getBlockState(any(BlockPos.class))).thenAnswer(call -> {
            BlockPos position = call.getArgument(0);
            return position.getY() == 64 ? Blocks.RAIL.defaultBlockState() : Blocks.AIR.defaultBlockState();
        });
        assertEquals(1,commands.create(source,"train",2,3));
        verify(level,never()).setBlock(any(),any(),anyInt());
    }

    @Test
    public void permissionBoundsAndDuplicateNamesDenyCreation() throws Exception {
        when(access.canConstruct(player)).thenReturn(false);
        assertEquals(0, commands.create(source, "maison", 2, 3));
        when(access.canConstruct(player)).thenReturn(true);
        assertEquals(0, commands.create(source, "bad/name", 2, 3));
        assertEquals(0, commands.create(source, "maison", 1000, 3));
        MinecraftPortal duplicate = portal("MAISON", owner);
        when(registry.snapshot()).thenReturn(List.of(duplicate));
        assertEquals(0, commands.create(source, "maison", 2, 3));
        verify(registry, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    public void linksBothDirectionsAndPrefersOwnersNames() throws Exception {
        MinecraftPortal a = portal("maison", owner);
        MinecraftPortal b = portal("mine", owner);
        MinecraftPortal other = portal("maison", UUID.randomUUID());
        when(registry.snapshot()).thenReturn(List.of(other, a, b));
        when(registry.canManage(eq(player), any())).thenReturn(true);
        when(registry.link(eq(player), any(), any())).thenReturn(true);
        assertEquals(1, commands.link(source, "maison", "mine"));
        verify(registry).link(player, a.getId(), b.getId());
        verify(registry).link(player, b.getId(), a.getId());
        verify(registry, never()).link(player, other.getId(), b.getId());
    }

    @Test
    public void cannotModifyInaccessiblePortalOrLinkToItself() throws Exception {
        MinecraftPortal a = portal("maison", owner);
        MinecraftPortal b = portal("mine", UUID.randomUUID());
        when(registry.snapshot()).thenReturn(List.of(a, b));
        when(registry.canManage(player, a)).thenReturn(true);
        assertEquals(0, commands.link(source, "maison", "mine"));
        assertEquals(0, commands.link(source, "maison", "MAISON"));
        assertEquals(0, commands.remove(source, "mine"));
        verify(registry, never()).link(any(), any(), any());
        verify(registry, never()).remove(any(ServerPlayer.class), any());
    }

    @Test
    public void failedSecondLinkRestoresPreviousDestination() throws Exception {
        MinecraftPortal a = portal("maison", owner);
        MinecraftPortal b = portal("mine", owner);
        UUID previous = UUID.randomUUID();
        when(a.getDestinationId()).thenReturn(previous);
        when(registry.snapshot()).thenReturn(List.of(a, b));
        when(registry.canManage(eq(player), any())).thenReturn(true);
        when(registry.link(player, a.getId(), b.getId())).thenReturn(true);
        assertEquals(0, commands.link(source, "maison", "mine"));
        verify(registry).link(player, a.getId(), previous);
    }

    @Test
    public void commandTreeExecutesHelpAndRejectsOversizedArguments() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(Commands.literal("wormholes"));
        commands.register(dispatcher);
        assertEquals(1, dispatcher.execute("portail", source));
        assertEquals(1, dispatcher.execute("portail aide", source));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
            () -> dispatcher.execute("portail creer maison 1000 3", source));
        verify(registry, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    public void bothLanguagesCreateLinkListAndDeleteTheSamePortals() throws Exception {
        List<String> messages = successMessages();
        List<MinecraftPortal> stored = new ArrayList<>();
        MinecraftPortal home = portal("home", owner);
        MinecraftPortal mine = portal("mine", owner);
        when(registry.snapshot()).thenAnswer(call -> List.copyOf(stored));
        when(registry.create(eq(owner), eq(level), anyCollection(), eq(PortalType.PORTAL), any())).thenReturn(home, mine);
        doAnswer(call -> { stored.add(call.getArgument(0)); return null; }).when(registry).save(any());
        when(registry.canManage(eq(player), any())).thenReturn(true);
        when(registry.link(eq(player), any(), any())).thenReturn(true);
        when(registry.remove(eq(player), any())).thenReturn(true);
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

        assertEquals(1, dispatcher.execute("portal create home", source));
        assertTrue(messages.getLast().contains("Portal home created (2 × 3)"));
        assertTrue(messages.getLast().contains("/portal link home"));
        assertEquals(1, dispatcher.execute("portail creer mine 4 5", source));
        assertTrue(messages.getLast().contains("Portail mine créé (4 × 5)"));
        verify(registry).create(eq(owner), eq(level), argThat(cells -> cells.size() == 6), eq(PortalType.PORTAL), any());
        verify(registry).create(eq(owner), eq(level), argThat(cells -> cells.size() == 20), eq(PortalType.PORTAL), any());

        assertEquals(1, dispatcher.execute("portal link home mine", source));
        assertTrue(messages.getLast().contains("linked in both directions"));
        assertEquals(1, dispatcher.execute("portail relier mine home", source));
        assertTrue(messages.getLast().contains("reliés dans les deux sens"));
        verify(registry, times(2)).link(player, home.getId(), mine.getId());
        verify(registry, times(2)).link(player, mine.getId(), home.getId());

        assertEquals(2, dispatcher.execute("portal list", source));
        assertEquals("2 accessible portal(s).", messages.getLast());
        assertTrue(messages.stream().anyMatch(message -> message.startsWith("home [") && message.endsWith("not linked")));
        assertEquals(2, dispatcher.execute("portail liste", source));
        assertEquals("2 portail(s) accessible(s).", messages.getLast());

        assertEquals(1, dispatcher.execute("portal delete home", source));
        assertEquals("Portal home deleted.", messages.getLast());
        assertEquals(1, dispatcher.execute("portail supprimer mine", source));
        assertEquals("Portail mine supprimé.", messages.getLast());
        verify(registry).remove(player, home.getId());
        verify(registry).remove(player, mine.getId());
    }

    @Test
    public void englishCommandsKeepPlacementPermissionsBoundsAndLinkRollback() throws Exception {
        List<String> errors = new ArrayList<>();
        doAnswer(call -> { errors.add(call.<Component>getArgument(0).getString()); return null; })
            .when(source).sendFailure(any(Component.class));
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        when(access.canConstruct(player)).thenReturn(false);
        assertEquals(0, dispatcher.execute("portal create home", source));
        assertEquals("You do not have permission to create a portal.", errors.getLast());
        when(access.canConstruct(player)).thenReturn(true);
        when(access.canPlace(any())).thenReturn(false);
        assertEquals(0, dispatcher.execute("portal create home", source));
        assertEquals("Portal creation is not allowed at this location.", errors.getLast());
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
            () -> dispatcher.execute("portal create home 17 3", source));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
            () -> dispatcher.execute("portal create home 2 1", source));
        verify(registry, never()).create(any(), any(), any(), any(), any());

        MinecraftPortal a = portal("home", owner);
        MinecraftPortal b = portal("mine", owner);
        UUID previous = UUID.randomUUID();
        when(a.getDestinationId()).thenReturn(previous);
        when(registry.snapshot()).thenReturn(List.of(a, b));
        when(registry.canManage(eq(player), any())).thenReturn(true);
        when(registry.link(player, a.getId(), b.getId())).thenReturn(true);
        assertEquals(0, dispatcher.execute("portal link home mine", source));
        verify(registry).link(player, a.getId(), previous);
        assertTrue(errors.getLast().contains("previous link has been restored"));
    }

    @Test
    public void helpAndAdvancedCommandsWorkInBothLanguagesAndStopWithTheRuntime() throws Exception {
        List<String> messages = successMessages();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(Commands.literal("wormholes").then(Commands.literal("probe").executes(context -> 7)));
        commands.register(dispatcher);
        assertEquals(1, dispatcher.execute("portal", source));
        assertTrue(messages.getLast().contains("/portal create <name> [width height]"));
        assertEquals(1, dispatcher.execute("portal help", source));
        assertTrue(messages.getLast().contains("The opening must be clear"));
        assertEquals(1, dispatcher.execute("portail aide", source));
        assertTrue(messages.getLast().contains("L'ouverture doit être libre"));
        assertEquals(7, dispatcher.execute("portal advanced probe", source));
        assertEquals(7, dispatcher.execute("portail avance probe", source));
        when(runtime.running()).thenReturn(false);
        for (String command : List.of("portal help", "portail aide", "portal advanced probe", "portail avance probe")) {
            assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute(command, source));
        }
    }

    @Test
    public void bothLanguagesSuggestOnlyAccessiblePortalNames() throws Exception {
        MinecraftPortal allowed = portal("Home", owner);
        MinecraftPortal hidden = portal("Hidden", UUID.randomUUID());
        when(registry.snapshot()).thenReturn(List.of(allowed, hidden));
        when(registry.canManage(player, allowed)).thenReturn(true);
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        for (String command : List.of("portal link h", "portal link Home h", "portal delete h",
            "portail relier h", "portail relier Home h", "portail supprimer h")) {
            var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(command, source)).get();
            assertEquals(List.of("Home"), suggestions.getList().stream().map(suggestion -> suggestion.getText()).toList());
        }
    }

    private CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(Commands.literal("wormholes"));
        commands.register(dispatcher);
        return dispatcher;
    }

    private List<String> successMessages() {
        List<String> messages = new ArrayList<>();
        doAnswer(call -> {
            Supplier<Component> message = call.getArgument(0);
            messages.add(message.get().getString());
            return null;
        }).when(source).sendSuccess(any(), eq(false));
        return messages;
    }

    private MinecraftPortal portal(String name, UUID owner) {
        MinecraftPortal portal = mock(MinecraftPortal.class);
        when(portal.getId()).thenReturn(UUID.randomUUID());
        when(portal.getOwner()).thenReturn(owner);
        when(portal.getName()).thenReturn(name);
        when(portal.getType()).thenReturn(PortalType.PORTAL);
        return portal;
    }
}
