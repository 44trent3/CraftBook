// $Id$
/*
 * CraftBook
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

import com.sk89q.craftbook.ic.ChipState;
import com.sk89q.craftbook.ic.SISOFamilyIC;

/**
 * 1-bit number based on modulus of server time.
 *
 * @author Shaun (sturmeh)
 */
public class MC1025 extends SISOFamilyIC {
	
	public void think(ChipState chip) {
		chip.title("CLOCK BIT");
		if (chip.in(1).is())
			chip.out(1).set(isServerTimeOdd());
	}

    private boolean isServerTimeOdd() {
        long time = etc.getServer().getRelativeTime() % 2;
        if (time < 0) time += 2;
        return (time == 1);
    }
}
