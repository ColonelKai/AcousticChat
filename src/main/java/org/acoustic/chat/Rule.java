package org.acoustic.chat;

public class Rule {

    private final String before, after;
    private final int weighting;
    private final String match;
    private final boolean remove;

    public Rule(int weighting, String matches, String before, String after, boolean remove) {
        this.weighting = weighting;
        this.match = matches;
        this.before = before;
        this.after = after;
        this.remove = remove;
    }

    public int getWeight() {
        return this.weighting;
    }

    public String getMatch() {
        return this.match;
    }

    public String getBefore() {
        return this.before;
    }

    public String getAfter() {
        return this.after;
    }

    public boolean doRemove() {
        return this.remove;
    }
}
