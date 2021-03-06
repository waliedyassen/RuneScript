/*
 * Copyright (c) 2020 Walied K. Yassen, All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package me.waliedyassen.runescript.compiler.codegen.script;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.waliedyassen.runescript.compiler.codegen.block.BlockList;
import me.waliedyassen.runescript.compiler.codegen.local.Local;
import me.waliedyassen.runescript.compiler.codegen.sw.SwitchTable;
import me.waliedyassen.runescript.compiler.symbol.impl.script.ScriptInfo;
import me.waliedyassen.runescript.type.stack.StackType;

import java.util.List;
import java.util.Map;

/**
 * Represents a single generated bytecode level script.
 *
 * @author Walied K. Yassen
 */
@RequiredArgsConstructor
public final class BinaryScript {

    /**
     * The extension of the file containing the script.
     */
    @Getter
    private final String extension;

    /**
     * The full name of the script.
     */
    @Getter
    private final String name;

    /**
     * The blocks that are registered within our script.
     */
    @Getter
    private final BlockList blockList;

    /**
     * A map of all the script parameters.
     */
    @Getter
    private final Map<StackType, List<Local>> parameters;

    /**
     * A map of all the script local variables.
     */
    @Getter
    private final Map<StackType, List<Local>> variables;

    /**
     * A list of all the script switch tables.
     */
    @Getter
    private final List<SwitchTable> switchTables;

    /**
     * The symbol information of this script.
     */
    @Getter
    private final ScriptInfo scriptInfo;
}
