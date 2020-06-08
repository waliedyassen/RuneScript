/*
 * Copyright (c) 2019 Walied K. Yassen, All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package me.waliedyassen.runescript.compiler;

import lombok.Getter;
import lombok.var;
import me.waliedyassen.runescript.CompilerError;
import me.waliedyassen.runescript.commons.stream.BufferedCharStream;
import me.waliedyassen.runescript.compiler.ast.AstScript;
import me.waliedyassen.runescript.compiler.ast.expr.AstExpression;
import me.waliedyassen.runescript.compiler.codegen.CodeGenerator;
import me.waliedyassen.runescript.compiler.codegen.InstructionMap;
import me.waliedyassen.runescript.compiler.codegen.optimizer.Optimizer;
import me.waliedyassen.runescript.compiler.codegen.optimizer.impl.DeadBlockOptimization;
import me.waliedyassen.runescript.compiler.codegen.optimizer.impl.DeadBranchOptimization;
import me.waliedyassen.runescript.compiler.codegen.optimizer.impl.NaturalFlowOptimization;
import me.waliedyassen.runescript.compiler.codegen.writer.CodeWriter;
import me.waliedyassen.runescript.compiler.codegen.writer.bytecode.BytecodeCodeWriter;
import me.waliedyassen.runescript.compiler.env.CompilerEnvironment;
import me.waliedyassen.runescript.compiler.idmapping.IdProvider;
import me.waliedyassen.runescript.compiler.lexer.Lexer;
import me.waliedyassen.runescript.compiler.lexer.token.Kind;
import me.waliedyassen.runescript.compiler.lexer.tokenizer.Tokenizer;
import me.waliedyassen.runescript.compiler.parser.ScriptParser;
import me.waliedyassen.runescript.compiler.semantics.SemanticChecker;
import me.waliedyassen.runescript.compiler.symbol.SymbolTable;
import me.waliedyassen.runescript.compiler.util.Operator;
import me.waliedyassen.runescript.compiler.util.Pair;
import me.waliedyassen.runescript.lexer.table.LexicalTable;
import me.waliedyassen.runescript.type.PrimitiveType;
import me.waliedyassen.runescript.type.StackType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the main class for the RuneScript language compiler module.
 *
 * @author Walied K. Yassen
 */
public final class Compiler {

    /**
     * The symbol table of the compiler.
     */
    @Getter
    private final SymbolTable symbolTable = new SymbolTable();

    /**
     * The code writer of the compiler.
     */
    private final CodeWriter<?> codeWriter;

    /**
     * The lexical table for our lexical analysis, it contains vario
     */
    @Getter
    private final LexicalTable<Kind> lexicalTable;

    /**
     * The compiler environment which is basically a user level symbol table.
     */
    @Getter
    private final CompilerEnvironment environment;

    /**
     * The instruction map to use for the
     */
    private final InstructionMap instructionMap;

    /**
     * The generated scripts optimizer.
     */
    private final Optimizer optimizer;

    /**
     * The id provider of the compiler.
     */
    private final IdProvider idProvider;

    /**
     * Whether or not the compiler supports long primitive type.
     */
    private final boolean supportsLongPrimitiveType;

    /**
     * Whether or not the compiler should override the symbols.
     */
    private final boolean overrideSymbols;


    // TODO: support supportsLongPrimitiveType in type checking.

    // TODO: Add context object so we don't have to manage
    // parameters constantly and update calls.

    /**
     * Constructs a new {@link Compiler} type object instance.
     *
     * @param environment               the environment of the compiler.
     * @param instructionMap            the instruction map to use for this compiler.
     * @param idProvider                the id provider of the compiler.
     * @param codeWriter                the code writer to use for the compiler.
     * @param supportsLongPrimitiveType whether or not the the compiler supports long primitive types.
     * @param overrideSymbols           whether or not the compiler should override the symbols.
     */
    private Compiler(CompilerEnvironment environment,
                     InstructionMap instructionMap,
                     IdProvider idProvider,
                     CodeWriter<?> codeWriter,
                     boolean supportsLongPrimitiveType,
                     boolean overrideSymbols) {
        if (!instructionMap.isReady()) {
            throw new IllegalArgumentException("The provided InstructionMap is not ready, please register all of core opcodes before using it.");
        }
        this.environment = environment;
        this.instructionMap = instructionMap;
        this.idProvider = idProvider;
        this.codeWriter = codeWriter;
        this.supportsLongPrimitiveType = supportsLongPrimitiveType;
        this.overrideSymbols = overrideSymbols;
        lexicalTable = createLexicalTable();
        optimizer = new Optimizer(instructionMap);
        optimizer.register(new NaturalFlowOptimization());
        optimizer.register(new DeadBranchOptimization());
        optimizer.register(new DeadBlockOptimization());
    }

    /**
     * Parses the Abstract Syntax Tree of the specified source file data.
     *
     * @param symbolTable the symbol table to use for parsing.
     * @param data        the source file data in bytes.
     * @return a {@link List list} of the parsed {@link AstScript} objects.
     */
    private List<AstScript> parseSyntaxTree(SymbolTable symbolTable, byte[] data) throws IOException {
        var stream = new BufferedCharStream(new ByteArrayInputStream(data));
        var tokenizer = new Tokenizer(lexicalTable, stream);
        var lexer = new Lexer(tokenizer);
        var parser = new ScriptParser(environment, symbolTable, lexer);
        var scripts = new ArrayList<AstScript>();
        while (lexer.remaining() > 0) {
            scripts.add(parser.script());
        }
        return scripts;
    }

    /**
     * Attempts to compile all of the source code specified in the {@link CompileInput input} object
     * and produce a {@link CompileResult} object which contains the compiled form of the object
     * and the associated errors produced during that compilation process.
     *
     * @param input the input object which contains the all of the source code that we want to compile.
     * @return the {@link CompileResult} object instance.
     * @throws IOException if somehow a problem occurred while writing or reading from the temporary streams.
     */
    public CompileResult compile(CompileInput input) throws IOException {
        return compile(input, true);
    }

    /**
     * Attempts to compile all of the source code specified in the {@link CompileInput input} object
     * and produce a {@link CompileResult} object which contains the compiled form of the object
     * and the associated errors produced during that compilation process.
     *
     * @param input    the input object which contains the all of the source code that we want to compile.
     * @param generate whether or not to run the code generation phase, this is useful for when we just want
     *                 to compile for errors only and we are not interested in the output.
     * @return the {@link CompileResult} object instance.
     * @throws IOException if somehow a problem occurred while writing or reading from the temporary streams.
     */
    public CompileResult compile(CompileInput input, boolean generate) throws IOException {
        var compilingScripts = new ArrayList<Pair<Object, AstScript>>();
        var errors = new ArrayList<Pair<Object, CompilerError>>();
        for (var source : input.getSourceData()) {
            try {
                var scripts = parseSyntaxTree(symbolTable, source.getValue());
                for (var script : scripts) {
                    compilingScripts.add(Pair.of(source.getKey(), script));
                }
            } catch (CompilerError e) {
                errors.add(Pair.of(source.getKey(), e));
            }
        }
        if (compilingScripts.isEmpty()) {
            return CompileResult.of(Collections.emptyList(), errors);
        }
        input.getVisitors().forEach(visitor -> {
            for (var script : compilingScripts) {
                script.getValue().accept(visitor);
            }
        });
        input.getFeedbacks().forEach(feedback -> {
            for (var script : compilingScripts) {
                feedback.onParserDone(script.getKey(), script.getValue());
            }
        });
        var symbolTable = this.symbolTable.createSubTable();
        // Perform semantic analysis checking on the parsed AST.
        var checker = new SemanticChecker(environment, symbolTable, overrideSymbols);
        checker.executePre(compilingScripts);
        checker.execute(compilingScripts);
        // Check if there is any compilation errors and throw them if there is any.
        if (checker.getErrors().size() > 0) {
            for (var pair : checker.getErrors()) {
                var key = pair.getKey();
                var value = pair.getValue();
                errors.add(Pair.of(key, value));
                compilingScripts.removeIf(pairInList -> pairInList.getValue() == value.getScript());
            }
        }
        var codeGenerator = new CodeGenerator(environment, symbolTable, instructionMap, environment.getHookTriggerType());
        // Compile all of the parsed and checked scripts into a bytecode format.
        var compiledScripts = new ArrayList<Pair<Object, CompiledScript>>();
        if (generate) {
            for (var pair : compilingScripts) {
                var script = pair.getValue();
                var trigger = environment.lookupTrigger(script.getTrigger().getText());
                var info = symbolTable.lookupScript(trigger, AstExpression.extractNameText(script.getName()));
                var generated = codeGenerator.visit(script);
                optimizer.run(generated);
                var output = codeWriter.write(generated);
                compiledScripts.add(Pair.of(pair.getKey(), new CompiledScript(generated.getName(), output, info)));
            }
        }
        return CompileResult.of(compiledScripts, errors);
    }

    /**
     * Create a new {@link LexicalTable} object and then register all of the lexical symbols for our RuneScript language
     * syntax.
     *
     * @return the created {@link LexicalTable} object.
     */
    public static LexicalTable<Kind> createLexicalTable() {
        // TODO: Cache LexicalTable
        var table = new LexicalTable<Kind>();
        // the keywords chunk.
        table.registerKeyword("true", Kind.BOOL);
        table.registerKeyword("false", Kind.BOOL);
        table.registerKeyword("if", Kind.IF);
        table.registerKeyword("else", Kind.ELSE);
        table.registerKeyword("while", Kind.WHILE);
        table.registerKeyword("return", Kind.RETURN);
        table.registerKeyword("case", Kind.CASE);
        table.registerKeyword("default", Kind.DEFAULT);
        table.registerKeyword("calc", Kind.CALC);
        table.registerKeyword("null", Kind.NULL);
        for (var type : PrimitiveType.values()) {
            if (type.isReferencable()) {
                table.registerKeyword(type.getRepresentation(), Kind.TYPE);
            }
            if (type.isDeclarable()) {
                table.registerKeyword("def_" + type.getRepresentation(), Kind.DEFINE);
            }
            if (type.isArrayable()) {
                table.registerKeyword(type.getRepresentation() + "array", Kind.ARRAY_TYPE);
            }
            if (type.getStackType() == StackType.INT) {
                table.registerKeyword("switch_" + type.getRepresentation(), Kind.SWITCH);
            }
        }
        // the separators chunk.
        table.registerSeparator('(', Kind.LPAREN);
        table.registerSeparator(')', Kind.RPAREN);
        table.registerSeparator('[', Kind.LBRACKET);
        table.registerSeparator(']', Kind.RBRACKET);
        table.registerSeparator('{', Kind.LBRACE);
        table.registerSeparator('}', Kind.RBRACE);
        table.registerSeparator(',', Kind.COMMA);
        table.registerSeparator('~', Kind.TILDE);
        table.registerSeparator('@', Kind.AT);
        table.registerSeparator('$', Kind.DOLLAR);
        table.registerSeparator('^', Kind.CARET);
        table.registerSeparator(':', Kind.COLON);
        table.registerSeparator(';', Kind.SEMICOLON);
        table.registerSeparator('.', Kind.DOT);
        table.registerSeparator('#', Kind.HASH);
        // register all of the operators.
        for (var operator : Operator.values()) {
            table.registerOperator(operator.getRepresentation(), operator.getKind());
        }
        return table;
    }

    /**
     * Returns a new {@link CompilerBuilder} object.
     *
     * @return the created {@link CompilerBuilder} object.
     */
    public static CompilerBuilder builder() {
        return new CompilerBuilder();
    }

    /**
     * A builder class for the {@link Compiler} type.
     *
     * @author Walied k. Yassen
     */
    public static final class CompilerBuilder {

        /**
         * The environment of the compiler.
         */
        private CompilerEnvironment environment;

        /**
         * The instruction map of the compiler.
         */
        private InstructionMap instructionMap;

        /**
         * The code writer of the compiler.
         */
        private CodeWriter<?> codeWriter;

        /**
         * Whether or not the compiler supports the long primitive type.
         */
        private boolean supportsLongPrimitiveType;

        /**
         * Whether or not the compiler should override the symbols.
         */
        private boolean overrideSymbols;

        /**
         * The {@link IdProvider} of the compiler.
         */
        private IdProvider idProvider;

        /**
         * Sets the environment object we are going to use for the compiler.
         *
         * @param environment the environment object to set.
         * @return this {@link CompilerBuilder} object instance.
         */
        public CompilerBuilder withEnvironment(CompilerEnvironment environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Sets the instruction map object we are going to use for the compiler.
         *
         * @param instructionMap the instruction map object to set.
         * @return this {@link CompilerBuilder} object instance.
         */
        public CompilerBuilder withInstructionMap(InstructionMap instructionMap) {
            this.instructionMap = instructionMap;
            return this;
        }

        /**
         * Sets whether or not the compiler that we are going to build should support the long primitive type.
         *
         * @param supportsLongPrimitiveType <code>true</code> if it supports the long primitive type otherwise
         *                                  <code>false</code>.
         * @return this {@link CompilerBuilder} object instance.
         */
        public CompilerBuilder withSupportsLongPrimitiveType(boolean supportsLongPrimitiveType) {
            this.supportsLongPrimitiveType = supportsLongPrimitiveType;
            return this;
        }

        /**
         * Sets the id provider that we are going to use for the compiler.
         *
         * @param idProvider the id provider of the compiler.
         * @return this {@link CompilerBuilder} object instance.
         */
        public CompilerBuilder withIdProvider(IdProvider idProvider) {
            this.idProvider = idProvider;
            return this;
        }

        /**
         * Sets whether or not the compiler that we are going to build should override the symbols in the symbol
         * tabl.
         *
         * @param overrideSymbols whether or not we should override symbols.
         * @return this {@link CompilerBuilder} object instance.
         */
        public CompilerBuilder withOverrideSymbols(boolean overrideSymbols) {
            this.overrideSymbols = overrideSymbols;
            return this;
        }

        /**
         * Sets the code writer that we are going to use for the compiler.
         *
         * @param codeWriter the code writer of the compiler.
         * @return this {@link CompilerBuilder} object instance.
         */
        public CompilerBuilder withCodeWriter(CodeWriter<?> codeWriter) {
            this.codeWriter = codeWriter;
            return this;
        }

        /**
         * Builds the {@link Compiler} object with the details configured in the builder.
         *
         * @return the built {@link Compiler} object.
         * @throws IllegalStateException if one or more of the configuration is invalid or missing.
         */
        public Compiler build() {
            if (instructionMap == null) {
                throw new IllegalStateException("You must provide an InstructionMap before performing build() operation");
            }
            if (idProvider == null) {
                throw new IllegalStateException("You must provide an IdProvider before performing build() operation");
            }
            if (environment == null) {
                environment = new CompilerEnvironment();
            }
            if (codeWriter == null) {
                codeWriter = new BytecodeCodeWriter(idProvider, supportsLongPrimitiveType);
            }
            return new Compiler(environment, instructionMap, idProvider, codeWriter, supportsLongPrimitiveType, overrideSymbols);
        }
    }
}
