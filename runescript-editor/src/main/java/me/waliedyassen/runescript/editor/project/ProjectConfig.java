/*
 * Copyright (c) 2020 Walied K. Yassen, All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package me.waliedyassen.runescript.editor.project;

import com.electronwill.nightconfig.core.CommentedConfig;
import lombok.var;
import me.waliedyassen.runescript.compiler.type.ArrayReference;
import me.waliedyassen.runescript.config.var.ConfigProperty;
import me.waliedyassen.runescript.config.var.rule.ConfigRule;
import me.waliedyassen.runescript.config.var.rule.ConfigRules;
import me.waliedyassen.runescript.config.var.rule.impl.ConfigRangeRule;
import me.waliedyassen.runescript.config.var.rule.impl.ConfigRequireRule;
import me.waliedyassen.runescript.type.primitive.PrimitiveType;
import me.waliedyassen.runescript.type.Type;
import me.waliedyassen.runescript.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A static-class that is responsible for loading and saving the project configuration.
 *
 * @author Walied K. Yassen
 */
public final class ProjectConfig {

    /**
     * The range rule pattern.
     */
    private static final Pattern RANGE_RULE_PATTERN = createConfigRulePattern("RANGE", int.class, int.class);

    /**
     * The require rule pattern.
     */
    private static final Pattern REQUIRE_RULE_PATTERN = createConfigRulePattern("REQUIRE", String.class);

    /**
     * Attempts to parse a type variable from the specified {@link CommentedConfig config}.
     *
     * @param config
     *         the configuration object to attempt to parse from.
     * @param name
     *         the name of the variable that we are trying to parse.
     *
     * @return the inferred variable name.
     */
    public static String parseInferredVariable(CommentedConfig config, String name) {
        if (!config.contains(name + ".components")) {
            throw new IllegalArgumentException("Missing required property: " + name);
        }
        var components = parsePrimitiveTypes(config, name + ".components");
        if (components.length != 1 || components[0] != PrimitiveType.TYPE) {
            throw new IllegalArgumentException("Malformed components for required property: " + name + ", expected TYPE component only");
        }
        return name;
    }

    /**
     * Attempts to parse an array of {@link ConfigRule}s list from the specified {@link CommentedConfig} object.
     *
     * @param config
     *         the configuration object to attempt to parse from.
     * @param name
     *         the name of the configuration to parse.
     *
     * @return the parsed array of {@link ConfigRule}s list objects.
     */
    public static List<ConfigRule>[] parseConfigRules(CommentedConfig config, String name) {
        var array = config.<List<List<String>>>get(name);
        if (array == null) {
            return ConfigProperty.NO_RULES;
        }
        var rules = new List[array.size()];
        for (var index = 0; index < array.size(); index++) {
            var types = array.get(index);
            var mapped = new ArrayList<ConfigRule>(types.size());
            for (var typeName : types) {
                mapped.add(parseConfigRule(typeName));
            }
            rules[index] = mapped;
        }
        return rules;
    }

    /**
     * Parses a single configuration rule from the specified raw text.
     *
     * @param raw
     *         the raw text to extract the single configuration rule from.
     *
     * @return the extracted {@link ConfigRule} type object instance.
     */
    private static ConfigRule parseConfigRule(String raw) {
        var lParenIndex = raw.indexOf('(');
        var ruleName = lParenIndex > 0 ? raw.substring(0, lParenIndex) : raw;
        switch (ruleName) {
            case "RANGE": {
                var arguments = extractArguments(RANGE_RULE_PATTERN, raw);
                if (arguments == null) {
                    throw new IllegalArgumentException("Malformed arguments for configuration rule:" + raw);
                }
                return new ConfigRangeRule(Integer.parseInt(arguments[0]), Integer.parseInt(arguments[1]));
            }
            case "REQUIRE": {
                var arguments = extractArguments(REQUIRE_RULE_PATTERN, raw);
                if (arguments == null) {
                    throw new IllegalArgumentException("Malformed arguments for configuration rule:" + raw);
                }
                return new ConfigRequireRule(arguments[0]);
            }
            default:
                return ConfigRules.valueOf(raw);
        }
    }

    /**
     * Extracts the arguments from the specified text using the specified {@link Pattern pattern}.
     *
     * @param pattern
     *         the pattern to to use for extracting the arguments.
     * @param text
     *         the text to extract the arguments from.
     *
     * @return the extract arguments as a {@link String} array or {@code null} if we failed to extract the arguments.
     */
    private static String[] extractArguments(Pattern pattern, String text) {
        var matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        var arguments = new String[matcher.groupCount()];
        for (int index = 0; index < arguments.length; index++) {
            arguments[index] = matcher.group(1 + index);
        }
        return arguments;
    }

    /**
     * Creates a {@link Pattern} object that matches the specified rule with the specified parameters.
     *
     * @param ruleName
     *         the rule name that we want to match in the pattern.
     * @param parameters
     *         the parameters of the rule that we want to match.
     *
     * @return the created {@link Pattern} object.
     */
    private static Pattern createConfigRulePattern(String ruleName, Class<?>... parameters) {
        final var IGNORE_WHIETSPACE = "(?:\\s*)";
        var builder = new StringBuilder();
        builder.append(ruleName);
        builder.append(IGNORE_WHIETSPACE);
        builder.append("\\(");
        builder.append(IGNORE_WHIETSPACE);
        for (int index = 0; index < parameters.length; index++) {
            if (index != 0) {
                builder.append(IGNORE_WHIETSPACE);
                builder.append(",");
                builder.append(IGNORE_WHIETSPACE);
            }
            var parameter = ReflectionUtil.box(parameters[index]);
            builder.append("(");
            if (parameter == Integer.class) {
                builder.append("[-+]?[0-9]+");
            } else if (parameter == String.class) {
                builder.append("[a-zA-Z0-9_]+");
            } else {
                throw new IllegalArgumentException("Unrecognized config rule parameter type: " + parameter.getSimpleName());
            }
            builder.append(")");
        }
        builder.append(IGNORE_WHIETSPACE);
        builder.append("\\)");
        return Pattern.compile(builder.toString());
    }

    /**
     * Attempts to parse an array of {@link PrimitiveType} from the specified {@link CommentedConfig} object.
     *
     * @param config
     *         the configuration object to attempt to parse from.
     * @param name
     *         the name of the configuration to parse.
     *
     * @return the parsed array {@link PrimitiveType} object.
     */
    public static PrimitiveType[] parsePrimitiveTypes(CommentedConfig config, String name) {
        var types = config.<List<String>>get(name);
        if (types == null) {
            return new PrimitiveType[0];
        }
        var mapped = new PrimitiveType[types.size()];
        for (var index = 0; index < types.size(); index++) {
            var typeName = types.get(index);
            mapped[index] = PrimitiveType.valueOf(typeName);
        }
        return mapped;
    }

    /**
     * Attempts to parse an array of {@link Type} from the specified {@link CommentedConfig} object.
     *
     * @param config
     *         the configuration object to attempt to parse from.
     * @param name
     *         the name of the configuration to parse.
     *
     * @return the parsed array {@link Type} object.
     */
    public static Type[] parseTypes(CommentedConfig config, String name) {
        var types = config.<List<String>>get(name);
        if (types == null) {
            return new Type[0];
        }
        var mapped = new Type[types.size()];
        for (var index = 0; index < types.size(); index++) {
            var typeName = types.get(index);
            if (typeName.endsWith("ARRAY")) {
                mapped[index] = new ArrayReference(PrimitiveType.valueOf(typeName.substring(0, typeName.length() - 5)), index);
            } else {
                mapped[index] = PrimitiveType.valueOf(typeName);
            }
        }
        return mapped;
    }

    private ProjectConfig() {
        // NOOP
    }
}
