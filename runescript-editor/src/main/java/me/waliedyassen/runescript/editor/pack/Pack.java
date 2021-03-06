/*
 * Copyright (c) 2020 Walied K. Yassen, All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package me.waliedyassen.runescript.editor.pack;

/**
 * The interface which should be implemented by all of the packing methods.
 *
 * @author Walied K. Yassen
 */
public interface Pack {

    /**
     * Packs the specified {@link PackFile} using this method.
     *
     * @param file the file to pack using this method.
     */
    void pack(PackFile file);
}
