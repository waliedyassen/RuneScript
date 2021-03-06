/*
 * Copyright (c) 2020 Walied K. Yassen, All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package me.waliedyassen.runescript.runtime;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.waliedyassen.runescript.runtime.script.Script;

import java.util.Stack;

/**
 * The script execution runtime, it holds the information about the current context as well
 *
 * @author Walied K. Yassen
 */
@RequiredArgsConstructor
public abstract class ScriptRuntime implements AutoCloseable {

    /**
     * The maximum amount of local fields we can have per runtime.
     */
    public static final int MAX_LOCALS = 256;

    /**
     * The maximum amount of arrays we can have per runtime.
     */
    public static final int MAX_ARRAYS = 5;

    /**
     * The maximum amount of elements we can have per array per runtime.
     */
    public static final int MAX_ARRAY_ELEMENTS = 5000;

    /**
     * The integer stack of the runtime.
     */
    @Getter
    private final Stack<Integer> intStack = new Stack<>();

    /**
     * The string stack of the runtime.
     */
    @Getter
    private final Stack<String> stringStack = new Stack<>();

    /**
     * The long stack of the runtime.
     */
    @Getter
    private final Stack<Long> longStack = new Stack<>();

    /**
     * An array holding of all the long int fields values.
     */
    @Getter
    private final int[] intLocals = new int[MAX_LOCALS];

    /**
     * An array holding of all the long string fields values.
     */
    @Getter
    private final String[] stringLocals = new String[MAX_LOCALS];

    /**
     * An array holding of all the long local fields values.
     */
    @Getter
    private final long[] longLocals = new long[MAX_LOCALS];

    /**
     * An array of all the array sizes in the runtime.
     */
    @Getter
    private final int[] arraySize = new int[MAX_ARRAYS];

    /**
     * An array of all the array elements in the runtime.
     */
    @Getter
    private final int[][] arrayElements = new int[MAX_ARRAYS][MAX_ARRAY_ELEMENTS];

    /**
     * The execution call frame of the runtime.
     */
    @Getter
    private final Stack<ScriptFrame> frames = new Stack<>();

    /**
     * The owner {@link ScriptRuntimePool} of this object.
     */
    @SuppressWarnings("rawtypes")
    @Getter
    private final ScriptRuntimePool pool;

    /**
     * The script which we are currently executing.
     */
    @Getter
    @Setter
    private Script script;

    /**
     * The current execution address.
     */
    @Getter
    @Setter
    private int address;

    /**
     * Whether or not we should abort the execution after the current instruction.
     */
    @Getter
    @Setter
    private boolean abort;

    /**
     * Sets the current execution frame of the runtime from the specified {@link ScriptFrame frame}.
     *
     * @param frame
     *         the frame which we want to set the execution frame based on.
     */
    public void set(ScriptFrame frame) {
        script = frame.getScript();
        address = frame.getAddress();
        System.arraycopy(frame.getIntLocals(), 0, intLocals, 0, MAX_LOCALS);
        System.arraycopy(frame.getStringLocals(), 0, stringLocals, 0, MAX_LOCALS);
        System.arraycopy(frame.getLongLocals(), 0, longLocals, 0, MAX_LOCALS);
    }

    /**
     * Resets the state of the runtime.
     */
    public void reset() {
        intStack.clear();
        stringStack.clear();
        longStack.clear();
        frames.clear();
        address = 0;
        abort = false;
    }

    /**
     * Aborts the execution of the runtime.
     */
    public void abort() {
        if (abort) {
            throw new IllegalStateException("An abort was already requested");
        }
        abort = true;
    }

    /**
     * Pushes a {@code int} value to the top of the int stack.
     *
     * @param value
     *         the int value to push to the int stack.
     */
    public void pushInt(int value) {
        intStack.push(value);
    }

    /**
     * Pops an {@code int} value from the top of the int stack.
     *
     * @return the popped {@code int} value.
     */
    public int popInt() {
        return intStack.pop();
    }

    /**
     * Pushes a {@link String} value to the top of the string stack.
     *
     * @param value
     *         the string value to push to the string stack.
     */
    public void pushString(String value) {
        stringStack.push(value);
    }

    /**
     * Pops an {@link String} value from the top of the string stack.
     *
     * @return the popped {@link String} value.
     */
    public String popString() {
        return stringStack.pop();
    }

    /**
     * Pushes a {@code long} value to the top of the long stack.
     *
     * @param value
     *         the long value to push to the long stack.
     */
    public void pushLong(long value) {
        longStack.push(value);
    }

    /**
     * Pops an {@link String} value from the top of the long stack.
     *
     * @return the popped {@link String} value.
     */
    public long popLong() {
        return longStack.pop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void close() {
        pool.push(this);
    }

    /**
     * Returns the {@code int} value operand that is at the current instruction address.
     *
     * @return the {@code int} value of the operand.
     */
    public int intOperand() {
        return (int) script.getOperands()[address];
    }

    /**
     * Returns the {@link String} value operand that is at the current instruction address.
     *
     * @return the {@link String} value of the operand.
     */
    public String stringOperand() {
        return (String) script.getOperands()[address];
    }

    /**
     * Returns the {@code long} value operand that is at the current instruction address.
     *
     * @return the {@code long} value of the operand.
     */
    public long longOperand() {
        return (long) script.getOperands()[address];
    }
}
