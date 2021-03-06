/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.commands.generic.permission;

import me.lucko.luckperms.api.TemporaryDataMutateResult;
import me.lucko.luckperms.api.TemporaryMergeBehaviour;
import me.lucko.luckperms.api.context.MutableContextSet;
import me.lucko.luckperms.api.nodetype.types.InheritanceType;
import me.lucko.luckperms.common.actionlog.ExtendedLogEntry;
import me.lucko.luckperms.common.command.CommandResult;
import me.lucko.luckperms.common.command.abstraction.CommandException;
import me.lucko.luckperms.common.command.abstraction.SharedSubCommand;
import me.lucko.luckperms.common.command.access.ArgumentPermissions;
import me.lucko.luckperms.common.command.access.CommandPermission;
import me.lucko.luckperms.common.command.tabcomplete.TabCompleter;
import me.lucko.luckperms.common.command.tabcomplete.TabCompletions;
import me.lucko.luckperms.common.command.utils.ArgumentParser;
import me.lucko.luckperms.common.command.utils.MessageUtils;
import me.lucko.luckperms.common.command.utils.StorageAssistant;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.locale.LocaleManager;
import me.lucko.luckperms.common.locale.command.CommandSpec;
import me.lucko.luckperms.common.locale.message.Message;
import me.lucko.luckperms.common.model.PermissionHolder;
import me.lucko.luckperms.common.node.factory.NodeFactory;
import me.lucko.luckperms.common.node.model.NodeTypes;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.common.utils.DurationFormatter;
import me.lucko.luckperms.common.utils.Predicates;

import java.util.List;

public class PermissionSetTemp extends SharedSubCommand {
    public PermissionSetTemp(LocaleManager locale) {
        super(CommandSpec.PERMISSION_SETTEMP.localize(locale), "settemp", CommandPermission.USER_PERM_SET_TEMP, CommandPermission.GROUP_PERM_SET_TEMP, Predicates.inRange(0, 2));
    }

    @Override
    public CommandResult execute(LuckPermsPlugin plugin, Sender sender, PermissionHolder holder, List<String> args, String label, CommandPermission permission) throws CommandException {
        if (ArgumentPermissions.checkModifyPerms(plugin, sender, permission, holder)) {
            Message.COMMAND_NO_PERMISSION.send(sender);
            return CommandResult.NO_PERMISSION;
        }

        String node = ArgumentParser.parseString(0, args);
        boolean value = ArgumentParser.parseBoolean(1, args);
        long duration = ArgumentParser.parseDuration(2, args);
        TemporaryMergeBehaviour modifier = ArgumentParser.parseTemporaryModifier(3, args).orElseGet(() -> plugin.getConfiguration().get(ConfigKeys.TEMPORARY_ADD_BEHAVIOUR));
        MutableContextSet context = ArgumentParser.parseContext(3, args, plugin);

        if (ArgumentPermissions.checkContext(plugin, sender, permission, context) ||
                ArgumentPermissions.checkGroup(plugin, sender, holder, context) ||
                ArgumentPermissions.checkArguments(plugin, sender, permission, node)) {
            Message.COMMAND_NO_PERMISSION.send(sender);
            return CommandResult.NO_PERMISSION;
        }

        InheritanceType inheritanceType = NodeTypes.parseInheritanceType(node);
        if (inheritanceType != null) {
            if (ArgumentPermissions.checkGroup(plugin, sender, inheritanceType.getGroupName(), context)) {
                Message.COMMAND_NO_PERMISSION.send(sender);
                return CommandResult.NO_PERMISSION;
            }
        }

        TemporaryDataMutateResult result = holder.setPermission(NodeFactory.builder(node).setValue(value).withExtraContext(context).setExpiry(duration).build(), modifier);

        if (result.getResult().asBoolean()) {
            duration = result.getMergedNode().getExpiryUnixTime();
            Message.SETPERMISSION_TEMP_SUCCESS.send(sender, node, value, holder.getFriendlyName(), DurationFormatter.LONG.formatDateDiff(duration), MessageUtils.contextSetToString(plugin.getLocaleManager(), context));

            ExtendedLogEntry.build().actor(sender).acted(holder)
                    .action("permission", "settemp", node, value, duration, context)
                    .build().submit(plugin, sender);

            StorageAssistant.save(holder, sender, plugin);
            return CommandResult.SUCCESS;
        } else {
            Message.ALREADY_HAS_TEMP_PERMISSION.send(sender, holder.getFriendlyName(), node, MessageUtils.contextSetToString(plugin.getLocaleManager(), context));
            return CommandResult.STATE_ERROR;
        }
    }

    @Override
    public List<String> tabComplete(LuckPermsPlugin plugin, Sender sender, List<String> args) {
        return TabCompleter.create()
                .at(0, TabCompletions.permissions(plugin))
                .at(1, TabCompletions.booleans())
                .complete(args);
    }
}
