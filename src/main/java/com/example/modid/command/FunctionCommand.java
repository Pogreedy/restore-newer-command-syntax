package com.example.restore_newer_command_syntax.command;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * Simple high-version-style /function backport for Forge 1.12.2.
 *
 * <p>Functions are loaded from:</p>
 * <pre>
 * world/datapacks/any_pack/data/namespace/functions/name.mcfunction
 * </pre>
 */
public class FunctionCommand extends CommandBase {

    @Override
    public String getName() {
        return "function";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/function <namespace>:<name>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1) {
            sender.sendMessage(new TextComponentString("用法: " + getUsage(sender)));
            return;
        }

        String functionId = args[0];
        int separator = functionId.indexOf(':');
        if (separator <= 0 || separator == functionId.length() - 1 || functionId.indexOf(':', separator + 1) >= 0) {
            sender.sendMessage(new TextComponentString("无效的函数 ID: " + functionId));
            return;
        }

        String namespace = functionId.substring(0, separator);
        String functionName = functionId.substring(separator + 1);
        if (!isValidNamespace(namespace) || !isValidFunctionName(functionName)) {
            sender.sendMessage(new TextComponentString("无效的函数 ID: " + functionId));
            return;
        }

        File functionFile;
        try {
            functionFile = findFunction(server, namespace, functionName);
        } catch (IOException exception) {
            throw new CommandException("读取函数失败: " + exception.getMessage());
        }

        if (functionFile == null) {
            sender.sendMessage(new TextComponentString("未知的函数: " + functionId));
            return;
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(functionFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new CommandException("读取函数失败: " + functionId + ": " + exception.getMessage());
        }

        int executedLines = 0;
        int lineNumber = 0;
        for (String rawLine : lines) {
            lineNumber++;
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            try {
                server.getCommandManager().executeCommand(sender, line);
                executedLines++;
            } catch (RuntimeException exception) {
                throw new CommandException(
                        "函数 " + functionId + " 第 " + lineNumber + " 行执行失败: " + exception.getMessage());
            }
        }

        sender.sendMessage(new TextComponentString("已执行函数 " + functionId + "，共运行 " + executedLines + " 条指令"));
    }

    private File findFunction(MinecraftServer server, String namespace, String functionName) throws IOException {
        File worldDirectory = server.getWorld(0).getSaveHandler().getWorldDirectory().getCanonicalFile();
        File datapacksDirectory = new File(worldDirectory, "datapacks");
        File[] packs = datapacksDirectory.listFiles(File::isDirectory);
        if (packs == null) {
            return null;
        }

        List<File> sortedPacks = new java.util.ArrayList<>();
        Collections.addAll(sortedPacks, packs);
        sortedPacks.sort(Comparator.comparing(File::getName));

        String relativeName = functionName.replace('/', File.separatorChar) + ".mcfunction";
        for (File pack : sortedPacks) {
            File packRoot = pack.getCanonicalFile();
            File functionsRoot = new File(
                    new File(new File(packRoot, "data"), namespace), "functions").getCanonicalFile();
            File candidate = new File(functionsRoot, relativeName).getCanonicalFile();

            if (candidate.toPath().startsWith(functionsRoot.toPath()) && candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isValidNamespace(String namespace) {
        return namespace.matches("[a-z0-9_.-]+");
    }

    private boolean isValidFunctionName(String functionName) {
        return !functionName.contains("..")
                && !functionName.startsWith("/")
                && !functionName.startsWith("\\")
                && functionName.matches("[a-z0-9_./-]+");
    }
}
