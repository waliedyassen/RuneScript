/*
 * Copyright (c) 2020 Walied K. Yassen, All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package me.waliedyassen.runescript.config.lexer.token;

public enum Kind {

    /**
     * The End of File token kind.
     */
    EOF,

    /**
     * The Left Bracket token kind.
     */
    LBRACKET,

    /**
     * The Right Bracket token kind.
     */
    RBRACKET,

    /**
     * The Equal token kind.
     */
    EQUAL,

    /**
     * The Caret token kind.
     */
    CARET,

    /**
     * The Comma token kind.
     */
    COMMA,

    /**
     * The identifier token kind.
     */
    IDENTIFIER,

    /**
     * The string token kind.
     */
    STRING,

    /**
     * The normal integer (32-bit) literal token kind.
     */
    INTEGER,

    /**
     * The long integer (64-bit) literal token kind.
     */
    LONG,

    /**
     * The boolean literal token kind.
     */
    BOOLEAN,

    /**
     * The type literal token kind.
     */
    TYPE,

    /**
     * The coordgrid ltieral token kind.
     */
    COORDGRID,

    /**
     * The comment token kind.
     */
    COMMENT,
}
