package com.pauljang.towerDefense.data;

import org.bukkit.Location;
import java.util.List;

public class TDWaypoint {
    private final String id;
    private final Location location;
    private final List<String> nextIds;

    public TDWaypoint(String id, Location location, List<String> nextIds) {
        this.id = id;
        this.location = location;
        this.nextIds = nextIds;
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public List<String> getNextIds() {
        return nextIds;
    }
}
