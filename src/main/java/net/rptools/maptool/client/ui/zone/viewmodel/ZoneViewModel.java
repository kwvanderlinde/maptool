package net.rptools.maptool.client.ui.zone.viewmodel;

import net.rptools.lib.CodeTimer;
import net.rptools.maptool.client.ui.zone.ZoneRenderer;
import net.rptools.maptool.model.GUID;
import net.rptools.maptool.model.Zone;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ZoneViewModel {
    private final CodeTimer timer;
    private final Zone zone;

    public ZoneViewModel(CodeTimer timer, Zone zone) {
        this.timer = timer;
        this.zone = zone;
    }
}
