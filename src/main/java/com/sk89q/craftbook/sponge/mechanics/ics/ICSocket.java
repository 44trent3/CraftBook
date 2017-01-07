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
package com.sk89q.craftbook.sponge.mechanics.ics;

import com.me4502.modularframework.module.Module;
import com.sk89q.craftbook.core.CraftBookAPI;
import com.sk89q.craftbook.core.util.CraftBookException;
import com.sk89q.craftbook.core.util.documentation.DocumentationGenerator;
import com.sk89q.craftbook.core.util.documentation.DocumentationProvider;
import com.sk89q.craftbook.sponge.CraftBookPlugin;
import com.sk89q.craftbook.sponge.mechanics.ics.pinsets.PinSet;
import com.sk89q.craftbook.sponge.mechanics.ics.pinsets.Pins3ISO;
import com.sk89q.craftbook.sponge.mechanics.ics.pinsets.PinsSISO;
import com.sk89q.craftbook.sponge.mechanics.types.SpongeBlockMechanic;
import com.sk89q.craftbook.sponge.st.SpongeSelfTriggerManager;
import com.sk89q.craftbook.sponge.st.SelfTriggeringMechanic;
import com.sk89q.craftbook.sponge.util.SignUtil;
import com.sk89q.craftbook.sponge.util.SpongeMechanicData;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.NotifyNeighborBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sk89q.craftbook.core.util.documentation.DocumentationGenerator.createStringOfLength;
import static com.sk89q.craftbook.core.util.documentation.DocumentationGenerator.padToLength;

@Module(moduleName = "ICSocket", onEnable="onInitialize", onDisable="onDisable")
public class ICSocket extends SpongeBlockMechanic implements SelfTriggeringMechanic, DocumentationProvider {

    static final HashMap<String, PinSet> PINSETS = new HashMap<>();
    private static final Pattern IC_TABLE_PATTERN = Pattern.compile("%IC_TABLE%", Pattern.LITERAL);

    static {
        PINSETS.put("SISO", new PinsSISO());
        PINSETS.put("3ISO", new Pins3ISO());
    }

    @Override
    public void onInitialize() throws CraftBookException {
        super.onInitialize();

        if ("true".equalsIgnoreCase(System.getProperty("craftbook.generate-docs"))) {
            ICManager.getICTypes().forEach(DocumentationGenerator::generateDocumentation);
        }
    }

    @Override
    public String getName() {
        return "ICs";
    }

    @Listener
    public void onChangeSign(ChangeSignEvent event, @First Player player) {
        ICType<? extends IC> icType = ICManager.getICType((event.getText().lines().get(1)).toPlain());
        if (icType == null) return;

        List<Text> lines = event.getText().lines().get();
        lines.set(0, Text.of(icType.shorthandId.toUpperCase()));
        lines.set(1, Text.of(SignUtil.getTextRaw(lines.get(1)).toUpperCase()));

        try {
            createICData(event.getTargetTile().getLocation(), lines, player);
            player.sendMessage(Text.of(TextColors.YELLOW, "Created " + icType.name));

            event.getText().set(Keys.SIGN_LINES, lines);
        } catch (InvalidICException e) {
            event.setCancelled(true);
            player.sendMessage(Text.of("Failed to create IC. " + e.getMessage()));
        }
    }

    @Listener
    public void onBlockUpdate(NotifyNeighborBlockEvent event, @First BlockSnapshot source) {
        if(source.getState().getType() != BlockTypes.WALL_SIGN) return;

        BaseICData data = getICData(source.getLocation().get());
        if (data == null) return;

        for(Entry<Direction, BlockState> entries : event.getNeighbors().entrySet()) {

            boolean powered = entries.getValue().get(Keys.POWER).orElse(0) > 0;

            if (powered != data.ic.getPinSet().getInput(data.ic.getPinSet().getPinForLocation(data.ic, source.getLocation().get().getRelative(entries.getKey())), data.ic)) {
                data.ic.getPinSet().setInput(data.ic.getPinSet().getPinForLocation(data.ic, source.getLocation().get().getRelative(entries.getKey())), powered, data.ic);
                data.ic.trigger();
            }
        }
    }

    @Override
    public void onThink(Location<?> block) {
        BaseICData data = getICData((Location<World>) block);
        if (data == null) return;
        if (!(data.ic instanceof SelfTriggeringIC)) return;
        ((SelfTriggeringIC) data.ic).think();
    }

    @Override
    public boolean isValid(Location<?> location) {
        return getICData((Location<World>) location) != null;
    }

    private void createICData(Location<World> block, List<Text> lines, Player player) throws InvalidICException {
        if (block.getBlockType() == BlockTypes.WALL_SIGN) {
            ICType<? extends IC> icType = ICManager.getICType(SignUtil.getTextRaw(lines.get(1)));
            if (icType == null) {
                throw new InvalidICException("Invalid IC Type");
            }

            BaseICData data = getData(BaseICData.class, block);
            data.ic = icType.buildIC(block);

            data.ic.create(player, lines);
            Sponge.getScheduler().createTaskBuilder().execute(task -> {
                data.ic.load();
                if (data.ic instanceof SelfTriggeringIC && (((SelfTriggeringIC) data.ic).canThink())) ((SpongeSelfTriggerManager) CraftBookPlugin.inst().getSelfTriggerManager().get()).register(this, block);
            }).submit(CraftBookPlugin.spongeInst().getContainer());
        } else {
            throw new InvalidICException("Block is not a sign");
        }
    }

    private BaseICData getICData(Location<World> block) {
        if (block.getBlockType() == BlockTypes.WALL_SIGN) {
            Sign sign = (Sign) block.getTileEntity().get();
            ICType<? extends IC> icType = ICManager.getICType(SignUtil.getTextRaw(sign, 1));
            if (icType == null) return null;

            BaseICData data = getData(BaseICData.class, block);

            if (data.ic == null || (data.ic.type != null && !icType.equals(data.ic.type))) {
                // Found broken IC.
                CraftBookPlugin.spongeInst().getLogger().error("Warning: Found broken IC at " + block.toString());
                if (data.ic != null && data.ic.type != null) {
                    CraftBookPlugin.spongeInst().getLogger().error("Wrong type. Excepted " + icType.name + " and got " + data.ic.type.name);
                }
            } else if(data.ic.block == null) {
                data.ic.block = block;
                data.ic.type = icType;

                data.ic.load();
                if (data.ic instanceof SelfTriggeringIC && (((SelfTriggeringIC) data.ic).canThink())) ((SpongeSelfTriggerManager) CraftBookPlugin.inst().getSelfTriggerManager().get()).register(this, block);
            }

            return data;
        }

        return null;
    }

    @Override
    public String getPath() {
        return "mechanics/ics/index";
    }

    @Override
    public String performCustomConversions(String input) {
        StringBuilder icTable = new StringBuilder();

        icTable.append(".. toctree::\n");
        icTable.append("    :hidden:\n");
        icTable.append("    :glob:\n");
        icTable.append("    :titlesonly:\n\n");
        icTable.append("    *\n\n");


        icTable.append("ICs\n");
        icTable.append("===\n\n");

        int idLength = "IC ID".length(),
                shorthandLength = "Shorthand".length(),
                nameLength = "Name".length(),
                descriptionLength = "Description".length(),
                familiesLength = "Family".length(),
                stLength = "Self Triggering".length();

        for(ICType<? extends IC> icType : ICManager.getICTypes()) {
            if((":doc:`" + icType.modelId + '`').length() > idLength)
                idLength = (":doc:`ics/" + icType.modelId + '`').length();
            if(icType.shorthandId.length() > shorthandLength)
                shorthandLength = icType.shorthandId.length();
            if(icType.name.length() > nameLength)
                nameLength = icType.name.length();
            if(icType.description.length() > descriptionLength)
                descriptionLength = icType.description.length();
            if(icType.getDefaultPinSet().length() > familiesLength)
                familiesLength = icType.getDefaultPinSet().length();
            if((SelfTriggeringIC.class.isAssignableFrom(icType.icClass) ? "Yes" : "No").length() > stLength)
                stLength = (SelfTriggeringIC.class.isAssignableFrom(icType.icClass) ? "Yes" : "No").length();
        }

        String border = createStringOfLength(idLength, '=') + ' '
                + createStringOfLength(shorthandLength, '=') + ' '
                + createStringOfLength(nameLength, '=') + ' '
                + createStringOfLength(descriptionLength, '=') + ' '
                + createStringOfLength(familiesLength, '=') + ' '
                + createStringOfLength(stLength, '=');

        icTable.append(border).append('\n');
        icTable.append(padToLength("IC ID", idLength + 1))
                .append(padToLength("Shorthand", shorthandLength + 1))
                .append(padToLength("Name", nameLength + 1))
                .append(padToLength("Description", descriptionLength + 1))
                .append(padToLength("Family", familiesLength + 1))
                .append(padToLength("Self Triggering", stLength + 1)).append('\n');
        icTable.append(border).append('\n');
        for(ICType<? extends IC> icType : ICManager.getICTypes()) {
            icTable.append(padToLength(":doc:`" + icType.modelId + '`', idLength + 1))
                    .append(padToLength(icType.shorthandId, shorthandLength + 1))
                    .append(padToLength(icType.name, nameLength + 1))
                    .append(padToLength(icType.description, descriptionLength + 1))
                    .append(padToLength(icType.getDefaultPinSet(), familiesLength + 1))
                    .append(padToLength((SelfTriggeringIC.class.isAssignableFrom(icType.icClass) ? "Yes" : "No"), stLength + 1)).append('\n');
        }
        icTable.append(border).append('\n');

        return IC_TABLE_PATTERN.matcher(input).replaceAll(Matcher.quoteReplacement(icTable.toString()));
    }

    public static class BaseICData extends SpongeMechanicData {
        public IC ic;

        @Override
        public String toString() {
            return String.valueOf(ic);
        }
    }
}
