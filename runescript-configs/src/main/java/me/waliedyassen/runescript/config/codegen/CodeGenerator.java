/*
 * Copyright (c) 2019 Walied K. Yassen, All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package me.waliedyassen.runescript.config.codegen;

import lombok.RequiredArgsConstructor;
import lombok.var;
import me.waliedyassen.runescript.config.ast.AstConfig;
import me.waliedyassen.runescript.config.ast.AstIdentifier;
import me.waliedyassen.runescript.config.ast.AstProperty;
import me.waliedyassen.runescript.config.ast.value.AstValueBoolean;
import me.waliedyassen.runescript.config.ast.value.AstValueInteger;
import me.waliedyassen.runescript.config.ast.value.AstValueLong;
import me.waliedyassen.runescript.config.ast.value.AstValueString;
import me.waliedyassen.runescript.config.ast.visitor.AstVisitor;
import me.waliedyassen.runescript.config.binding.ConfigBinding;
import me.waliedyassen.runescript.config.type.rule.ConfigRules;
import me.waliedyassen.runescript.type.PrimitiveType;

import java.util.ArrayList;

/**
 * The code generator for the configuration compiler.
 *
 * @author Walied K. Yassen
 */
@RequiredArgsConstructor
public final class CodeGenerator implements AstVisitor<Object> {

    /**
     * The binding of the configuration.
     */
    private final ConfigBinding binding;

    /**
     * {@inheritDoc}
     */
    @Override
    public BinaryConfig visit(AstConfig config) {
        var count = config.getProperties().length;
        var properties = new ArrayList<BinaryProperty>(count);
        for (var index = 0; index < count; index++) {
            var property = visit(config.getProperties()[index]);
            properties.add(property);
        }
        return new BinaryConfig(binding.getGroup(), config.getName().getText(), properties.toArray(new BinaryProperty[0]));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BinaryProperty visit(AstProperty property) {
        var variable = binding.getVariables().get(property.getKey().getText());
        var rawValues = property.getValues();
        var types = new PrimitiveType[rawValues.length];
        var values = new Object[rawValues.length];
        for (var valueIndex = 0; valueIndex < rawValues.length; valueIndex++) {
            types[valueIndex] = variable.getType().getComponents()[valueIndex];
            values[valueIndex] = rawValues[valueIndex].accept(this);
        }
        if (values.length == 1) {
            Boolean rule = null;
            if (variable.getRules().contains(ConfigRules.EMIT_EMPTY_IF_TRUE)) {
                rule = Boolean.TRUE;
            } else if (variable.getRules().contains(ConfigRules.EMIT_EMPTY_IF_FALSE)) {
                rule = Boolean.FALSE;
            }
            if (rule != null && values[0] == rule) {
                values = null;
            }
        }
        return new BinaryProperty(variable.getOpcode(), types, values);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String visit(AstValueString value) {
        return value.getText();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer visit(AstValueInteger value) {
        return value.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long visit(AstValueLong value) {
        return value.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean visit(AstValueBoolean value) {
        return value.isValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String visit(AstIdentifier identifier) {
        return identifier.getText();
    }
}
