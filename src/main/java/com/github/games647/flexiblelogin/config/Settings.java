/*
 * This file is part of FlexibleLogin
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2018 contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.flexiblelogin.config;

import com.github.games647.flexiblelogin.config.node.General;
import com.github.games647.flexiblelogin.config.node.TextConfig;
import com.github.games647.flexiblelogin.config.serializer.DurationSerializer;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMapper;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializerCollection;

import org.slf4j.Logger;
import org.spongepowered.api.config.ConfigDir;

@Singleton
public class Settings {

    private final Logger logger;
    private final Path dataFolder;

    private final ConfigurationOptions options = getConfigurationOptions();

    private ObjectMapper<General>.BoundInstance configMapper;
    private ObjectMapper<TextConfig>.BoundInstance textMapper;

    @Inject
    //We will place more than one config there (i.e. H2/SQLite database)
    Settings(Logger logger, @ConfigDir(sharedRoot = false) Path dataFolder) {
        this.logger = logger;
        this.dataFolder = dataFolder;

        try {
            configMapper = options.getObjectMapperFactory().getMapper(General.class).bindToNew();
            textMapper = options.getObjectMapperFactory().getMapper(TextConfig.class).bindToNew();
        } catch (ObjectMappingException objMappingExc) {
            logger.error("Invalid plugin structure", objMappingExc);
        }
    }

    private ConfigurationOptions getConfigurationOptions() {
        ConfigurationOptions defaults = ConfigurationOptions.defaults();

        TypeSerializerCollection serializers = defaults.getSerializers().newChild();
        serializers.registerType(TypeToken.of(Duration.class), new DurationSerializer());

        return defaults.setSerializers(serializers);
    }

    public void load() {
        Path configFile = dataFolder.resolve("config.conf");
        Path textFile = dataFolder.resolve("locale.conf");

        try {
            if (Files.notExists(dataFolder)) {
                Files.createDirectories(dataFolder);
            }

            if (Files.notExists(configFile)) {
                Files.createFile(configFile);
            }

            if (Files.notExists(textFile)) {
                Files.createFile(textFile);
            }
        } catch (IOException ioEx) {
            logger.error("Failed to create default config file", ioEx);
        }

        loadMapper(configMapper, configFile, options);
        loadMapper(textMapper, textFile, options
                .setHeader("Visit: https://github.com/games647/FlexibleLogin/wiki for community given templates"));
    }

    private <T> void loadMapper(ObjectMapper<T>.BoundInstance mapper, Path file, ConfigurationOptions options) {
        ConfigurationNode rootNode;
        if (mapper != null) {
            HoconConfigurationLoader loader = HoconConfigurationLoader.builder().setPath(file).build();
            try {
                rootNode = loader.load(options.setShouldCopyDefaults(true));
                ConfigurationNode hashNode = rootNode.getNode("hashAlgo");
                if ("bcrypt".equalsIgnoreCase(hashNode.getString())) {
                    hashNode.setValue("BCrypt");
                }

                //load the config into the object
                mapper.populate(rootNode);

                //add missing default values
                loader.save(rootNode);
            } catch (ObjectMappingException objMappingExc) {
                logger.error("Error loading the configuration", objMappingExc);
            } catch (IOException ioExc) {
                logger.error("Error saving the default configuration", ioExc);
            }
        }
    }

    public General getGeneral() {
        return configMapper.getInstance();
    }

    public TextConfig getText() {
        return textMapper.getInstance();
    }

    public Path getConfigDir() {
        return dataFolder;
    }
}
