package dev.esp.config;

/** Filters that apply to players only, since every player shares a single {@code EntityType}. */
public final class PlayerFilters {
    private boolean renderSelf;
    private boolean renderOthers;
    private boolean onlyFriends;
    private boolean highlightFriends;
    private boolean renderInvisible;

    public PlayerFilters() {
        this.renderSelf = false;
        this.renderOthers = true;
        this.onlyFriends = false;
        this.highlightFriends = true;
        this.renderInvisible = true;
    }

    public boolean renderSelf() {
        return renderSelf;
    }

    public void renderSelf(boolean value) {
        this.renderSelf = value;
    }

    public boolean renderOthers() {
        return renderOthers;
    }

    public void renderOthers(boolean value) {
        this.renderOthers = value;
    }

    public boolean onlyFriends() {
        return onlyFriends;
    }

    public void onlyFriends(boolean value) {
        this.onlyFriends = value;
    }

    public boolean highlightFriends() {
        return highlightFriends;
    }

    public void highlightFriends(boolean value) {
        this.highlightFriends = value;
    }

    public boolean renderInvisible() {
        return renderInvisible;
    }

    public void renderInvisible(boolean value) {
        this.renderInvisible = value;
    }
}
