import external.mifmif.common.regex.Generex;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Math {

    static String fixColor(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    static double calcEntropy(Player sender, Player receiver, FileConfiguration config) {
        double distance = sender.getLocation().distance(receiver.getLocation());
        double percent = distance / config.getInt("maxDistance");
        double fluctuation = config.getDouble("entropyRandomFluctuation");
        double entropy = percent + (fluctuation*2* AcousticChat.r.nextDouble() - fluctuation);
        entropy = java.lang.Math.min(java.lang.Math.max(0, entropy), 1.0); //to make sure it's still in range
        entropy = AcousticChat.config.getBoolean("obeyInverseSquareLaw") ? java.lang.Math.pow(entropy, 2):entropy;

        //increase entropy if there are blocks in the way
        if (AcousticChat.config.getBoolean("lineOfSight.enabled") && !sender.hasLineOfSight(receiver))
            entropy += AcousticChat.config.getDouble("lineOfSight.weighting");

        if (!AcousticChat.config.getBoolean("senderFacing.enabled")) return entropy;

        //increase entropy if players are not facing each other
        Vector senderFacing = sender.getEyeLocation().getDirection().normalize();
        Vector receiverDirection = receiver.getLocation().toVector().subtract(sender.getLocation().toVector()).normalize();

        double anglenoise = senderFacing.angle(receiverDirection) / java.lang.Math.PI;
        entropy += anglenoise * AcousticChat.config.getDouble("senderFacing.weighting");
        //Pi radians is 180 degrees which should be max angle between vectors
        return entropy;
    }

    private static int sumWeights(ArrayList<Rule> rules) {
        int weightSum = 0;
        for (Rule r : rules)
            weightSum += (r.getWeight() > 0) ? r.getWeight():0;
        return weightSum;
    }

    public static String addNoise(String message, double entropy) {
        ConfigurationSection effects = AcousticChat.config.getConfigurationSection("noiseEffects");
        Set<String> ruleKeys = effects.getKeys(false);

        ArrayList<Rule> rules = new ArrayList<Rule>();
        for (String ruleKey : ruleKeys) {
            if (effects.contains(ruleKey+".enabled") && !effects.getBoolean(ruleKey+".enabled"))
                continue;
            rules.add(new Rule(
                            effects.contains(ruleKey+".weighting", false) ? effects.getInt(ruleKey+".weighting"):-1,
                            effects.contains(ruleKey+".match", false) ? effects.getString(ruleKey+".match"):"",
                            fixColor(effects.contains(ruleKey+".before", false) ? effects.getString(ruleKey+".before"):""),
                            fixColor(effects.contains(ruleKey+".after", false) ? effects.getString(ruleKey+".after"):""),
                            effects.contains(ruleKey+".remove", false) ? effects.getBoolean(ruleKey+".remove"):false
                    )
            );
        }

        int weightSum = sumWeights(rules);
        String noiseMessage = message;

        for (Rule rule : rules) {
            Pattern rulePattern = Pattern.compile(rule.getMatch());
            Matcher m = rulePattern.matcher(noiseMessage);

            int num_matches = 0;
            while (m.find()) num_matches++;
            m.reset();

            //TODO: don't count num_matches if rule weight <= 0
            int num_insertions = (rule.getWeight() < 0) ? num_matches:(int) java.lang.Math.round(num_matches * ((double)(rule.getWeight()) / weightSum) * entropy);

            Generex before = new Generex(rule.getBefore());
            Generex after = new Generex(rule.getAfter());

            StringBuilder sb = new StringBuilder();
            int pos = 0;

            while (m.find()) {
                sb.append(noiseMessage, pos, m.start());
                if (AcousticChat.r.nextInt(num_matches) < num_insertions) {
                    sb.append(before.random());
                    if (!rule.doRemove())
                        sb.append(noiseMessage, m.start(), m.end());
                    sb.append(after.random());
                    pos = m.end();
                    num_insertions--;
                } else {
                    sb.append(noiseMessage, m.start(), m.end());
                    pos = m.end();
                }
                num_matches--;
            }
            sb.append(noiseMessage, pos, noiseMessage.length());//in case pos still = 0
            noiseMessage = sb.toString();
        }

        return noiseMessage;
    }
}
