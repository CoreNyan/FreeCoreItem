package io.github.freecoreitem.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public final class ItemAction {
    private final List<String> commands = new ArrayList<>();
    private final List<String> conditions = new ArrayList<>();
    private int cooldownTicks;
    private boolean consume;
    private boolean cancelEvent;

    public static ItemAction load(ConfigurationSection section) {
        ItemAction action = new ItemAction();
        if (section != null) {
            action.commands.addAll(section.getStringList("commands"));
            action.conditions.addAll(section.getStringList("conditions"));
            action.cooldownTicks = Math.max(0, section.getInt("cooldown-ticks", 0));
            action.consume = section.getBoolean("consume", false);
            action.cancelEvent = section.getBoolean("cancel-event", false);
        }
        return action;
    }

    public void save(ConfigurationSection section) {
        section.set("commands", commands);
        section.set("conditions", conditions);
        section.set("cooldown-ticks", cooldownTicks);
        section.set("consume", consume);
        section.set("cancel-event", cancelEvent);
    }

    public List<String> commands() { return commands; }
    public List<String> conditions() { return conditions; }
    public int cooldownTicks() { return cooldownTicks; }
    public void cooldownTicks(int value) { cooldownTicks = Math.max(0, value); }
    public boolean consume() { return consume; }
    public void consume(boolean value) { consume = value; }
    public boolean cancelEvent() { return cancelEvent; }
    public void cancelEvent(boolean value) { cancelEvent = value; }
}
