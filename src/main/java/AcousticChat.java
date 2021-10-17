import mineverse.Aust1n46.chat.MineverseChat;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.command.chat.Venturechat;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
			Credits to
	https://github.com/cs-au-dk/dk.brics.automaton
	https://github.com/mifmif/Generex
*/

public class AcousticChat extends JavaPlugin implements Listener {
	public static Random r;
	public HashMap<Player, Long> cooldown;
	public static FileConfiguration config;
	MineverseChat ventureChat;

	private static Logger log = null;
	
	@Override
	public void onEnable() {
		this.ventureChat
				= (MineverseChat) getServer().getPluginManager().getPlugin("VentureChat");
		r = new Random();
		log = getLogger();

		AcousticChat.config = getConfig();

		cooldown = new HashMap<Player, Long>();//make sure this is before registerEvents
		getServer().getPluginManager().registerEvents(this, this);
		getCommand("ac").setExecutor(new AC_commands(this));
		saveResource("config.yml", false);
		
		log.info("Max distance set to " + getConfig().getInt("maxDistance"));
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cooldown.remove(player);
    }
	
	private String applyFormat(String format, String username, String message) {
		return format.replaceFirst(Pattern.quote("%1$s"), Matcher.quoteReplacement(username))
				.replaceFirst(Pattern.quote("%2$s"), Matcher.quoteReplacement(message));
	}
	
	@EventHandler (priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {

		// time to check if we should do anything or not.
		MineverseChatPlayer MChatplayer =
			MineverseChatAPI.getMineverseChatPlayer(event.getPlayer().getUniqueId());

		String VChatChannel = getConfig().getString("ventureChatChannel");
		if(MChatplayer.getCurrentChannel().getName().equalsIgnoreCase(VChatChannel)) {
			event.setCancelled(true);
		} else {
			return;
		}

		//region Variable Setting Area
		Player sender = event.getPlayer();
		String mformat = event.getFormat();
		String senderName = sender.getDisplayName();
		String messageText = event.getMessage();
		
		String message = applyFormat(mformat, senderName, messageText);
		
		getServer().getConsoleSender().sendMessage(message);
		sender.sendMessage(message);
		//TODO: colour this in a way to indicate who heard it
		
		@SuppressWarnings("unchecked")
		List<Player> players = (List<Player>) Bukkit.getOnlinePlayers();

		//endregion

		boolean sentOne = false;
		for (Player p : players) {
			//message already sent to themselves
			if (p.getUniqueId().equals(sender.getUniqueId())) {
				continue;
			}

			//region OP-HEAR CHECKS
			if ((sender.isOp() && getConfig().getBoolean("opsAlwaysHeard"))) {
				p.sendMessage(message);
				sentOne = true;
				continue;
			} else if (p.isOp() && getConfig().getBoolean("opsHearEverything")) {
				p.sendMessage(message);
				sentOne = sentOne || !getConfig().getBoolean("messageWhenNoListeners.notifyIfOpHears");
				continue;
			}
			if (p.getWorld() != sender.getWorld()) continue;
			//endregion

			double entropy = Math.calcEntropy(sender, p, getConfig());
			if (entropy > 1.0) continue;
			if (getConfig().getBoolean("hideSender.enabled") && getConfig().getDouble("hideSender.minEntropy") < entropy)
				senderName = Math.fixColor(getConfig().getString("hideSender.senderName"));
			p.sendMessage(applyFormat(mformat, senderName, Math.addNoise(messageText, entropy)));
			sentOne = true;
		}


		if (!getConfig().getBoolean("messageWhenNoListeners.enabled") || sentOne) return;
		
		int cooltime = getConfig().getInt("messageWhenNoListeners.cooldown") * 1000;
		if (cooltime != 0 &&
				cooldown.containsKey(sender) &&
				System.currentTimeMillis() - cooldown.get(sender) < cooltime)
			return;

		sender.sendMessage(Math.fixColor(getConfig().getString("messageWhenNoListeners.message")));
		
		if (cooltime == 0) return;
		cooldown.put(sender, System.currentTimeMillis());
	}


}