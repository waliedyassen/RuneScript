/*
 * Copyright (c) 2020 Walied K. Yassen, All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package me.waliedyassen.runescript.config.codegen;

import lombok.RequiredArgsConstructor;
import lombok.var;
import me.waliedyassen.runescript.compiler.idmapping.IDManager;
import me.waliedyassen.runescript.compiler.symbol.SymbolTable;
import me.waliedyassen.runescript.config.syntax.ConfigSyntax;
import me.waliedyassen.runescript.config.syntax.ConstantSyntax;
import me.waliedyassen.runescript.config.syntax.IdentifierSyntax;
import me.waliedyassen.runescript.config.syntax.PropertySyntax;
import me.waliedyassen.runescript.config.syntax.value.*;
import me.waliedyassen.runescript.config.syntax.visitor.SyntaxVisitor;
import me.waliedyassen.runescript.config.binding.ConfigBinding;
import me.waliedyassen.runescript.config.codegen.property.impl.BinaryBasicProperty;
import me.waliedyassen.runescript.config.codegen.property.impl.BinaryMapProperty;
import me.waliedyassen.runescript.config.codegen.property.impl.BinaryParamProperty;
import me.waliedyassen.runescript.config.codegen.property.impl.BinarySplitArrayProperty;
import me.waliedyassen.runescript.config.var.ConfigBasicDynamicProperty;
import me.waliedyassen.runescript.config.var.ConfigBasicProperty;
import me.waliedyassen.runescript.config.var.ConfigMapProperty;
import me.waliedyassen.runescript.config.var.ConfigParamProperty;
import me.waliedyassen.runescript.config.var.rule.ConfigRules;
import me.waliedyassen.runescript.config.var.splitarray.ConfigSplitArrayProperty;
import me.waliedyassen.runescript.type.primitive.PrimitiveType;
import me.waliedyassen.runescript.type.stack.StackType;

/**
 * The code generator for the configuration compiler.
 *
 * @author Walied K. Yassen
 */
@RequiredArgsConstructor
public final class CodeGenerator implements SyntaxVisitor<Object> {

    /**
     * The ID provider of the generator.
     */
    private final IDManager idProvider;

    /**
     * The symbol table of the compiler.
     */
    private final SymbolTable symbolTable;

    /**
     * The binding of the configuration.
     */
    private final ConfigBinding binding;

    /**
     * {@inheritDoc}
     */
    @Override
    public Object visit(ConstantSyntax syntax) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BinaryConfig visit(ConfigSyntax config) {
        var count = config.getProperties().length;
        var binaryConfig = new BinaryConfig(binding.getGroup(), config.getName().getText());
        for (var index = 0; index < count; index++) {
            generateProperty(binaryConfig, config.getProperties()[index]);
        }
        return binaryConfig;
    }

    // TODO: Do this in a cleaner way.

    /**
     * Generates a binary property for the specified property.
     *
     * @param config
     *         the binary configuration.
     * @param property
     *         the  property that we are generating for.
     */
    private void generateProperty(BinaryConfig config, PropertySyntax property) {
        var bindingProperty = binding.getProperties().get(property.getKey().getText());
        if (bindingProperty instanceof ConfigBasicProperty) {
            generateBasicProperty(config, property, (ConfigBasicProperty) bindingProperty);
        } else if (bindingProperty instanceof ConfigBasicDynamicProperty) {
            generateBasicDynamicProperty(config, property, (ConfigBasicDynamicProperty) bindingProperty);
        } else if (bindingProperty instanceof ConfigSplitArrayProperty) {
            generateSplitArrayProperty(config, property, (ConfigSplitArrayProperty) bindingProperty);
        } else if (bindingProperty instanceof ConfigParamProperty) {
            generateParamProperty(config, property, (ConfigParamProperty) bindingProperty);
        } else if (bindingProperty instanceof ConfigMapProperty) {
            generateMapProperty(config, property, (ConfigMapProperty) bindingProperty);
        } else {
            throw new IllegalArgumentException("Unrecognised binding property type: " + bindingProperty);
        }
    }

    /**
     * Generates a binary property for the specified basic property.
     *
     * @param config
     *         the binary configuration.
     * @param node
     *         the AST node of the property.
     * @param property
     *         the basic property that we are generating for.
     */
    private void generateBasicProperty(BinaryConfig config, PropertySyntax node, ConfigBasicProperty property) {
        var rawValues = node.getValues();
        var types = new PrimitiveType[rawValues.length];
        var values = new Object[rawValues.length];
        for (var valueIndex = 0; valueIndex < rawValues.length; valueIndex++) {
            types[valueIndex] = property.getComponents()[valueIndex];
            values[valueIndex] = rawValues[valueIndex].accept(this);
        }
        if (values.length == 1) {
            Boolean rule = null;
            if (property.getRules(0).contains(ConfigRules.EMIT_EMPTY_IF_TRUE)) {
                rule = Boolean.TRUE;
            } else if (property.getRules(0).contains(ConfigRules.EMIT_EMPTY_IF_FALSE)) {
                rule = Boolean.FALSE;
            }
            if (rule != null) {
                if (values[0] == rule) {
                    // emit empty if the true matches.
                    values = null;
                } else {
                    // Skip if the rule does not match.
                    return;
                }
            }
        }
        config.addProperty(new BinaryBasicProperty(property.getOpcode(), types, values));
    }

    /**
     * Generates a binary property for the specified basic dynamic opcode property.
     *
     * @param config
     *         the binary configuration.
     * @param node
     *         the AST node of the property.
     * @param property
     *         the basic dynamic opcode property that we are generating for.
     */
    private void generateBasicDynamicProperty(BinaryConfig config, PropertySyntax node, ConfigBasicDynamicProperty property) {
        var inferring = ((ConfigSyntax) node.getParent()).findProperty(property.getTypeProperty());
        if (inferring == null) {
            throw new IllegalStateException();
        }
        var type = ((ValueTypeSyntax) inferring.getValues()[0]).getType();
        config.addProperty(new BinaryBasicProperty(property.getOpcodes()[type.getStackType() == StackType.INT ? 0 : 1], new PrimitiveType[]{type}, new Object[]{node.getValues()[0].accept(this)}));
    }

    /**
     * Generates a binary property for the specified split array property.
     *
     * @param config
     *         the binary configuration.
     * @param node
     *         the AST node of the property.
     * @param property
     *         the basic property that we are generating for.
     */
    private void generateSplitArrayProperty(BinaryConfig config, PropertySyntax node, ConfigSplitArrayProperty property) {
        var binaryProperty = (BinarySplitArrayProperty) config.findProperty(property.getData().getCode());
        if (binaryProperty == null) {
            binaryProperty = new BinarySplitArrayProperty(
                    property.getData().getCode(),
                    property.getData().getSizeType(),
                    property.getData().getMaxSize());
            config.addProperty(binaryProperty);
        }
        var binaryValue = binaryProperty.getValue(property.getElementId());
        if (binaryValue == null) {
            binaryValue = binaryProperty.addValue(property.getElementId(), property.getData().getComponentsCount());
        }
        binaryValue.getTypes()[property.getComponentId()] = property.getType();
        binaryValue.getValues()[property.getComponentId()] = node.getValues()[0].accept(this);
    }

    /**
     * Generates a binary property for the specified param property.
     *
     * @param config
     *         the binary configuration.
     * @param node
     *         the AST node of the property.
     * @param property
     *         the basic property that we are generating for.
     */
    private void generateParamProperty(BinaryConfig config, PropertySyntax node, ConfigParamProperty property) {
        var binaryProperty = (BinaryParamProperty) config.findProperty(property.getCode());
        if (binaryProperty == null) {
            binaryProperty = new BinaryParamProperty(property.getCode());
            config.addProperty(binaryProperty);
        }
        var paramInfo = symbolTable.lookupConfig(((ValueConfigSyntax) node.getValues()[0]).getName().getText());
        binaryProperty.getValues().put(idProvider.findConfig(PrimitiveType.PARAM, paramInfo.getName()), node.getValues()[1].accept(this));
    }

    /**
     * Generates a binary property for the specified param property.
     *
     * @param config
     *         the binary configuration.
     * @param node
     *         the AST node of the property.
     * @param property
     *         the basic property that we are generating for.
     */
    private void generateMapProperty(BinaryConfig config, PropertySyntax node, ConfigMapProperty property) {
        var valueProperty = ((ConfigSyntax) node.getParent()).findProperty(property.getValueTypeProperty());
        if (valueProperty == null) {
            throw new IllegalStateException();
        }
        var valueType = ((ValueTypeSyntax) valueProperty.getValues()[0]).getType();
        var code = property.getOpcodes()[valueType.getStackType() == StackType.INT ? 0 : 1];
        var binaryProperty = (BinaryMapProperty) config.findProperty(code);
        if (binaryProperty == null) {
            binaryProperty = new BinaryMapProperty(code);
            config.addProperty(binaryProperty);
        }
        var keyRaw = node.getValues()[0];
        var valueRaw = node.getValues()[1];
        binaryProperty.getValues().put((Integer)keyRaw.accept(this), valueRaw.accept(this));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object visit(PropertySyntax property) {
        throw new UnsupportedOperationException("You shouldn't be doing this, for now.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object visit(ValueStringSyntax value) {
        var graphic = symbolTable.lookupGraphic(value.getText());
        if (graphic != null) {
            return graphic.getId();
        }
        return value.getText();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer visit(ValueIntegerSyntax value) {
        return value.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long visit(ValueLongSyntax value) {
        return value.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean visit(ValueBooleanSyntax value) {
        return value.isValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PrimitiveType visit(ValueTypeSyntax value) {
        return value.getType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer visit(ValueCoordgridSyntax valueCoordgridSyntax) {
        return valueCoordgridSyntax.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object visit(ValueConstantSyntax value) {
        return symbolTable.lookupConstant(value.getName().getText()).getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object visit(ValueConfigSyntax value) {
        var name = value.getName().getText();
        return idProvider.findConfig(symbolTable.lookupConfig(name).getType(), name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String visit(IdentifierSyntax identifier) {
        return identifier.getText();
    }
}
