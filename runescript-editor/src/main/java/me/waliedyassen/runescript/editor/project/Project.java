/*
 * Copyright (c) 2019 Walied K. Yassen, All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package me.waliedyassen.runescript.editor.project;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import me.waliedyassen.runescript.compiler.Compiler;
import me.waliedyassen.runescript.compiler.codegen.InstructionMap;
import me.waliedyassen.runescript.compiler.codegen.opcode.BasicOpcode;
import me.waliedyassen.runescript.compiler.codegen.opcode.CoreOpcode;
import me.waliedyassen.runescript.compiler.env.CompilerEnvironment;
import me.waliedyassen.runescript.compiler.lexer.token.Kind;
import me.waliedyassen.runescript.compiler.util.trigger.BasicTriggerType;
import me.waliedyassen.runescript.editor.Api;
import me.waliedyassen.runescript.editor.pack.manager.PackManager;
import me.waliedyassen.runescript.editor.pack.provider.impl.SQLitePackProvider;
import me.waliedyassen.runescript.editor.project.build.BuildPath;
import me.waliedyassen.runescript.editor.project.cache.Cache;
import me.waliedyassen.runescript.editor.util.JsonUtil;
import me.waliedyassen.runescript.editor.util.ex.PathEx;
import me.waliedyassen.runescript.editor.vfs.VFS;
import me.waliedyassen.runescript.type.PrimitiveType;
import me.waliedyassen.runescript.type.TupleType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A very basic project system that provides basic information such as the name and the build path directories.
 *
 * @author Walied K. Yassen
 */
@Slf4j
public final class Project {

    /**
     * The project information file name.
     */
    static final String FILE_NAME = ".rsproj";

    /**
     * The base directory {@link Path} of the project.
     */
    @Getter
    private final Path directory;

    /**
     * the name of the project.
     */
    @Getter
    @Setter
    private String name;

    /**
     * The build path of the project.
     */
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private BuildPath buildPath;

    /**
     * The pack manager of the project.
     */
    @Getter
    private PackManager packManager;

    /**
     * The virtual file system of the project.
     */
    @Getter
    private VFS vfs;

    /**
     * The compiler we are going to use for this project.
     */
    @Getter
    private Compiler compiler;

    /**
     * The instruction map of the project.
     */
    private InstructionMap instructionMap;

    /**
     * The compiler environment of the project.
     */
    private CompilerEnvironment compilerEnvironment;

    /**
     * The cache of the project.
     */
    @Getter
    private Cache cache;

    /**
     * The commands configuration path.
     */
    @Getter
    @Setter
    private String commandsPath;

    /**
     * The triggers configuration path.
     */
    @Getter
    @Setter
    private String triggersPath;

    /**
     * The instructions configuration path.
     */
    @Getter
    @Setter
    private String instructionsPath;

    /**
     * Whether or not the project supports long primitive type compilation.
     */
    @Getter
    @Setter
    private boolean supportsLongPrimitiveType;

    /**
     * Constructs a new {@link Project} type object instance.
     *
     * @param directory the root directory path of the project.
     */
    Project(Path directory) {
        this.directory = directory;
    }

    /**
     * Attempts to load the project information data from the local disk.
     *
     * @throws IOException if anything occurs during the loading procedure.
     */
    void loadData() throws IOException {
        // Read the node tree from the file.
        JsonNode root;
        try (var reader = Files.newBufferedReader(findProjectFile())) {
            root = JsonUtil.getMapper().reader().readTree(reader);
        }
        // Read the project general information.
        name = JsonUtil.getTextOrThrow(root, "name", "The project name cannot be null or empty");
        loadBuildPath(root);
        loadCompiler(root);
        supportsLongPrimitiveType = JsonUtil.getBooleanOrThrow(root, "supportsLongPrimitiveType", "The project supportsLongPrimitiveType cannot be null or empty");
        postLoad();
    }

    /**
     * Attempts to load the {@link BuildPath} object from the specified {@link JsonNode} root object.
     *
     * @param root the root node which contains the build path node.
     * @throws IOException if anything occurs during the loading procedure.
     */
    void loadBuildPath(JsonNode root) throws IOException {
        var object = JsonUtil.getObjectOrThrow(root, "build_path", "The build path object cannot be null");
        var sourcePath = JsonUtil.getTextOrThrow(object, "source", "The source directory cannot be null or empty");
        var packPath = JsonUtil.getTextOrThrow(object, "pack", "The pack directory cannot be null or empty");
        buildPath = new BuildPath(directory.resolve(sourcePath), directory.resolve(packPath));
        buildPath.ensureExistence();
    }

    /**
     * Loads the compiler configuration of the project.
     *
     * @param root the root node to load the configuration from.
     */
    void loadCompiler(JsonNode root) {
        var object = JsonUtil.getObjectOrThrow(root, "compiler", "The compiler object cannot be null");
        instructionsPath = JsonUtil.getTextOrThrow(object, "instructions", "The instructions map cannot be null");
        triggersPath = JsonUtil.getTextOrThrow(object, "commands", "The instructions map cannot be null");
        commandsPath = JsonUtil.getTextOrThrow(object, "triggers", "The instructions map cannot be null");
        reloadCompiler();
    }

    /**
     * Reloads the compiler configuration of the project.
     */
    public void reloadCompiler() {
        compilerEnvironment = new CompilerEnvironment();
        instructionMap = new InstructionMap();
        loadInstructions(instructionsPath);
        loadTriggers(triggersPath);
        compiler = Compiler.builder()
                .withEnvironment(compilerEnvironment)
                .withInstructionMap(instructionMap)
                .withSupportsLongPrimitiveType(false)
                .build();
        loadCommands(commandsPath);
    }

    /**
     * Loads the instructions configuration of the project.
     *
     * @param path the path which leads to the instructions configuration.
     */
    @SneakyThrows
    void loadInstructions(String path) {
        var file = (File) null;
        if (path.startsWith("*")) {
            path = path.substring(1);
            if ("osrs_default".equals(path)) {
                file = new File(getClass().getResource("osrs_default_instructions.toml").toURI());
            } else {
                throw new IllegalStateException("Unrecognised macro: " + path);
            }
        } else {
            file = new File(path);
        }
        if (!file.exists()) {
            throw new IllegalStateException("The specified instructions file does not exist");
        }
        try (var config = CommentedFileConfig.of(file)) {
            config.load();
            for (var entry : config.entrySet()) {
                var key = entry.getKey();
                var value = (CommentedConfig) entry.getValue();
                var coreOpcode = CoreOpcode.valueOf(key.toUpperCase());
                var opcode = value.getInt("opcode");
                var large = value.getOrElse("large", false);
                instructionMap.registerCore(coreOpcode, opcode, large);
            }

        }
    }

    /**
     * Loads the triggers configuration of the project.
     *
     * @param path the path which leads to the triggers configuration.
     */
    @SneakyThrows
    void loadTriggers(String path) {
        var file = (File) null;
        if (path.startsWith("*")) {
            path = path.substring(1);
            if ("osrs_default".equals(path)) {
                file = new File(getClass().getResource("osrs_default_triggers.toml").toURI());
            } else {
                throw new IllegalStateException("Unrecognised macro: " + path);
            }
        } else {
            file = new File(path);
        }
        if (!file.exists()) {
            throw new IllegalStateException("The specified instructions file does not exist");
        }
        try (var config = CommentedFileConfig.of(file)) {
            config.load();
            for (var entry : config.entrySet()) {
                var name = entry.getKey();
                var value = (CommentedConfig) entry.getValue();
                var operator = value.getOptionalEnum("operator", Kind.class).orElse(null);
                var opcode = value.getOptionalEnum("opcode", CoreOpcode.class).orElse(null);
                var supportArgument = value.getOrElse("support_arguments", false);
                var supportReturn = value.getOrElse("support_returns", false);
                var argumentTypes = value.contains("arguments") ? ProjectConfig.parseTypes(config, "arguments") : null;
                var returnTypes = value.contains("returns") ? ProjectConfig.parseTypes(config, "returns") : null;
                compilerEnvironment.registerTrigger(new BasicTriggerType(name, operator, opcode, supportArgument, argumentTypes, supportReturn, returnTypes));
            }
        }
    }


    /**
     * Loads the commands configuration of the project.
     *
     * @param path the path which leads to the commands configuration.
     */
    @SneakyThrows
    void loadCommands(String path) {
        var file = (File) null;
        if (path.startsWith("*")) {
            path = path.substring(1);
            if ("osrs_default".equals(path)) {
                file = new File(getClass().getResource("osrs_default_commands.toml").toURI());
            } else {
                throw new IllegalStateException("Unrecognised macro: " + path);
            }
        } else {
            file = new File(path);
        }
        if (!file.exists()) {
            throw new IllegalStateException("The specified instructions file does not exist");
        }
        try (var config = CommentedFileConfig.of(file)) {
            config.load();
            for (var entry : config.entrySet()) {
                var name = entry.getKey();
                var value = (CommentedConfig) entry.getValue();
                var opcode = value.getInt("opcode");
                var type = ProjectConfig.parseTypes(value, "type");
                var arguments = ProjectConfig.parseTypes(value, "arguments");
                var alternative = value.getOrElse("alternative", false);
                var hook = value.getOrElse("hook", false);
                compiler.getSymbolTable().defineCommand(new BasicOpcode(opcode, false), name, type.length > 1 ? new TupleType(type) : type.length == 0 ? PrimitiveType.VOID : type[0], arguments, hook, alternative);
            }
        }
    }

    /**
     * Gets called after the project has been loaded.
     */
    private void postLoad() {
        packManager = new PackManager(new SQLitePackProvider(buildPath.getPackDirectory().toAbsolutePath()));
        vfs = new VFS(directory);
        loadCache();
    }

    /**
     * Loads the cache of the project.
     */
    private void loadCache() {
        var rootPath = resolveRsPath();
        var cacheFile = rootPath.resolve("cache.bin");
        cache = new Cache(this);
        if (Files.exists(cacheFile)) {
            try (var stream = new DataInputStream(Files.newInputStream(cacheFile))) {
                cache.read(stream);
            } catch (IOException e) {
                log.error("An error occurred while loading the project cache", e);
            }
        }
        try {
            cache.diff(buildPath.getSourceDirectory());
        } catch (IOException e) {
            throw new ProjectException("Failed to generate the cache diff for the project cache", e);
        }
        updateErrors();
    }

    /**
     * Updates all of the errors of the project.
     */
    public void updateErrors() {
        var errorsView = Api.getApi().getUi().getErrorsView();
        errorsView.clearErrors();
        for (var cachedFile : cache.getFilesByPath().values()) {
            var path = cachedFile.getFullPath();
            for (var cachedError : cachedFile.getErrors()) {
                var line = cachedError.getRange().getStart().getLine();
                var column = cachedError.getRange().getStart().getColumn();
                errorsView.addError(path, line, column, cachedError.getMessage());
            }
        }
    }

    /**
     * Refreshes the errors list of the cached file at the specified {@link Path path}. This does only update
     * the UI errors view, it does not alter the cached errors list.
     *
     * @param path the path of the cached file to grab the errors from.
     */
    public void updateErrors(Path path) {
        var errorsPath = PathEx.normaliseToString(buildPath.getSourceDirectory(), path);
        updateErrors(errorsPath);
    }

    /**
     * Refreshes the errors list of the cached file at the specified {@code path}. This does only update
     * the UI errors view, it does not alter the cached errors list.
     *
     * @param path the path of the cached file to grab the errors from.
     */
    public void updateErrors(String path) {
        var errorsView = Api.getApi().getUi().getErrorsView();
        errorsView.removeErrorForPath(path);
        var cachedFile = cache.getFilesByPath().get(path);
        if (cachedFile == null) {
            return;
        }
        for (var cachedError : cachedFile.getErrors()) {
            var line = cachedError.getRange().getStart().getLine();
            var column = cachedError.getRange().getStart().getColumn();
            errorsView.addError(path, line, column, cachedError.getMessage());
        }
    }

    /**
     * Saves the cache of the project.
     */
    public void saveCache() {
        var rootPath = resolveRsPath();
        var cacheFile = rootPath.resolve("cache.bin");
        try (var stream = new DataOutputStream(Files.newOutputStream(cacheFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE))) {
            cache.write(stream);
        } catch (IOException e) {
            log.error("An error occurred while writing the project cache", e);
        }
    }

    /**
     * Resolves the root .rs directory path and create it if it does not exist.
     *
     * @return the {@link Path} object of the .rs directory.
     * @throws ProjectException if the creation of the .rs directory failed.
     */
    private Path resolveRsPath() {
        var path = directory.resolve(".rs/");
        if (!Files.exists(path)) {
            try {
                Files.createDirectory(path);
            } catch (IOException e) {
                throw new ProjectException("Failed to create the .rs directory in the project root directory", e);
            }
        }
        return path;
    }

    /**
     * Saves the information data of the project to the local disk.
     *
     * @throws IOException if anything occurs during the saving procedure.
     */
    void saveData() throws IOException {
        // Create the project root node.
        var root = JsonUtil.getMapper().createObjectNode();
        // Serialise the general information.
        root.put("name", name);
        // Serialise the build path information.
        var buildPath = root.putObject("build_path");
        buildPath.put("source", directory.relativize(this.buildPath.getSourceDirectory()).toString());
        buildPath.put("pack", directory.relativize(this.buildPath.getPackDirectory()).toString());
        var compiler = root.putObject("compiler");
        compiler.put("instructions", instructionsPath);
        compiler.put("triggers", triggersPath);
        compiler.put("commands", commandsPath);
        root.put("supportsLongPrimitiveType", supportsLongPrimitiveType);
        // Write the serialised data into the project file.
        JsonUtil.getMapper().writerWithDefaultPrettyPrinter().writeValue(findProjectFile().toFile(), root);
        // Save the cache of the project to the local disk.
        cache.performSaving();
    }

    /**
     * Closes the virtual file system of the project.
     */
    public void closeVfs() {
        try {
            vfs.close();
        } finally {
            vfs = null;
        }
    }

    /**
     * Finds the {@code .rspoj} file of the project.
     *
     * @return the {@link Path} object which leads to that file.
     */
    private Path findProjectFile() {
        return directory.resolve(FILE_NAME);
    }
}
