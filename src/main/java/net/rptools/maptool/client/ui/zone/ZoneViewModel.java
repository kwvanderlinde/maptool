package net.rptools.maptool.client.ui.zone;

import net.rptools.lib.CodeTimer;
import net.rptools.maptool.model.Zone;

public class ZoneViewModel {
    private final CodeTimer timer;
    private final Zone zone;

    public ZoneViewModel(CodeTimer timer, Zone zone) {
        this.timer = timer;
        this.zone = zone;
    }
}
