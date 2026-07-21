package com.example.restore_newer_command_syntax.command;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.Explosion;

/** Backports the modern entity damage command to Minecraft 1.12.2. */
public class DamageCommand extends CommandBase {

    @Override
    public String getName() {
        return "damage";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("dmg");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/damage <target> <amount> [type]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(new TextComponentString("用法: " + getUsage(sender)));
            return;
        }

        // Minecraft 1.12.2 requires the three-argument static overload.
        Entity target = CommandBase.getEntity(server, sender, args[0]);
        float amount = (float) CommandBase.parseDouble(args[1], 0.0D, Float.MAX_VALUE);
        String type = args.length == 3 ? args[2].toLowerCase(Locale.ROOT) : "generic";
        DamageSource source = createDamageSource(type, sender);

        if (source == null) {
            return;
        }

        if (!target.attackEntityFrom(source, amount)) {
            sender.sendMessage(new TextComponentString("无法对 " + target.getName() + " 造成伤害"));
            return;
        }

        // Brimstone leaves the victim burning after the initial magical hit.
        if ("brimstone".equals(type)) {
            target.setFire(8);
        }

        sender.sendMessage(new TextComponentString(
                "已对 " + target.getName() + " 造成 " + amount + " 点 " + type + " 伤害"));
    }

    private DamageSource createDamageSource(String type, ICommandSender sender) {
        switch (type) {
            case "generic":
                return DamageSource.GENERIC;
            case "void":
                return DamageSource.OUT_OF_WORLD;
            case "magic":
                return DamageSource.MAGIC;
            case "wither":
                return DamageSource.WITHER;
            case "explosion":
                return DamageSource.causeExplosionDamage((Explosion) null);
            case "lightning":
                return DamageSource.LIGHTNING_BOLT;
            case "player":
                Entity commandEntity = sender.getCommandSenderEntity();
                if (!(commandEntity instanceof EntityPlayer)) {
                    sender.sendMessage(new TextComponentString("player 伤害类型只能由玩家执行"));
                    return null;
                }
                return DamageSource.causePlayerDamage((EntityPlayer) commandEntity);
            case "anvil":
                return DamageSource.ANVIL;
            case "falling_block":
                return DamageSource.FALLING_BLOCK;
            case "fire":
                return DamageSource.IN_FIRE;
            case "lava":
                return DamageSource.LAVA;
            case "drown":
                return DamageSource.DROWN;
            case "starve":
                return DamageSource.STARVE;
            case "cactus":
                return DamageSource.CACTUS;
            case "fly_into_wall":
                return DamageSource.FLY_INTO_WALL;
            case "thorns":
                Entity thornsSource = sender.getCommandSenderEntity();
                if (thornsSource == null) {
                    sender.sendMessage(new TextComponentString("thorns 伤害类型需要实体执行者"));
                    return null;
                }
                return DamageSource.causeThornsDamage(thornsSource);
            case "hot_floor":
                return DamageSource.HOT_FLOOR;
            case "heiwang":
                return new DamageSource("heiwang") {
                    @Override
                    public ITextComponent getDeathMessage(EntityLivingBase entity) {
                        return new TextComponentString(entity.getName() + " 被黑王的权柄抹杀了");
                    }
                }.setDamageBypassesArmor().setDamageIsAbsolute().setDamageAllowedInCreativeMode();
            case "brimstone":
                return new DamageSource("brimstone") {
                    @Override
                    public ITextComponent getDeathMessage(EntityLivingBase entity) {
                        return new TextComponentString(entity.getName() + " 被硫火海吞没了");
                    }
                }.setMagicDamage().setDamageBypassesArmor().setFireDamage();
            case "rot":
                return new DamageSource("rot") {
                    @Override
                    public ITextComponent getDeathMessage(EntityLivingBase entity) {
                        return new TextComponentString(entity.getName() + " 在腐朽中崩解");
                    }
                }.setDamageBypassesArmor();
            case "apocalypse":
                return new DamageSource("apocalypse") {
                    @Override
                    public ITextComponent getDeathMessage(EntityLivingBase entity) {
                        return new TextComponentString(entity.getName() + " 被抹除");
                    }
                }.setDamageBypassesArmor().setDamageIsAbsolute().setDamageAllowedInCreativeMode();
            default:
                sender.sendMessage(new TextComponentString("未知的伤害类型: " + type));
                return null;
        }
    }
}
