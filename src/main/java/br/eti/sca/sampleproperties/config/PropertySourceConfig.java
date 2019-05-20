package br.eti.sca.sampleproperties.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Tnis class is responsable for reload properties file
 */
    public final class PropertySourceConfig extends PropertySourcesPlaceholderConfigurer implements EnvironmentAware {

        private static final Logger log = LoggerFactory.getLogger(PropertySourceConfig.class);

        private static final String APP_CONFIG_NAME = "application.properties";

        private static final String APP_CONFIG_PATH = Optional.ofNullable(System.getProperty("CONFIG_DIR"))
                .orElseThrow(() -> new RuntimeException("CONFIG_DIR must be config."));

        private FileSystemResource fileSystemResource;

        private Environment environment;

    public PropertySourceConfig() {

        super();

        configureWatcher();
    }

    @Override
    public void setEnvironment(Environment environment) {
        super.setEnvironment(environment);
        // EVN
        this.environment = environment;
        this.fileSystemResource = new FileSystemResource(new File(APP_CONFIG_PATH, APP_CONFIG_NAME));

        Properties properties = new Properties();
        try {
            PropertiesLoaderUtils.fillProperties(properties, this.fileSystemResource);
        } catch (IOException e) {
            throw new RuntimeException("Fail load properties from resources");
        }
    }

    /**
     * Prepare WatchService using Java NIO When properties change, all properties
     * will be reloaded
     */
    private void configureWatcher() {
        try {
            final WatchService watchService = FileSystems.getDefault().newWatchService();
            log.info("WatchService INITIATED");

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    Path path = Paths.get(APP_CONFIG_PATH);
                    path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                    WatchKey watchKey;
                    while ((watchKey = watchService.take()) != null) {

                        watchKey.pollEvents().stream().forEach(event -> {
                            if (event.context().toString().equals(APP_CONFIG_NAME)) {
                                //
                                log.info(String.format("File %s changed", APP_CONFIG_NAME));
                                this.prepareProperies();
                            }
                        });

                        watchKey.reset();
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            });

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    try {
                        watchService.close();
                        log.info("WatchService CLOSE");
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
            });
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void prepareProperies() {
        if (environment != null) {
            final ConfigurableEnvironment configEnv = ((ConfigurableEnvironment) environment);

            final MutablePropertySources propSources = configEnv.getPropertySources();

            Optional<PropertySource<?>> appConfig = StreamSupport.stream(propSources.spliterator(), false)
                    .filter(ps -> ps.getName().matches("^.*applicationConfig.*file:.*$")).findFirst();

            if (appConfig.isPresent()) {
                String name = appConfig.get().getName();
                Properties properties = new Properties();
                try {
                    PropertiesLoaderUtils.fillProperties(properties, fileSystemResource);
                } catch (IOException e) {
                    throw new RuntimeException("Fail load properties from resources");
                }
                propSources.replace(name, new PropertiesPropertySource(name, properties));
            }
        }
    }

}