package com.onarandombox.MultiverseCore;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Giant;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Pig;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Squid;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import com.iConomy.iConomy;
import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;
import com.onarandombox.MultiverseCore.command.CommandManager;
import com.onarandombox.MultiverseCore.command.QueuedCommand;
import com.onarandombox.MultiverseCore.command.commands.*;
import com.onarandombox.MultiverseCore.configuration.DefaultConfiguration;
import com.onarandombox.utils.DebugLog;
import com.onarandombox.utils.Messaging;
import com.onarandombox.utils.UpdateChecker;

public class MultiverseCore extends JavaPlugin {
    
    // Useless stuff to keep us going.
    private static final Logger log = Logger.getLogger("Minecraft");
    private static DebugLog debugLog;
    
    // Debug Mode
    private boolean debug;
    
    // Setup our Map for our Commands using the CommandHandler.
    private Map<String, MVCommandHandler> commands = new HashMap<String, MVCommandHandler>();
    private CommandManager commandManager = new CommandManager();
    
    private final String tag = "[Multiverse-Core]";
    
    // Messaging
    private Messaging messaging = new Messaging();
    
    // Multiverse Permissions Handler
    public MVPermissions ph = new MVPermissions(this);
    
    // Permissions Handler
    public static PermissionHandler Permissions = null;
    
    // iConomy Handler
    public static iConomy iConomy = null;
    public static boolean useiConomy = false;
    
    // Configurations
    public Configuration configMV = null;
    public Configuration configWorlds = null;
    
    // Setup the block/player/entity listener.
    private MVPlayerListener playerListener = new MVPlayerListener(this);;
    @SuppressWarnings("unused")
    private MVBlockListener blockListener = new MVBlockListener(this);
    private MVEntityListener entityListener = new MVEntityListener(this);
    private MVPluginListener pluginListener = new MVPluginListener(this);
    
    public UpdateChecker updateCheck;
    
    // HashMap to contain all the Worlds which this Plugin will manage.
    public HashMap<String, MVWorld> worlds = new HashMap<String, MVWorld>();
    
    // HashMap to contain all custom generators. Plugins will have to register!
    public HashMap<String, ChunkGenerator> worldGenerators = new HashMap<String, ChunkGenerator>();
    
    // HashMap to contain information relating to the Players.
    public HashMap<String, MVPlayerSession> playerSessions = new HashMap<String, MVPlayerSession>();
    
    // List to hold commands that require approval
    public List<QueuedCommand> queuedCommands = new ArrayList<QueuedCommand>();
    
    @Override
    public void onLoad() {
        // Create our DataFolder
        getDataFolder().mkdirs();
        // Setup our Debug Log
        debugLog = new DebugLog("Multiverse-Core", getDataFolder() + File.separator + "debug.log");
        
        // Setup & Load our Configuration files.
        loadConfigs();
    }
    
    @Override
    public void onEnable() {
        // Output a little snippet to show it's enabled.
        log(Level.INFO, "- Version " + this.getDescription().getVersion() + " Enabled - By " + getAuthors());
        
        // Setup all the Events the plugin needs to Monitor.
        registerEvents();
        // Setup Permissions, we'll do an initial check for the Permissions plugin then fall back on isOP().
        setupPermissions();
        // Setup iConomy.
        setupEconomy();
        // Call the Function to assign all the Commands to their Class.
        registerCommands();
        
        // Start the Update Checker
        // updateCheck = new UpdateChecker(this.getDescription().getName(), this.getDescription().getVersion());
        
        // Call the Function to load all the Worlds and setup the HashMap
        // When called with null, it tries to load ALL
        // this function will be called every time a plugin registers a new envtype with MV
        loadWorlds(null);
        
        // Purge Worlds of old Monsters/Animals which don't adhere to the setup.
        purgeWorlds();
    }
    
    /**
     * Function to Register all the Events needed.
     */
    private void registerEvents() {
        PluginManager pm = getServer().getPluginManager();
        // pm.registerEvent(Event.Type.PLAYER_MOVE, playerListener, Priority.Highest, this); // Low so it acts above any other.
        pm.registerEvent(Event.Type.PLAYER_TELEPORT, playerListener, Priority.Highest, this); // Cancel Teleports if needed.
        pm.registerEvent(Event.Type.PLAYER_LOGIN, playerListener, Priority.Normal, this); // To create the Player Session
        pm.registerEvent(Event.Type.PLAYER_QUIT, playerListener, Priority.Normal, this); // To remove Player Sessions
        pm.registerEvent(Event.Type.PLAYER_KICK, playerListener, Priority.Highest, this);
        pm.registerEvent(Event.Type.PLAYER_RESPAWN, playerListener, Priority.Normal, this);
        
        pm.registerEvent(Event.Type.ENTITY_DAMAGE, entityListener, Priority.Normal, this); // To Allow/Disallow PVP as well as EnableHealth.
        pm.registerEvent(Event.Type.CREATURE_SPAWN, entityListener, Priority.Normal, this); // To prevent all or certain animals/monsters from spawning.
        
        pm.registerEvent(Event.Type.PLUGIN_ENABLE, pluginListener, Priority.Monitor, this);
        pm.registerEvent(Event.Type.PLUGIN_DISABLE, pluginListener, Priority.Monitor, this);
        
        // pm.registerEvent(Event.Type.BLOCK_BREAK, blockListener, Priority.Normal, this); // To prevent Blocks being destroyed.
        // pm.registerEvent(Event.Type.BLOCK_PLACED, blockListener, Priority.Normal, this); // To prevent Blocks being placed.
        // pm.registerEvent(Event.Type.ENTITY_EXPLODE, entityListener, Priority.Normal, this); // Try to prevent Ghasts from blowing up structures.
        // pm.registerEvent(Event.Type.EXPLOSION_PRIMED, entityListener, Priority.Normal, this); // Try to prevent Ghasts from blowing up structures.
    }
    
    /**
     * Check for Permissions plugin and then setup our own Permissions Handler.
     */
    private void setupPermissions() {
        Plugin p = this.getServer().getPluginManager().getPlugin("Permissions");
        
        if (MultiverseCore.Permissions == null) {
            if (p != null && p.isEnabled()) {
                MultiverseCore.Permissions = ((Permissions) p).getHandler();
                log(Level.INFO, "- Attached to Permissions");
            }
        }
    }
    
    /**
     * Check for the iConomy plugin and set it up accordingly.
     */
    private void setupEconomy() {
        Plugin test = this.getServer().getPluginManager().getPlugin("iConomy");
        
        if (MultiverseCore.iConomy == null) {
            if (test != null) {
                MultiverseCore.iConomy = (iConomy) test;
            }
        }
    }
    
    /**
     * Load the Configuration files OR create the default config files.
     */
    public void loadConfigs() {
        // Call the defaultConfiguration class to create the config files if they don't already exist.
        new DefaultConfiguration(getDataFolder(), "config.yml");
        new DefaultConfiguration(getDataFolder(), "worlds.yml");
        
        // Now grab the Configuration Files.
        configMV = new Configuration(new File(getDataFolder(), "config.yml"));
        configWorlds = new Configuration(new File(getDataFolder(), "worlds.yml"));
        
        // Now attempt to Load the configurations.
        try {
            configMV.load();
            log(Level.INFO, "- Multiverse Config -- Loaded");
        } catch (Exception e) {
            log(Level.INFO, "- Failed to load config.yml");
        }
        
        try {
            configWorlds.load();
            log(Level.INFO, "- World Config -- Loaded");
        } catch (Exception e) {
            log(Level.INFO, "- Failed to load worlds.yml");
        }
        
        // Setup the Debug option, we'll default to false because this option will not be in the default config.
        this.debug = configMV.getBoolean("debug", false);
    }
    
    /**
     * Purge the Worlds of Entities that are disallowed.
     */
    private void purgeWorlds() {
        if (worlds.size() <= 0)
            return;
        
        // TODO: Need a better method than this... too messy and atm it's not complete.
        
        Set<String> worldKeys = worlds.keySet();
        for (String key : worldKeys) {
            World world = getServer().getWorld(key);
            if (world == null)
                continue;
            MVWorld mvworld = worlds.get(key);
            List<String> monsters = mvworld.monsterList;
            List<String> animals = mvworld.animalList;
            System.out.print("Monster Size:" + monsters.size() + " - " + "Animal Size: " + animals.size());
            for (Entity e : world.getEntities()) {
                // Check against Monsters
                if (e instanceof Creeper || e instanceof Skeleton || e instanceof Spider || e instanceof Zombie || e instanceof Ghast || e instanceof PigZombie || e instanceof Giant || e instanceof Slime || e instanceof Monster) {
                    // If Monsters are disabled and there's no exceptions we can simply remove them.
                    if (mvworld.monsters == false && !(monsters.size() > 0)) {
                        e.remove();
                        continue;
                    }
                    // If monsters are enabled and there's no exceptions we can continue to the next set.
                    if (mvworld.monsters == true && !(monsters.size() > 0)) {
                        continue;
                    }
                    String creature = e.toString().replaceAll("Craft", "");
                    if (monsters.contains(creature.toUpperCase())) {
                        if (mvworld.monsters) {
                            System.out.print(creature + " - Removed");
                            e.remove();
                            continue;
                        }
                    }
                }
                // Check against Animals
                if (e instanceof Chicken || e instanceof Cow || e instanceof Sheep || e instanceof Pig || e instanceof Squid || e instanceof Animals) {
                    // If Monsters are disabled and there's no exceptions we can simply remove them.
                    if (mvworld.animals == false && !(animals.size() > 0)) {
                        e.remove();
                        continue;
                    }
                    // If monsters are enabled and there's no exceptions we can continue to the next set.
                    if (mvworld.animals == true && !(animals.size() > 0)) {
                        continue;
                    }
                    String creature = e.toString().replaceAll("Craft", "");
                    if (animals.contains(creature.toUpperCase())) {
                        if (mvworld.animals) {
                            e.remove();
                            continue;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Register Multiverse-Core commands to DThielke's Command Manager.
     */
    private void registerCommands() {
        // Page 1
        commandManager.addCommand(new HelpCommand(this));
        commandManager.addCommand(new CoordCommand(this));
        commandManager.addCommand(new TeleportCommand(this));
        commandManager.addCommand(new ListCommand(this));
        commandManager.addCommand(new WhoCommand(this));
        commandManager.addCommand(new SetSpawnCommand(this));
        commandManager.addCommand(new CreateCommand(this));
        commandManager.addCommand(new ImportCommand(this));
        commandManager.addCommand(new SpawnCommand(this));
        commandManager.addCommand(new RemoveCommand(this));
        commandManager.addCommand(new DeleteCommand(this));
        commandManager.addCommand(new UnloadCommand(this));
        commandManager.addCommand(new ConfirmCommand(this));
        commandManager.addCommand(new InfoCommand(this));
        commandManager.addCommand(new ReloadCommand(this));
        commandManager.addCommand(new ModifyCommand(this));
    }
    
    /**
     * Load the Worlds & Settings from the configuration file.
     */
    public void loadWorlds(String filter) {
        // Basic Counter to count how many Worlds we are loading.
        int count = 0;
        // Grab all the Worlds from the Config.
        List<String> worldKeys = configWorlds.getKeys("worlds");
        
        // Check that the list is not null.
        if (worldKeys != null) {
            for (String worldKey : worldKeys) {
                // Check if the World is already loaded within the Plugin.
                if (worlds.containsKey(worldKey)) {
                    continue;
                }
                // Grab the initial values from the config file.
                String environment = configWorlds.getString("worlds." + worldKey + ".environment", "NORMAL"); // Grab the Environment as a String.
                String seedString = configWorlds.getString("worlds." + worldKey + ".seed", "");
                
                log(Level.INFO, "Loading World & Settings - '" + worldKey + "' - " + environment);
                
                boolean isStockWorldType = this.getEnvFromString(environment) != null;
                
                // If we don't have a seed then add a standard World, else add the world with the Seed.
                if (filter == null && isStockWorldType) {
                    addWorld(worldKey, environment, seedString);
                    // Increment the world count
                    count++;
                } else if (filter != null && filter.equalsIgnoreCase(environment)) {
                    addWorld(worldKey, environment, seedString);
                    // Increment the world count
                    count++;
                } else {
                    log(Level.INFO, "World " + worldKey + " was not loaded YET. Multiverse is waiting for a plugin to handle the environment type " + environment);
                }
            }
        }
        
        // Ensure that the worlds created by the default server were loaded into MV, useful for first time runs
        count += loadDefaultWorlds();
        
        // Simple Output to the Console to show how many Worlds were loaded.
        log(Level.INFO, count + " - World(s) loaded.");
    }
    
    /**
     * 
     * @return
     */
    private int loadDefaultWorlds() {
        int additonalWorldsLoaded = 0;
        // Load the default world:
        World world = this.getServer().getWorlds().get(0);
        if (!this.worlds.containsKey(world.getName())) {
            log.info("Loading World & Settings - '" + world.getName() + "' - " + world.getEnvironment());
            addWorld(world.getName(), "NORMAL", "");
            additonalWorldsLoaded++;
        }
        
        // This next one could be null if they have it disabled in server.props
        World world_nether = this.getServer().getWorld(world.getName() + "_nether");
        if (world_nether != null && !this.worlds.containsKey(world_nether.getName())) {
            log.info("Loading World & Settings - '" + world.getName() + "' - " + world_nether.getEnvironment());
            addWorld(world_nether.getName(), "NORMAL", "");
            additonalWorldsLoaded++;
        }
        
        return additonalWorldsLoaded;
    }
    
    /**
     * Get the worlds Seed.
     * 
     * @param w World
     * @return Seed
     */
    public long getSeed(World w) {
        return ((CraftWorld) w).getHandle().worldData.b();
    }
    
    /**
     * Add a new World to the Multiverse Setup.
     * 
     * Isn't there a prettier way to do this??!!?!?!
     * 
     * @param name World Name
     * @param environment Environment Type
     */
    public boolean addWorld(String name, String envString, String seedString) {
        
        Long seed = null;
        if (seedString.length() > 0) {
            try {
                seed = Long.parseLong(seedString);
            } catch (NumberFormatException numberformatexception) {
                seed = (long) seedString.hashCode();
            }
        }
        
        Environment env = getEnvFromString(envString);
        ChunkGenerator customGenerator = getChunkGenFromEnv(envString);
        if (env == null) {
            env = Environment.NORMAL;
            // If the env was null, ie. not a built in one.
            // AND the customGenerator is null, then someone
            // screwed up... return false!
            if (customGenerator == null) {
                return false;
            }
        }
        
        if (seed != null) {
            if (customGenerator != null) {
                World world = getServer().createWorld(name, env, seed, customGenerator);
                worlds.put(name, new MVWorld(world, configWorlds, this, seed, envString)); // Place the World into the HashMap.
                log(Level.INFO, "Loading World & Settings - '" + name + "' - " + envString + " with seed: " + seed);
            } else {
                World world = getServer().createWorld(name, env, seed);
                worlds.put(name, new MVWorld(world, configWorlds, this, seed, envString)); // Place the World into the HashMap.
                log(Level.INFO, "Loading World & Settings - '" + name + "' - " + env + " with seed: " + seed);
            }
        } else {
            if (customGenerator != null) {
                World world = getServer().createWorld(name, env, customGenerator);
                worlds.put(name, new MVWorld(world, configWorlds, this, null, envString)); // Place the World into the HashMap.
                log(Level.INFO, "Loading World & Settings - '" + name + "' - " + envString);
            } else {
                World world = getServer().createWorld(name, env);
                worlds.put(name, new MVWorld(world, configWorlds, this, null, envString)); // Place the World into the HashMap.
                log(Level.INFO, "Loading World & Settings - '" + name + "' - " + env);
            }
        }
        return true;
        
    }
    
    /**
     * Remove the world from the Multiverse list
     * 
     * @param name The name of the world to remove
     * @return True if success, false if failure.
     */
    public boolean unloadWorld(String name) {
        if (worlds.containsKey(name)) {
            worlds.remove(name);
            return true;
        }
        return false;
    }
    
    /**
     * Remove the world from the Multiverse list and from the config
     * 
     * @param name The name of the world to remove
     * @return True if success, false if failure.
     */
    public boolean removeWorld(String name) {
        unloadWorld(name);
        configWorlds.removeProperty("worlds." + name);
        configWorlds.save();
        return false;
    }
    
    /**
     * Remove the world from the Multiverse list, from the config and deletes the folder
     * 
     * @param name The name of the world to remove
     * @return True if success, false if failure.
     */
    public boolean deleteWorld(String name) {
        unloadWorld(name);
        removeWorld(name);
        if (getServer().unloadWorld(name, false)) {
            return deleteFolder(new File(name));
        }
        return false;
    }
    
    /**
     * Delete a folder Courtesy of: lithium3141
     * 
     * @param file The folder to delete
     * @return true if success
     */
    private boolean deleteFolder(File file) {
        if (file.exists()) {
            // If the file exists, and it has more than one file in it.
            if (file.isDirectory()) {
                for (File f : file.listFiles()) {
                    if (!this.deleteFolder(f)) {
                        return false;
                    }
                }
            }
            file.delete();
            return !file.exists();
        } else {
            return false;
        }
    }
    
    /**
     * What happens when the plugin gets disabled...
     */
    @Override
    public void onDisable() {
        debugLog.close();
        MultiverseCore.Permissions = null;
        log(Level.INFO, "- Disabled");
    }
    
    /**
     * Grab the players session if one exists, otherwise create a session then return it.
     * 
     * @param player
     * @return
     */
    public MVPlayerSession getPlayerSession(Player player) {
        if (playerSessions.containsKey(player.getName())) {
            return playerSessions.get(player.getName());
        } else {
            playerSessions.put(player.getName(), new MVPlayerSession(player, configMV, this));
            return playerSessions.get(player.getName());
        }
    }
    
    /**
     * Grab and return the Teleport class.
     * 
     * @return
     */
    public MVTeleport getTeleporter() {
        return new MVTeleport(this);
    }
    
    /**
     * Grab the iConomy setup.
     * 
     * @return
     */
    public static iConomy getiConomy() {
        return iConomy;
    }
    
    /**
     * Grab the Permissions Handler for MultiVerse
     */
    public MVPermissions getPermissions() {
        return this.ph;
    }
    
    /**
     * onCommand
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        if (this.isEnabled() == false) {
            sender.sendMessage("This plugin is Disabled!");
            return true;
        }
        return commandManager.dispatch(sender, command, commandLabel, args);
    }
    
    /**
     * Print messages to the server Log as well as to our DebugLog. 'debugLog' is used to seperate Heroes information from the Servers Log Output.
     * 
     * @param level
     * @param msg
     */
    public void log(Level level, String msg) {
        log.log(level, "[Multiverse-Core] " + msg);
        debugLog.log(level, "[Multiverse-Core] " + msg);
    }
    
    /**
     * Print messages to the Debug Log, if the servers in Debug Mode then we also wan't to print the messages to the standard Server Console.
     * 
     * @param level
     * @param msg
     */
    public void debugLog(Level level, String msg) {
        if (this.debug) {
            log.log(level, "[Debug] " + msg);
        }
        debugLog.log(level, "[Debug] " + msg);
    }
    
    public Messaging getMessaging() {
        return messaging;
    }
    
    /**
     * Parse the Authors Array into a readable String with ',' and 'and'.
     * 
     * @return
     */
    private String getAuthors() {
        String authors = "";
        ArrayList<String> auths = this.getDescription().getAuthors();
        
        if (auths.size() == 1) {
            return auths.get(0);
        }
        
        for (int i = 0; i < auths.size(); i++) {
            if (i == this.getDescription().getAuthors().size() - 1) {
                authors += " and " + this.getDescription().getAuthors().get(i);
            } else {
                authors += ", " + this.getDescription().getAuthors().get(i);
            }
        }
        return authors.substring(2);
    }
    
    public CommandManager getCommandManager() {
        return commandManager;
    }
    
    public String getTag() {
        return tag;
    }
    
    /**
     * This code should get moved somewhere more appropriate, but for now, it's here.
     * 
     * @param env
     * @return
     */
    public Environment getEnvFromString(String env) {
        // Don't reference the enum directly as there aren't that many, and we can be more forgiving to users this way
        if (env.equalsIgnoreCase("HELL") || env.equalsIgnoreCase("NETHER"))
            env = "NETHER";
        
        if (env.equalsIgnoreCase("SKYLANDS") || env.equalsIgnoreCase("SKYLAND") || env.equalsIgnoreCase("STARWARS"))
            env = "SKYLANDS";
        
        if (env.equalsIgnoreCase("NORMAL") || env.equalsIgnoreCase("WORLD"))
            env = "NORMAL";
        
        try {
            return Environment.valueOf(env);
        } catch (IllegalArgumentException e) {
            // Sender will be null on loadWorlds
            return null;
            // TODO: Show the player the mvenvironments command.
        }
    }
    
    public ChunkGenerator getChunkGenFromEnv(String env) {
        if (worldGenerators.containsKey(env)) {
            return worldGenerators.get(env);
        }
        return null;
    }
    
    public boolean registerEnvType(String name, ChunkGenerator generator) {
        if (this.worldGenerators.containsKey(name)) {
            return false;
        }
        this.worldGenerators.put(name, generator);
        this.loadWorlds(name);
        return true;
    }
    
    // TODO: Find out where to put these next 3 methods! I just stuck them here for now --FF
    
    /**
     * 
     */
    public void queueCommand(CommandSender sender, String commandName, String methodName, String[] args, Class<?>[] paramTypes, String success, String fail) {
        cancelQueuedCommand(sender);
        queuedCommands.add(new QueuedCommand(methodName, args, paramTypes, sender, Calendar.getInstance(), this, success, fail));
        sender.sendMessage("The command " + ChatColor.RED + commandName + ChatColor.WHITE + " has been halted due to the fact that it could break something!");
        sender.sendMessage("If you still wish to execute " + ChatColor.RED + commandName + ChatColor.WHITE + ", please type: " + ChatColor.GREEN + "/mvconfirm " + ChatColor.GOLD + "YES");
        sender.sendMessage(ChatColor.GREEN + "/mvconfirm" + ChatColor.WHITE + " will only be available for 10 seconds.");
    }
    
    /**
     * Tries to fire off the command
     * 
     * @param sender
     * @return
     */
    public boolean confirmQueuedCommand(CommandSender sender) {
        for (QueuedCommand com : queuedCommands) {
            if (com.getSender().equals(sender)) {
                if (com.execute()) {
                    sender.sendMessage(com.getSuccess());
                    return true;
                } else {
                    sender.sendMessage(com.getFail());
                    return false;
                }
            }
        }
        return false;
    }
    
    /**
     * Cancels(invalidates) a command that has been requested. This is called when a user types something other than 'yes' or when they try to queue a second command Queuing a second command will delete the first command entirely.
     * 
     * @param sender
     */
    public void cancelQueuedCommand(CommandSender sender) {
        QueuedCommand c = null;
        for (QueuedCommand com : queuedCommands) {
            if (com.getSender().equals(sender)) {
                c = com;
            }
        }
        if (c != null) {
            // Each person is allowed at most one queued command.
            queuedCommands.remove(c);
        }
    }
}