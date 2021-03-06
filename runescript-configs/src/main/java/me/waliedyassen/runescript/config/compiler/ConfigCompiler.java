/*
 * Copyright (c) 2020 Walied K. Yassen, All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package me.waliedyassen.runescript.config.compiler;

import lombok.Getter;
import lombok.var;
import me.waliedyassen.runescript.commons.stream.BufferedCharStream;
import me.waliedyassen.runescript.compiler.CompilerBase;
import me.waliedyassen.runescript.compiler.CompilerError;
import me.waliedyassen.runescript.compiler.Input;
import me.waliedyassen.runescript.compiler.Output;
import me.waliedyassen.runescript.compiler.error.ErrorReporter;
import me.waliedyassen.runescript.compiler.idmapping.IDManager;
import me.waliedyassen.runescript.compiler.lexer.table.LexicalTable;
import me.waliedyassen.runescript.compiler.symbol.SymbolTable;
import me.waliedyassen.runescript.config.binding.ConfigBinding;
import me.waliedyassen.runescript.config.codegen.CodeGenerator;
import me.waliedyassen.runescript.config.lexer.Lexer;
import me.waliedyassen.runescript.config.lexer.Tokenizer;
import me.waliedyassen.runescript.config.lexer.token.Kind;
import me.waliedyassen.runescript.config.semantics.SemanticChecker;
import me.waliedyassen.runescript.config.syntax.ConfigSyntax;
import me.waliedyassen.runescript.config.syntax.SyntaxParser;
import me.waliedyassen.runescript.type.primitive.PrimitiveType;
import me.waliedyassen.runescript.util.CollectorsEx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * Represents the RuneScript configurations compiler.
 *
 * @author Walied K. Yassen
 */
public final class ConfigCompiler extends CompilerBase<ConfigSyntax, CompiledConfigUnit> {

    /**
     * A map of all the bindings that can be used by this compiler mapped by their extension.
     */
    @Getter
    private final Map<String, ConfigBinding> bindings = new HashMap<>();

    /**
     * The lexical table of the configuration compiler.
     */
    @Getter
    private final LexicalTable<Kind> lexicalTable = createLexicalTable();

    /**
     * The symbol table of the configuration compiler.
     */
    @Getter
    private final SymbolTable symbolTable;

    /**
     * Whether or not to allow overriding of symbols.
     */
    @Getter
    private final boolean allowOverriding;

    /**
     * Constructs a new {@link ConfigCompiler} type object instance.
     *
     * @param idManager       the ids provider for the compiler.
     * @param symbolTable     the symbol table for the compiler.
     * @param allowOverriding whether or not to allow overriding of symbols.
     */
    public ConfigCompiler(IDManager idManager, SymbolTable symbolTable, boolean allowOverriding) {
        super(idManager);
        this.symbolTable = symbolTable;
        this.allowOverriding = allowOverriding;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Output<ConfigSyntax, CompiledConfigUnit> compile(Input input) throws IOException {
        var symbolTable = this.symbolTable.createSubTable();
        var output = new Output<ConfigSyntax, CompiledConfigUnit>();
        for (var sourceFile : input.getSourceFiles()) {
            var binding = bindings.get(sourceFile.getExtension());
            if (binding == null) {
                throw new IllegalStateException("Missing configuration binding for file extension: " + sourceFile.getExtension());
            }
            var stream = new BufferedCharStream(new ByteArrayInputStream(sourceFile.getContent()));
            try {
                var errorReporter = new ErrorReporter();
                var tokenizer = new Tokenizer(errorReporter, lexicalTable, stream);
                var lexer = new Lexer(tokenizer);
                var parser = new SyntaxParser(errorReporter, lexer);
                var configs = parser.configs();
                errorReporter.getErrors().forEach(error -> output.addError(sourceFile, error));
                if (configs.length == 0) {
                    continue;
                }
                for (var config : configs) {
                    var compiledUnit = new CompiledConfigUnit(binding);
                    compiledUnit.setSyntax(config);
                    output.addUnit(sourceFile, compiledUnit);
                }
            } catch (CompilerError error) {
                output.addError(sourceFile, error);
            }
        }
        var mapped = output.getCompiledFiles().stream().collect(groupingBy(Function.identity(), CollectorsEx.flatMapping(file -> file.getUnits().stream().map(CompiledConfigUnit::getSyntax), toList())));
        var checker = new SemanticChecker(symbolTable, allowOverriding);
        for (var entry : mapped.entrySet()) {
            var compiledFile = entry.getKey();
            if (compiledFile.isErroneous()) {
                continue;
            }
            var binding = bindings.get(compiledFile.getExtension());
            checker.executePre(entry.getValue(), binding);
            compiledFile.getErrors().addAll(checker.getErrors());
            checker.getErrors().clear();
        }
        for (var entry : mapped.entrySet()) {
            var compiledFile = entry.getKey();
            if (compiledFile.isErroneous()) {
                continue;
            }
            var binding = bindings.get(compiledFile.getExtension());
            checker.execute(entry.getValue(), binding);
            compiledFile.getErrors().addAll(checker.getErrors());
            checker.getErrors().clear();
        }
        if (input.isRunIdGeneration()) {
            // We want to assign IDs for all of the nodes, including the erroneous ones
            // because they could be referenced from non erroneous.
            for (var entry : mapped.keySet()) {
                var binding = bindings.get(entry.getExtension());
                var type = binding.getGroup().getType();
                for (var unit : entry.getUnits()) {
                    idManager.findOrCreateConfig(type, unit.getSyntax().getName().getText());
                }
            }
        }
        if (input.isRunCodeGeneration()) {
            for (var entry : mapped.keySet()) {
                if (entry.isErroneous()) {
                    continue;
                }
                var binding = bindings.get(entry.getExtension());
                var codeGen = new CodeGenerator(idManager, symbolTable, binding);
                for (var unit : entry.getUnits()) {
                    var binaryConfig = codeGen.visit(unit.getSyntax());
                    unit.setBinaryConfig(binaryConfig);
                }
            }
        }
        return output;
    }

    /**
     * Registers a new configuration binding into this compiler.
     *
     * @param extension the configuration file extension.
     * @param binding   the configuration binding.
     */
    public void registerBinding(String extension, ConfigBinding binding) {
        extension = extension.toLowerCase();
        if (bindings.containsKey(extension)) {
            throw new IllegalArgumentException("The specified binding extension is already registered: " + extension);
        }
        bindings.put(extension, binding);
    }

    /**
     * Looks-up for the {@link ConfigBinding} with the specified {@code extension}.
     *
     * @param extension the extension of the config binding that we want.
     * @return the {@link ConfigBinding} object if it was found otherwise {@code null}.
     */
    public ConfigBinding lookupBinding(String extension) {
        return bindings.get(extension);
    }

    /**
     * Create a new {@link LexicalTable} object and then register all of the lexical symbols for our configurations
     * syntax.
     *
     * @return the created {@link LexicalTable} object.
     */
    public static LexicalTable<Kind> createLexicalTable() {
        var table = new LexicalTable<Kind>();
        table.registerSeparator('[', Kind.LBRACKET);
        table.registerSeparator(']', Kind.RBRACKET);
        table.registerSeparator('=', Kind.EQUAL);
        table.registerSeparator('^', Kind.CARET);
        table.registerSeparator(',', Kind.COMMA);
        table.registerKeyword("yes", Kind.BOOLEAN);
        table.registerKeyword("no", Kind.BOOLEAN);
        table.registerKeyword("true", Kind.BOOLEAN);
        table.registerKeyword("false", Kind.BOOLEAN);
        for (PrimitiveType type : PrimitiveType.values()) {
            if (type.isReferencable()) {
                table.registerKeyword(type.getRepresentation(), Kind.TYPE);
            }
        }
        return table;
    }
}
