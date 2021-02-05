package acousticChat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.Random;
import java.util.HashMap;
import java.util.regex.Pattern;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class AcousticChat extends JavaPlugin implements Listener {
	public static Random r;
	public HashMap<Player, Long> cooldown;
	public static final char vowels[] = {'a', 'e', 'i', 'o', 'u'};
	
	private static Logger log = null;
	
	@Override
	public void onEnable() {
		r = new Random();
		log = getLogger();

		cooldown = new HashMap<Player, Long>();//make sure this is before registerevents
		getServer().getPluginManager().registerEvents(this, this);
		getCommand("ac").setExecutor(new AC_commands(this));
		saveResource("config.yml", false);
		
		log.info("Hello from Cabbache");
		log.info("Max distance set to " + getConfig().getInt("maxDistance"));
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cooldown.remove(player);
    }
	
	private String applyFormat(String format, String username, String message) {
		return format.replaceAll(Pattern.quote("%1$s"), username)
				.replaceAll(Pattern.quote("%2$s"), message);
	}
	
	@EventHandler (priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
		event.setCancelled(true);//from now on it will be handled manually
		
		Player sender = event.getPlayer();
		String mformat = event.getFormat();
		String senderName = sender.getDisplayName();
		String messageText = event.getMessage();
		
		String message = applyFormat(mformat, senderName, messageText);

		log.info(message);
		
		sender.sendMessage(message);
		
		boolean sentOne = false;
		@SuppressWarnings("unchecked")
		List<Player> players = (List<Player>) Bukkit.getOnlinePlayers();
		
		for (int i = 0;i < players.size();i++) {
			Player p = players.get(i);
			if (p.getUniqueId().equals(sender.getUniqueId())) continue; //message already sent to themselves
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
			
			double entropy = calcEntropy(sender, p);
			if (entropy > 1.0) continue;
			if (getConfig().getBoolean("hideSender.enabled") && getConfig().getDouble("hideSender.minEntropy") < entropy)
				senderName = fixColor(getConfig().getString("hideSender.senderName"));
			p.sendMessage(applyFormat(mformat, senderName, addNoise(messageText, entropy)));
			sentOne = true;
		}
		if (!getConfig().getBoolean("messageWhenNoListeners.enabled") || sentOne) return;
		
		int cooltime = getConfig().getInt("messageWhenNoListeners.cooldown") * 1000;
		if (cooltime != 0 &&
				cooldown.containsKey(sender) &&
				System.currentTimeMillis() - cooldown.get(sender) < cooltime)
			return;

		sender.sendMessage(fixColor(getConfig().getString("messageWhenNoListeners.message")));
		
		if (cooltime == 0) return;
		cooldown.put(sender, System.currentTimeMillis());
	}
	
	private boolean isVowel(char c) {
		for (char n : vowels) if (n == Character.toLowerCase(c)) return true;
		return false;
	}
	
	private String fixColor(String text) {
		return ChatColor.translateAlternateColorCodes('&', text);
	}
	
	private double calcEntropy(Player sender, Player receiver) {
		double distance = sender.getLocation().distance(receiver.getLocation());
		double percent = distance / getConfig().getInt("maxDistance");
		double fluctuation = getConfig().getDouble("entropyRandomFluctuation");
		double entropy = percent + (fluctuation*2*r.nextDouble() - fluctuation);
		entropy = Math.min(Math.max(0, entropy), 1.0); //to make sure it's still in range
		entropy = getConfig().getBoolean("obeyInverseSquareLaw") ? Math.pow(entropy, 2):entropy;
		
		//increase entropy if there are blocks in the way
		if (getConfig().getBoolean("lineOfSight.enabled") && !sender.hasLineOfSight(receiver))
			entropy += getConfig().getDouble("lineOfSight.weighting");
		
		if (!getConfig().getBoolean("senderFacing.enabled")) return entropy;
		
		//increase entropy if players are not facing each other
		Vector senderFacing = sender.getEyeLocation().getDirection().normalize();
		Vector receiverDirection = receiver.getLocation().toVector().subtract(sender.getLocation().toVector()).normalize();
		
		double anglenoise = senderFacing.angle(receiverDirection) / Math.PI;
		entropy += anglenoise * getConfig().getDouble("senderFacing.weighting");
		//Pi radians is 180 degrees which should be max angle between vectors
		return entropy;
	}

	//r.nextDouble() is between 0 and 1
	private String addNoise(String original, double entropy) {
		final double vowelConsonantRatio = 3.7;//how much more likely than vowels are consonant letters to be misheard
		final double asteriskOmmission = 0.3;//probability that an asterisk gets removed
		final double asteriskCloning = 0.4;//probability that an asterisk is replaced by adjacent letter
		//the rest is probability it remains asterisk
		
		char chars[] = original.toCharArray();
		int num_asterisk = (int) (entropy * chars.length);
		
		ArrayList<Integer> consIndexes = new ArrayList<Integer>();
		ArrayList<Integer> vowelIndexes = new ArrayList<Integer>();
		for (int i = 0;i < chars.length;i++) {
			if (isVowel(chars[i])) vowelIndexes.add(i);
			else consIndexes.add(i);
		}
		
		while (num_asterisk-- > 0) {
			ArrayList<Integer> selected = vowelIndexes;
			if (consIndexes.size() == 0) selected = vowelIndexes;
			else if (vowelIndexes.size() == 0) selected = consIndexes;
			else if (r.nextDouble() > 1.0/vowelConsonantRatio) selected = consIndexes;
		
			int chosen = r.nextInt(selected.size());
			chars[selected.get(chosen)] = '*';
			selected.remove(chosen);
		}
		
		List<Character> asterisked = new ArrayList<Character>();
		for (int i = 0;i < chars.length;i++) {
			if (chars[i] != '*') {
				asterisked.add(chars[i]);
				continue;
			}
			double x = r.nextDouble();
			if (x < asteriskOmmission) continue;
			else if (i < chars.length - 1 && x > asteriskOmmission && x < asteriskOmmission + asteriskCloning)
				chars[i] = chars[i+1];
			asterisked.add(chars[i]);
		}
		
		return asterisked.toString().substring(1, 3 * asterisked.size() - 1).replaceAll(", ", ""); 
	}
}
