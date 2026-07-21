package com.example.restore_newer_command_syntax;

import com.example.restore_newer_command_syntax.command.CommandExecute;
import com.example.restore_newer_command_syntax.command.DamageCommand;
import com.example.restore_newer_command_syntax.command.FunctionCommand;
import com.example.restore_newer_command_syntax.proxy.IProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION)
public class ExampleMod {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    @SidedProxy(modId = Tags.MOD_ID, clientSide = "com.example.restore_newer_command_syntax.proxy.ClientProxy", serverSide = "com.example.restore_newer_command_syntax.proxy.CommonProxy")
    public static IProxy proxy;
    /**
     * <a href="https://cleanroommc.com/wiki/forge-mod-development/event#overview">
     *     Take a look at how many FMLStateEvents you can listen to via the @Mod.EventHandler annotation here
     * </a>
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Hello From {}!", Tags.MOD_NAME);
        LOGGER.info("Proxy is {}", proxy);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandExecute());
        event.registerServerCommand(new DamageCommand());
        event.registerServerCommand(new FunctionCommand());
    }

}
