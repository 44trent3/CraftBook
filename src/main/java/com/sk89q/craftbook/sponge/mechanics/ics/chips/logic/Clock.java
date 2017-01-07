/*
 * CraftBook Copyright (C) 2010-2017 sk89q <http://www.sk89q.com>
 * CraftBook Copyright (C) 2011-2017 me4502 <http://www.me4502.com>
 * CraftBook Copyright (C) Contributors
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not,
 * see <http://www.gnu.org/licenses/>.
 */
package com.sk89q.craftbook.sponge.mechanics.ics.chips.logic;

import com.sk89q.craftbook.sponge.mechanics.ics.IC;
import com.sk89q.craftbook.sponge.mechanics.ics.ICType;
import com.sk89q.craftbook.sponge.mechanics.ics.InvalidICException;
import com.sk89q.craftbook.sponge.mechanics.ics.SelfTriggeringIC;
import com.sk89q.craftbook.sponge.util.SignUtil;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.List;

public class Clock extends SelfTriggeringIC {

    private int ticks;
    private int limit;

    public Clock(ICType<? extends IC> type, Location<World> block) {
        super(type, block);
    }

    @Override
    public void create(Player player, List<Text> lines) throws InvalidICException {
        super.create(player, lines);

        try {
            limit = Math.max(5, Math.min(1000, Integer.parseInt(SignUtil.getTextRaw(lines.get(2)))));
        } catch (Exception e) {
            limit = 20;
        }

        lines.set(2, Text.of(limit));
    }

    @Override
    public void think() {
        ticks++;
        if (ticks == limit) {
            ticks = 0;
            getPinSet().setOutput(0, !getPinSet().getOutput(0, this), this);
        }
    }

    @Override
    public boolean canThink() {
        return true;
    }
}
