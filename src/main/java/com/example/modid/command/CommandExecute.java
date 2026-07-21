package com.example.restore_newer_command_syntax.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.EntityNotFoundException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Backports the most useful modern /execute grammar to Minecraft 1.12.2. */
public class CommandExecute extends CommandBase {

    @Override
    public String getName() {
        return "execute";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/execute <as|at|positioned|rotated|facing|align|anchored|in|if|unless|run> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw usage(sender);
        }

        Entity initialEntity = sender.getCommandSenderEntity();
        float initialYaw = initialEntity == null ? 0.0F : initialEntity.rotationYaw;
        float initialPitch = initialEntity == null ? 0.0F : initialEntity.rotationPitch;
        List<ExecutionContext> contexts = Collections.singletonList(new ExecutionContext(
                sender, initialEntity, sender.getEntityWorld(), sender.getPositionVector(), initialYaw, initialPitch, false));

        int index = 0;
        while (index < args.length) {
            String subcommand = args[index++].toLowerCase(Locale.ROOT);

            if ("run".equals(subcommand)) {
                if (index >= args.length) {
                    throw usage(sender);
                }
                String command = String.join(" ", Arrays.copyOfRange(args, index, args.length));
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                for (ExecutionContext context : contexts) {
                    String contextualCommand = command;
                    if (context.entity != null) {
                        contextualCommand = contextualCommand.replaceAll(
                                "(?<!\\S)@s(?!\\S)",
                                java.util.regex.Matcher.quoteReplacement(context.entity.getUniqueID().toString()));
                    }
                    server.getCommandManager().executeCommand(context, contextualCommand);
                }
                return;
            }

            if ("as".equals(subcommand) || "at".equals(subcommand)) {
                require(args, index, 1, sender);
                contexts = applyEntityModifier(server, contexts, args[index++], "as".equals(subcommand));
            } else if ("positioned".equals(subcommand)) {
                require(args, index, 1, sender);
                if ("as".equalsIgnoreCase(args[index])) {
                    require(args, index, 2, sender);
                    contexts = positionedAs(server, contexts, args[index + 1]);
                    index += 2;
                } else {
                    require(args, index, 3, sender);
                    contexts = positioned(contexts, args[index], args[index + 1], args[index + 2]);
                    index += 3;
                }
            } else if ("rotated".equals(subcommand)) {
                require(args, index, 1, sender);
                if ("as".equalsIgnoreCase(args[index])) {
                    require(args, index, 2, sender);
                    contexts = rotatedAs(server, contexts, args[index + 1]);
                    index += 2;
                } else {
                    require(args, index, 2, sender);
                    contexts = rotated(contexts, args[index], args[index + 1]);
                    index += 2;
                }
            } else if ("facing".equals(subcommand)) {
                require(args, index, 1, sender);
                if ("entity".equalsIgnoreCase(args[index])) {
                    require(args, index, 3, sender);
                    contexts = facingEntity(server, contexts, args[index + 1], args[index + 2]);
                    index += 3;
                } else {
                    require(args, index, 3, sender);
                    contexts = facingPosition(contexts, args[index], args[index + 1], args[index + 2]);
                    index += 3;
                }
            } else if ("align".equals(subcommand)) {
                require(args, index, 1, sender);
                contexts = align(contexts, args[index++]);
            } else if ("anchored".equals(subcommand)) {
                require(args, index, 1, sender);
                boolean eyes;
                if ("eyes".equalsIgnoreCase(args[index])) {
                    eyes = true;
                } else if ("feet".equalsIgnoreCase(args[index])) {
                    eyes = false;
                } else {
                    throw usage(sender);
                }
                index++;
                contexts = anchored(contexts, eyes);
            } else if ("in".equals(subcommand)) {
                require(args, index, 1, sender);
                contexts = inDimension(server, contexts, args[index++]);
            } else if ("if".equals(subcommand) || "unless".equals(subcommand)) {
                boolean positive = "if".equals(subcommand);
                require(args, index, 1, sender);
                String condition = args[index++].toLowerCase(Locale.ROOT);
                if ("entity".equals(condition)) {
                    require(args, index, 1, sender);
                    contexts = conditionEntity(server, contexts, args[index++], positive);
                } else if ("block".equals(condition)) {
                    require(args, index, 4, sender);
                    contexts = conditionBlock(contexts, args[index], args[index + 1], args[index + 2], args[index + 3], positive);
                    index += 4;
                } else if ("score".equals(condition)) {
                    require(args, index, 3, sender);
                    if ("matches".equalsIgnoreCase(args[index + 2])) {
                        require(args, index, 4, sender);
                        contexts = conditionScoreMatches(server, contexts, args[index], args[index + 1], args[index + 3], positive);
                        index += 4;
                    } else {
                        require(args, index, 5, sender);
                        contexts = conditionScoreCompare(server, contexts, args[index], args[index + 1], args[index + 2],
                                args[index + 3], args[index + 4], positive);
                        index += 5;
                    }
                } else {
                    throw new CommandException("Unsupported execute condition: " + condition);
                }
            } else {
                throw new CommandException("Unsupported execute subcommand: " + subcommand);
            }

            if (contexts.isEmpty()) {
                return;
            }
        }

        throw usage(sender);
    }

    private CommandException usage(ICommandSender sender) {
        return new CommandException(getUsage(sender));
    }

    private void require(String[] args, int index, int count, ICommandSender sender) throws CommandException {
        if (index + count > args.length) {
            throw usage(sender);
        }
    }

    private List<Entity> entities(MinecraftServer server, ExecutionContext context, String selector) throws CommandException {
        if ("@s".equals(selector)) {
            return context.entity == null
                    ? Collections.emptyList()
                    : Collections.singletonList(context.entity);
        }
        try {
            return CommandBase.getEntityList(server, context, selector);
        } catch (EntityNotFoundException ignored) {
            return Collections.emptyList();
        }
    }

    private List<ExecutionContext> applyEntityModifier(
            MinecraftServer server, List<ExecutionContext> contexts, String selector, boolean as) throws CommandException {
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            for (Entity entity : entities(server, context, selector)) {
                if (as) {
                    result.add(context.withEntity(entity));
                } else {
                    result.add(context.withWorldAndPosition(entity.world, entity.getPositionVector()));
                }
            }
        }
        return result;
    }

    private List<ExecutionContext> positionedAs(
            MinecraftServer server, List<ExecutionContext> contexts, String selector) throws CommandException {
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            for (Entity entity : entities(server, context, selector)) {
                result.add(context.withWorldAndPosition(entity.world, entity.getPositionVector()));
            }
        }
        return result;
    }

    private List<ExecutionContext> positioned(List<ExecutionContext> contexts, String x, String y, String z)
            throws CommandException {
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            Vec3d position = x.startsWith("^") || y.startsWith("^") || z.startsWith("^")
                    ? localPosition(context, x, y, z)
                    : new Vec3d(coordinate(context.position.x, x), coordinate(context.position.y, y),
                            coordinate(context.position.z, z));
            result.add(context.withPosition(position));
        }
        return result;
    }

    private List<ExecutionContext> rotated(List<ExecutionContext> contexts, String yaw, String pitch)
            throws CommandException {
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            result.add(context.withRotation((float) coordinate(context.yaw, yaw), (float) coordinate(context.pitch, pitch)));
        }
        return result;
    }

    private List<ExecutionContext> rotatedAs(
            MinecraftServer server, List<ExecutionContext> contexts, String selector) throws CommandException {
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            for (Entity entity : entities(server, context, selector)) {
                result.add(context.withRotation(entity.rotationYaw, entity.rotationPitch));
            }
        }
        return result;
    }

    private List<ExecutionContext> facingPosition(List<ExecutionContext> contexts, String x, String y, String z)
            throws CommandException {
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            Vec3d target = new Vec3d(coordinate(context.position.x, x), coordinate(context.position.y, y),
                    coordinate(context.position.z, z));
            result.add(face(context, target));
        }
        return result;
    }

    private List<ExecutionContext> facingEntity(
            MinecraftServer server, List<ExecutionContext> contexts, String selector, String anchor) throws CommandException {
        boolean eyes;
        if ("eyes".equalsIgnoreCase(anchor)) {
            eyes = true;
        } else if ("feet".equalsIgnoreCase(anchor)) {
            eyes = false;
        } else {
            throw new CommandException("Expected eyes or feet");
        }
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            for (Entity entity : entities(server, context, selector)) {
                Vec3d target = entity.getPositionVector();
                if (eyes) {
                    target = target.add(0.0D, entity.getEyeHeight(), 0.0D);
                }
                result.add(face(context, target));
            }
        }
        return result;
    }

    private ExecutionContext face(ExecutionContext context, Vec3d target) {
        Vec3d origin = context.anchorPosition();
        double dx = target.x - origin.x;
        double dy = target.y - origin.y;
        double dz = target.z - origin.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = MathHelper.wrapDegrees((float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F);
        float pitch = MathHelper.wrapDegrees((float) (-(Math.atan2(dy, horizontal) * 180.0D / Math.PI)));
        return context.withRotation(yaw, pitch);
    }

    private List<ExecutionContext> align(List<ExecutionContext> contexts, String axes) throws CommandException {
        if (axes.isEmpty() || axes.length() > 3) {
            throw new CommandException("Invalid axes: " + axes);
        }
        boolean x = false;
        boolean y = false;
        boolean z = false;
        for (char axis : axes.toCharArray()) {
            if (axis == 'x' && !x) x = true;
            else if (axis == 'y' && !y) y = true;
            else if (axis == 'z' && !z) z = true;
            else throw new CommandException("Invalid axes: " + axes);
        }
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            result.add(context.withPosition(new Vec3d(x ? Math.floor(context.position.x) : context.position.x,
                    y ? Math.floor(context.position.y) : context.position.y,
                    z ? Math.floor(context.position.z) : context.position.z)));
        }
        return result;
    }

    private List<ExecutionContext> anchored(List<ExecutionContext> contexts, boolean eyes) {
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            result.add(context.withAnchor(eyes));
        }
        return result;
    }

    private List<ExecutionContext> inDimension(
            MinecraftServer server, List<ExecutionContext> contexts, String dimensionName) throws CommandException {
        int dimension;
        if ("minecraft:overworld".equals(dimensionName) || "overworld".equals(dimensionName)) dimension = 0;
        else if ("minecraft:the_nether".equals(dimensionName) || "the_nether".equals(dimensionName) || "nether".equals(dimensionName)) dimension = -1;
        else if ("minecraft:the_end".equals(dimensionName) || "the_end".equals(dimensionName) || "end".equals(dimensionName)) dimension = 1;
        else {
            try {
                dimension = Integer.parseInt(dimensionName);
            } catch (NumberFormatException exception) {
                throw new CommandException("Unknown dimension: " + dimensionName);
            }
        }
        WorldServer world = server.getWorld(dimension);
        if (world == null) {
            throw new CommandException("Unknown dimension: " + dimensionName);
        }
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            double scale = context.world.provider.getMovementFactor() / world.provider.getMovementFactor();
            Vec3d scaledPosition = new Vec3d(context.position.x * scale, context.position.y, context.position.z * scale);
            result.add(context.withWorldAndPosition(world, scaledPosition));
        }
        return result;
    }

    private List<ExecutionContext> conditionEntity(
            MinecraftServer server, List<ExecutionContext> contexts, String selector, boolean positive) throws CommandException {
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            boolean exists = !entities(server, context, selector).isEmpty();
            if (exists == positive) result.add(context);
        }
        return result;
    }

    private List<ExecutionContext> conditionBlock(
            List<ExecutionContext> contexts, String x, String y, String z, String blockName, boolean positive)
            throws CommandException {
        Block block = Block.REGISTRY.getObject(new ResourceLocation(blockName));
        if (block == null) {
            throw new CommandException("Unknown block: " + blockName);
        }
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            BlockPos pos = new BlockPos(coordinate(context.position.x, x), coordinate(context.position.y, y),
                    coordinate(context.position.z, z));
            IBlockState state = context.world.getBlockState(pos);
            if ((state.getBlock() == block) == positive) result.add(context);
        }
        return result;
    }

    private List<ExecutionContext> conditionScoreMatches(MinecraftServer server, List<ExecutionContext> contexts,
            String holder, String objectiveName, String range, boolean positive) throws CommandException {
        int[] bounds = parseRange(range);
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            Integer score = score(server, context, holder, objectiveName);
            boolean matches = score != null && score >= bounds[0] && score <= bounds[1];
            if (matches == positive) result.add(context);
        }
        return result;
    }

    private List<ExecutionContext> conditionScoreCompare(MinecraftServer server, List<ExecutionContext> contexts,
            String holder, String objective, String operation, String sourceHolder, String sourceObjective,
            boolean positive) throws CommandException {
        if (!("=".equals(operation) || "<".equals(operation) || "<=".equals(operation)
                || ">".equals(operation) || ">=".equals(operation))) {
            throw new CommandException("Invalid score comparison: " + operation);
        }
        List<ExecutionContext> result = new ArrayList<>();
        for (ExecutionContext context : contexts) {
            Integer left = score(server, context, holder, objective);
            Integer right = score(server, context, sourceHolder, sourceObjective);
            boolean matches = left != null && right != null && compare(left, right, operation);
            if (matches == positive) result.add(context);
        }
        return result;
    }

    private Integer score(MinecraftServer server, ExecutionContext context, String holder, String objectiveName)
            throws CommandException {
        Scoreboard scoreboard = server.getWorld(0).getScoreboard();
        ScoreObjective objective = scoreboard.getObjective(objectiveName);
        if (objective == null) return null;
        String name = holder;
        if ("@s".equals(holder)) {
            if (context.entity == null) return null;
            name = context.entity.getName();
        } else if (holder.startsWith("@")) {
            List<Entity> selected = entities(server, context, holder);
            if (selected.isEmpty()) return null;
            name = selected.get(0).getName();
        }
        return scoreboard.entityHasObjective(name, objective) ? scoreboard.getOrCreateScore(name, objective).getScorePoints() : null;
    }

    private boolean compare(int left, int right, String operation) {
        if ("=".equals(operation)) return left == right;
        if ("<".equals(operation)) return left < right;
        if ("<=".equals(operation)) return left <= right;
        if (">".equals(operation)) return left > right;
        return left >= right;
    }

    private int[] parseRange(String range) throws CommandException {
        try {
            int separator = range.indexOf("..");
            if (separator < 0) {
                int value = Integer.parseInt(range);
                return new int[] {value, value};
            }
            String minimum = range.substring(0, separator);
            String maximum = range.substring(separator + 2);
            return new int[] {minimum.isEmpty() ? Integer.MIN_VALUE : Integer.parseInt(minimum),
                    maximum.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(maximum)};
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid score range: " + range);
        }
    }

    private Vec3d localPosition(ExecutionContext context, String x, String y, String z) throws CommandException {
        if (!(x.startsWith("^") && y.startsWith("^") && z.startsWith("^"))) {
            throw new CommandException("Local coordinates cannot be mixed with world coordinates");
        }
        double leftAmount = localNumber(x);
        double upAmount = localNumber(y);
        double forwardAmount = localNumber(z);
        double yaw = Math.toRadians(context.yaw);
        double pitch = Math.toRadians(context.pitch);
        Vec3d forward = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3d left = new Vec3d(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3d up = forward.crossProduct(left);
        Vec3d origin = context.anchorPosition();
        return origin.add(left.scale(leftAmount)).add(up.scale(upAmount)).add(forward.scale(forwardAmount));
    }

    private double localNumber(String value) throws CommandException {
        try {
            return value.length() == 1 ? 0.0D : Double.parseDouble(value.substring(1));
        } catch (NumberFormatException exception) {
            throw new CommandException("Invalid local coordinate: " + value);
        }
    }

    private double coordinate(double base, String value) throws CommandException {
        try {
            return value.startsWith("~")
                    ? base + (value.length() == 1 ? 0.0D : Double.parseDouble(value.substring(1)))
                    : Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new CommandException("commands.generic.num.invalid", value);
        }
    }

    private static final class ExecutionContext implements ICommandSender {
        private final ICommandSender delegate;
        private final Entity entity;
        private final World world;
        private final Vec3d position;
        private final float yaw;
        private final float pitch;
        private final boolean eyes;

        private ExecutionContext(ICommandSender delegate, Entity entity, World world, Vec3d position,
                float yaw, float pitch, boolean eyes) {
            this.delegate = delegate;
            this.entity = entity;
            this.world = world;
            this.position = position;
            this.yaw = yaw;
            this.pitch = pitch;
            this.eyes = eyes;
        }

        private ExecutionContext withEntity(Entity value) {
            return new ExecutionContext(delegate, value, world, position, yaw, pitch, eyes);
        }

        private ExecutionContext withPosition(Vec3d value) {
            return new ExecutionContext(delegate, entity, world, value, yaw, pitch, eyes);
        }

        private ExecutionContext withWorld(World value) {
            return new ExecutionContext(delegate, entity, value, position, yaw, pitch, eyes);
        }

        private ExecutionContext withWorldAndPosition(World newWorld, Vec3d value) {
            return new ExecutionContext(delegate, entity, newWorld, value, yaw, pitch, eyes);
        }

        private ExecutionContext withRotation(float newYaw, float newPitch) {
            return new ExecutionContext(delegate, entity, world, position, newYaw, newPitch, eyes);
        }

        private ExecutionContext withAnchor(boolean useEyes) {
            return new ExecutionContext(delegate, entity, world, position, yaw, pitch, useEyes);
        }

        private Vec3d anchorPosition() {
            return eyes && entity != null ? position.add(0.0D, entity.getEyeHeight(), 0.0D) : position;
        }

        @Override public String getName() { return entity == null ? delegate.getName() : entity.getName(); }
        @Override public ITextComponent getDisplayName() { return entity == null ? delegate.getDisplayName() : entity.getDisplayName(); }
        @Override public void sendMessage(ITextComponent component) { delegate.sendMessage(component); }
        @Override public boolean canUseCommand(int level, String command) { return delegate.canUseCommand(level, command); }
        @Override public BlockPos getPosition() { return new BlockPos(position); }
        @Override public Vec3d getPositionVector() { return position; }
        @Override public World getEntityWorld() { return world; }
        @Override public Entity getCommandSenderEntity() { return entity; }
        @Override public boolean sendCommandFeedback() { return delegate.sendCommandFeedback(); }
        @Override public void setCommandStat(net.minecraft.command.CommandResultStats.Type type, int amount) { delegate.setCommandStat(type, amount); }
        @Override public MinecraftServer getServer() { return delegate.getServer(); }
    }
}
