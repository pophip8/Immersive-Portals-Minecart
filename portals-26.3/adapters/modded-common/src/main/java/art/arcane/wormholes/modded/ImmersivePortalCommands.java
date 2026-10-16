package art.arcane.wormholes.modded;

import art.arcane.wormholes.portal.PortalType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** French and English shortcuts sharing the same Wormholes portal registry. */
public final class ImmersivePortalCommands {
    private final WormholesModRuntime runtime;
    private final boolean english;

    public ImmersivePortalCommands(WormholesModRuntime runtime) {
        this(runtime, false);
    }

    private ImmersivePortalCommands(WormholesModRuntime runtime, boolean english) {
        this.runtime = runtime;
        this.english = english;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(tree(dispatcher));
        dispatcher.register(new ImmersivePortalCommands(runtime, true).tree(dispatcher));
    }

    private LiteralArgumentBuilder<CommandSourceStack> tree(CommandDispatcher<CommandSourceStack> dispatcher) {
        String name = text("nom", "name");
        String width = text("largeur", "width");
        String height = text("hauteur", "height");
        String first = text("premier", "first");
        String second = text("second", "second");
        return Commands.literal(text("portail", "portal")).requires(source -> runtime.running())
            .executes(context -> help(context.getSource()))
            .then(Commands.literal(text("aide", "help")).executes(context -> help(context.getSource())))
            .then(Commands.literal(text("creer", "create"))
                .then(Commands.argument(name, StringArgumentType.word())
                    .executes(context -> create(context.getSource(), StringArgumentType.getString(context, name), 2, 3))
                    .then(Commands.argument(width, IntegerArgumentType.integer(1, 16))
                        .then(Commands.argument(height, IntegerArgumentType.integer(2, 16))
                            .executes(context -> create(context.getSource(), StringArgumentType.getString(context, name),
                                IntegerArgumentType.getInteger(context, width), IntegerArgumentType.getInteger(context, height)))))))
            .then(Commands.literal(text("relier", "link"))
                .then(Commands.argument(first, StringArgumentType.word()).suggests(this::suggest)
                    .then(Commands.argument(second, StringArgumentType.word()).suggests(this::suggest)
                        .executes(context -> link(context.getSource(), StringArgumentType.getString(context, first),
                            StringArgumentType.getString(context, second))))))
            .then(Commands.literal(text("supprimer", "delete"))
                .then(Commands.argument(name, StringArgumentType.word()).suggests(this::suggest)
                    .executes(context -> remove(context.getSource(), StringArgumentType.getString(context, name)))))
            .then(Commands.literal(text("liste", "list")).executes(context -> list(context.getSource())))
            .then(Commands.literal(text("avance", "advanced")).redirect(dispatcher.getRoot().getChild("wormholes")));
    }

    private String text(String french, String english) {
        return this.english ? english : french;
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            String prefix = builder.getRemainingLowerCase();
            for (MinecraftPortal portal : runtime.portals().snapshot()) {
                if (runtime.portals().canManage(player, portal) && portal.getName().matches("[A-Za-z0-9_-]{1,32}")
                    && portal.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(portal.getName());
                }
            }
        }
        return builder.buildFuture();
    }

    int create(CommandSourceStack source, String name, int width, int height) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!name.matches("[A-Za-z0-9_-]{1,32}") || width < 1 || width > 16 || height < 2 || height > 16) {
            return fail(source, text("Nom : 1 à 32 lettres/chiffres, tirets ou underscores. Dimensions : largeur 1–16, hauteur 2–16.",
                "Name: 1–32 ASCII letters, digits, hyphens or underscores. Dimensions: width 1–16, height 2–16."));
        }
        if (!runtime.access().canConstruct(player) || !runtime.access().permission(player, PortalType.PORTAL.permission())) {
            return fail(source, text("Tu n'as pas la permission de créer un portail.", "You do not have permission to create a portal."));
        }
        // Names are scoped to the owner; another player's portal cannot block this name.
        if (runtime.portals().snapshot().stream().anyMatch(portal -> player.getUUID().equals(portal.getOwner())
            && name.equalsIgnoreCase(portal.getName()))) {
            return fail(source, text("Tu possèdes déjà un portail nommé ", "You already own a portal named ") + name + ".");
        }
        Direction facing = player.getDirection();
        List<BlockPos> cells = aperture(player.blockPosition(), facing, width, height);
        for (BlockPos cell : cells) {
            if (!player.level().getWorldBorder().isWithinBounds(cell)
                || cell.getY() < player.level().getMinY() || cell.getY() >= player.level().getMaxY()) {
                return fail(source, text("L'ouverture dépasse les limites du monde.", "The opening extends beyond the world limits."));
            }
            var state = player.level().getBlockState(cell);
            boolean track = cell.getY() == player.blockPosition().getY()
                && MinecartPortalRails.alignedRail(state, facing.getStepX() != 0);
            if (!state.isAir() && !track) {
                return fail(source, text("Libère l'ouverture à trois blocs devant toi ; seuls des rails droits au pied sont acceptés. Aucun bloc ne sera supprimé.",
                    "Clear the opening three blocks ahead of you; only aligned straight rails at its base are allowed. No blocks will be removed."));
            }
        }
        if (!runtime.access().canPlace(new MinecraftAccessService.Placement(player, player.level(), cells,
            MinecraftAccessService.PlacementKind.WAND))) {
            return fail(source, text("La création d'un portail est interdite à cet endroit.", "Portal creation is not allowed at this location."));
        }
        try {
            MinecraftPortal portal = runtime.portals().create(player.getUUID(), player.level(), cells,
                PortalType.PORTAL, new Vec3(facing.getStepX(), 0, facing.getStepZ()));
            portal.setName(name);
            runtime.portals().save(portal);
            runtime.effects().created(portal, java.util.Map.of());
            source.sendSuccess(() -> Component.literal(text("Portail ", "Portal ") + name + text(" créé (", " created (")
                + width + " × " + height + text("). Crée le second, puis /portail relier ",
                "). Create the second portal, then use /portal link ") + name + text(" autre_nom.", " other_name.")), false);
            return 1;
        } catch (IllegalArgumentException error) {
            return fail(source, text("Impossible de créer le portail : ", "Unable to create the portal: ") + error.getMessage());
        }
    }

    static List<BlockPos> aperture(BlockPos feet, Direction facing, int width, int height) {
        Direction right = facing.getClockWise();
        BlockPos origin = feet.relative(facing, 3).relative(right, -(width / 2));
        List<BlockPos> cells = new ArrayList<>(width * height);
        for (int column = 0; column < width; column++) {
            for (int row = 0; row < height; row++) {
                cells.add(origin.relative(right, column).above(row));
            }
        }
        return List.copyOf(cells);
    }

    int link(CommandSourceStack source, String first, String second) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftPortal a = find(source, player, first);
        MinecraftPortal b = find(source, player, second);
        if (a == null || b == null) { return 0; }
        if (a.getId().equals(b.getId())) { return fail(source, text("Choisis deux portails différents.", "Choose two different portals.")); }
        if (!linkable(a) || !linkable(b)) {
            return fail(source, text("Cette commande relie uniquement des portails ordinaires non gérés automatiquement.",
                "This command only links ordinary portals that are not managed automatically."));
        }
        UUID previous = a.getDestinationId();
        if (!runtime.portals().link(player, a.getId(), b.getId())) {
            return fail(source, text("Impossible de relier le premier portail.", "Unable to link the first portal."));
        }
        if (!runtime.portals().link(player, b.getId(), a.getId())) {
            runtime.portals().link(player, a.getId(), previous);
            return fail(source, text("Impossible de relier le second portail ; la première liaison a été rétablie.",
                "Unable to link the second portal; the first portal's previous link has been restored."));
        }
        source.sendSuccess(() -> Component.literal(text("Portails ", "Portals ") + a.getName() + " ↔ " + b.getName()
            + text(" reliés dans les deux sens.", " linked in both directions.")), false);
        return 1;
    }

    private static boolean linkable(MinecraftPortal portal) {
        return portal.getType() == PortalType.PORTAL && !portal.isMirrorMode() && !portal.isManaged();
    }

    int remove(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftPortal portal = find(source, player, name);
        if (portal == null) { return 0; }
        if (!linkable(portal)) { return fail(source, text("Utilise les outils avancés pour supprimer ce portail spécial.",
            "Use the advanced tools to delete this special portal.")); }
        if (!runtime.portals().remove(player, portal.getId())) { return fail(source, text("Suppression refusée.", "Deletion denied.")); }
        source.sendSuccess(() -> Component.literal(text("Portail ", "Portal ") + portal.getName() + text(" supprimé.", " deleted.")), false);
        return 1;
    }

    private MinecraftPortal find(CommandSourceStack source, ServerPlayer player, String name) {
        List<MinecraftPortal> candidates = runtime.portals().snapshot().stream()
            .filter(portal -> name.equalsIgnoreCase(portal.getName()) && runtime.portals().canManage(player, portal)).toList();
        List<MinecraftPortal> owned = candidates.stream().filter(portal -> player.getUUID().equals(portal.getOwner())).toList();
        List<MinecraftPortal> matches = owned.isEmpty() ? candidates : owned;
        if (matches.size() != 1) {
            fail(source, matches.isEmpty() ? text("Portail introuvable ou accès refusé : ", "Portal not found or access denied: ") + name
                : text("Nom ambigu : ", "Ambiguous name: ") + name
                    + text(". Utilise /wormholes portals avec l'UUID.", ". Use /wormholes portals with the UUID."));
            return null;
        }
        return matches.getFirst();
    }

    private int list(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<MinecraftPortal> portals = runtime.portals().snapshot().stream()
            .filter(portal -> runtime.portals().canManage(player, portal)).toList();
        for (MinecraftPortal portal : portals) {
            MinecraftPortal destination = portal.getDestinationId() == null ? null : runtime.portals().get(portal.getDestinationId());
            String line = portal.getName() + " [" + portal.getWorldKey() + "] → "
                + (destination == null ? text("non relié", "not linked") : destination.getName());
            source.sendSuccess(() -> Component.literal(line), false);
        }
        source.sendSuccess(() -> Component.literal(portals.size() + text(" portail(s) accessible(s).", " accessible portal(s).")), false);
        return portals.size();
    }

    private int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(text("Portails Immersifs — /portail aide, /portail creer <nom> [largeur hauteur], "
            + "/portail relier <nom1> <nom2>, /portail liste, /portail supprimer <nom>. "
            + "L'ouverture doit être libre à trois blocs devant toi. Outils avancés : /portail avance ou /wormholes wand. English: /portal help.",
            "Immersive Portals — /portal help, /portal create <name> [width height], "
            + "/portal link <first> <second>, /portal list, /portal delete <name>. "
            + "The opening must be clear three blocks ahead of you. Advanced tools: /portal advanced or /wormholes wand. Français : /portail aide.")), false);
        return 1;
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
