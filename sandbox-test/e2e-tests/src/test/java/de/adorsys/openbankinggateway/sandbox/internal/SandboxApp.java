package de.adorsys.openbankinggateway.sandbox.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class launches spring-fat jar in its own class loader (we assume {@link SandboxApp#runnable()}
 * is called in own thread). This way all services are launched in same JVM and we can easily manipulate
 * their state. Shared context (except system classes!) is not a problem, because it is shaded by Spring - each
 * application has its own class loader and context.
 *
 * Each application will receive spring profile based on its lowercase name. I.e. LEDGERS_GATEWAY assumes to have
 * sandbox/application-test-ledgers-gateway.yml as well as common sandbox/application-test-common.yml
 * property files on resources path.
 *
 * You can specify database type using System property / Environment variable (in order of precedence) `DB_TYPE`:
 * 1. DB_TYPE = LOCAL_POSTGRES - will connect to local postgres db on port 5432
 * 2. (DEFAULT) DB_TYPE = TEST_CONTAINERS_POSTGRES - will start Postgres using TestContainers and use port 15432 for it
 * (you need to prepare schema and users - see prepare-postgres.sql)
 */
@Slf4j
@Getter
public enum SandboxApp {

    LEDGERS_GATEWAY("gateway-app-5.4.jar"), // adorsys/xs2a-connector-examples
    ASPSP_PROFILE("aspsp-profile-server-5.4-exec.jar"), // adorsys/xs2a-aspsp-profile
    CONSENT_MGMT("cms-standalone-service-5.4.jar"), // adorsys/xs2a-consent-management
    ONLINE_BANKING("online-banking-app-1.8.jar"), // adorsys/xs2a-online-banking
    TPP_REST("tpp-rest-server-1.8.jar"), // adorsys/xs2a-tpp-rest-server
    CERT_GENERATOR("certificate-generator-1.8.jar"), // adorsys/xs2a-certificate-generator
    LEDGERS_APP("ledgers-app-2.1.jar"), // adorsys/ledgers
    ONLINE_BANKING_UI("adorsys/xs2a-online-banking-ui:1.8", true); // adorsys/xs2a-online-banking-ui

    public static final String DB_TYPE = "DB_TYPE";
    public static final String TEST_CONTAINERS_POSTGRES = "test-containers-postgres";


    private static final Pattern REFERENCE_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9.]+)}");
    private static final Pattern PORT_PATTERN = Pattern.compile("^.+:([0-9]+)/.+$");
    private static final Map<String, String> EXPOSED_VARIABLES = ImmutableMap.of(
            "dockerHost", "host.docker.internal"
    );
    private static final AtomicBoolean DB_STARTED = new AtomicBoolean();
    private static final ObjectMapper YML = new ObjectMapper(new YAMLFactory());

    private final AtomicReference<ClassLoader> loader = new AtomicReference<>();
    private final AtomicReference<GenericContainer> dockerContainer = new AtomicReference<>();

    private final boolean dockerized;
    private final String jarOrDockerFile;
    private final String mainClass;

    SandboxApp(String jarOrDockerFile) {
        this.jarOrDockerFile = jarOrDockerFile;
        this.mainClass = null;
        this.dockerized = false;
    }

    SandboxApp(String jarOrDockerFile, String mainClass) {
        this.jarOrDockerFile = jarOrDockerFile;
        this.mainClass = mainClass;
        this.dockerized = false;
    }

    SandboxApp(String jarOrDockerImage, boolean dockerized) {
        this.jarOrDockerFile = jarOrDockerImage;
        this.mainClass = null;
        this.dockerized = dockerized;
    }

    @SneakyThrows
    public Runnable runnable() {
        return new SandboxRunnable(this, this::doRun);
    }

    @SneakyThrows
    public boolean isReadyToUse() {
        JsonNode tree = YML.readTree(Resources.getResource("sandbox/application-test-common.yml"));
        String pointer = "/common/apps/local/" + name().toLowerCase().replaceAll("_", "") + "/port";
        JsonNode port = tree.at(pointer);

        if (!port.isInt()) {
            throw new IllegalStateException("Port for " + pointer + " should be specified");
        }

        try (Socket ignored = new Socket("localhost", port.asInt())) {
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static boolean allReadyToUse() {
        return SandboxApp.values().length ==
                Arrays.stream(SandboxApp.values()).map(SandboxApp::isReadyToUse).filter(it -> it).count();
    }

    @SneakyThrows
    Method getMainEntryPoint(ClassloaderWithJar classloaderWithJar) {
        String jarPath = classloaderWithJar.getJarPath();
        URLClassLoader loader = classloaderWithJar.getLoader();

        Class<?> cls = loader.loadClass(getMainClass(jarPath));
        return cls.getDeclaredMethod("main", String[].class);
    }

    @SneakyThrows
    String getMainClass(String jarPath) {
        if (null != mainClass) {
            return mainClass;
        }

        JarFile jarFile = new JarFile(jarPath);
        Manifest manifest = jarFile.getManifest();
        Attributes attributes = manifest.getMainAttributes();
        return attributes.getValue(Attributes.Name.MAIN_CLASS);
    }

    private void doRun() {
        if (dockerized) {
            doRunDocker();
        } else {
            doRunJar();
        }
    }

    private void doRunDocker() {
        String oldName = Thread.currentThread().getName();
        Thread.currentThread().setName(name());
        try {
            Map<String, String> envVars = new HashMap<>();
            // sandbox/application-test-common.yml
            JsonNode commonConfig = YML.readTree(
                    Resources.getResource("sandbox/application-test-common.yml")
            );
            // sandbox/application-test-${appName}.yml
            String pointer = "/env";
            JsonNode appConfig = YML.readTree(
                    Resources.getResource("sandbox/application-" + testProfileName() +".yml")
            );
            JsonNode declaredVars = appConfig.at(pointer);
            declaredVars.fields().forEachRemaining(entry ->
                readYamlVariableToMap(entry, commonConfig, envVars)
            );

            int desiredPort = Integer.parseInt(readVariableFromConfig(appConfig.at("/port"), commonConfig));
            FixedHostPortGenericContainer container = new FixedHostPortGenericContainer(jarOrDockerFile);
            container.withFixedExposedPort(desiredPort, desiredPort);

            // Hack for linux as it does not have `host.docker.internal` so directly placing into host network
            if (System.getProperty("os.name").toLowerCase().contains("linux")) {
                container.withExtraHost(
                    "host.docker.internal",
                    new InetSocketAddress(0).getAddress().getHostAddress()
                );

                container.withNetworkMode("host");
            }

            container.withEnv(envVars).waitingFor(Wait.defaultWaitStrategy());

            container.start();
            this.dockerContainer.set(container);
        } catch (IOException | RuntimeException ex) {
            log.error("{} from {} Dockerfile has terminated exceptionally", name(), jarOrDockerFile, ex);
        } finally {
            Thread.currentThread().setName(oldName);
        }
    }

    private void readYamlVariableToMap(
            Map.Entry<String, JsonNode> entry, JsonNode configSource, Map<String, String> envVars
    ) {
        envVars.put(entry.getKey(), readVariableFromConfig(entry.getValue(), configSource));
    }

    private boolean hasReference(JsonNode entry) {
        return entry.isTextual()
                && entry.textValue().contains("${")
                && entry.textValue().contains("}");
    }

    private String readVariableFromConfig(JsonNode value, JsonNode configSource) {
        if (!hasReference(value)) {
            return value.asText();
        }

        String toParse = value.textValue();
        String result = value.textValue();
        Matcher matcher = REFERENCE_PATTERN.matcher(toParse);
        while(matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); ++i) {
                String variableName = matcher.group(i);
                String foundValue;
                if (EXPOSED_VARIABLES.containsKey(variableName)) {
                    foundValue = EXPOSED_VARIABLES.get(variableName);
                } else {
                    String path = variableName.replaceAll("\\.", "/");
                    foundValue = readVariableFromConfig(configSource.at("/" + path), configSource);
                }

                result = result.replaceAll(
                        Pattern.quote("${" + variableName + "}"),
                        foundValue
                );
            }
        }
        return result;
    }

    private void doRunJar() {
        String oldName = Thread.currentThread().getName();
        Thread.currentThread().setName(name());
        try {
            ClassloaderWithJar classloaderWithJar = new ClassloaderWithJar(jarOrDockerFile);
            buildSpringConfigLocation();
            getMainEntryPoint(classloaderWithJar).invoke(
                    null,
                    (Object) new String[] {
                            "--spring.profiles.include=" + Joiner.on(",").join(activeProfilesForTest()),
                            "--spring.config.location=" + buildSpringConfigLocation(),
                            "--primary.profile=" + getPrimaryConfigFile()
                    }
            );
        } catch (IllegalAccessException | InvocationTargetException ex) {
            log.error("Failed to invoke main() method for {} of {}", name(), jarOrDockerFile, ex);
        } catch (RuntimeException ex) {
            log.error("{} from {} jar has terminated exceptionally", name(), jarOrDockerFile, ex);
        } finally {
            Thread.currentThread().setName(oldName);
        }
    }

    private Set<String> activeProfilesForTest() {
        return Sets.union(ImmutableSet.of(getDbProfile()), defaultProfiles());
    }

    @SneakyThrows
    private String buildSpringConfigLocation() {
        return Joiner.on(",").join(
                "classpath:/",
                // Due to different classloader used by Spring we can't reference these in other way:
                getAndValidatePathFromResource("sandbox/application-" + dbProfileAndStartDbIfNeeded() + ".yml"),
                getAndValidatePathFromResource("sandbox/application-test-common.yml"),
                getPrimaryConfigFile()
        );
    }

    private String getPrimaryConfigFile() {
        return getAndValidatePathFromResource("sandbox/application-" + testProfileName() + ".yml");
    }

    @SneakyThrows
    private String getAndValidatePathFromResource(String resource) {
        URI path = Resources.getResource(resource).toURI();
        if (!Paths.get(path).toFile().exists()) {
            throw new IllegalStateException("Profile path " + resource + " does not exist");
        }

        return path.toASCIIString();
    }

    private Set<String> defaultProfiles() {
        Set<String> basicProfiles = ImmutableSet.of("test-common", testProfileName());
        Set<String> extraProfiles = extraProfiles();

        return Sets.union(
                basicProfiles,
                extraProfiles
        );
    }

    private String testProfileName() {
        return "test-" + name().toLowerCase().replaceAll("_", "-");
    }

    // Profiles from spring.profile.active are not always applied, forcing it.
    @SneakyThrows
    public Set<String> extraProfiles() {
        JsonNode tree = YML.readTree(Resources.getResource("sandbox/application-" + testProfileName() + ".yml"));
        String pointer = "/spring/profiles/active";
        JsonNode activeProfiles = tree.at(pointer);

        return Arrays.stream(activeProfiles.asText("").split(","))
                .filter(Strings::isNotBlank)
                .collect(Collectors.toSet());
    }

    private static String dbProfileAndStartDbIfNeeded() {
        String profile = getDbProfile();

        if (TEST_CONTAINERS_POSTGRES.equals(getDbType())) {
            startContainerizedPostgresAndPopulateIt();
        }

        return profile;
    }

    private static String getDbProfile() {
        String prefix = "test-db-";
        String value = getDbType();
        return prefix + value.toLowerCase().replaceAll("_", "-");
    }

    private static String getDbType() {
        String value = System.getProperty(DB_TYPE, System.getenv(DB_TYPE));

        if (null != value) {
            return value;
        }

        return TEST_CONTAINERS_POSTGRES;
    }

    @SneakyThrows
    private static void startContainerizedPostgresAndPopulateIt() {
        if (!DB_STARTED.compareAndSet(false, true)) {
            return;
        }

        try (Connection conn =
                     DriverManager.getConnection(
                             "jdbc:tc:postgresql:12:////sandbox_apps" +
                                     "?TC_TMPFS=/testtmpfs:rw" +
                                     "&TC_DAEMON=true" +
                                     "&TC_INITSCRIPT=sandbox/prepare-postgres.sql")) {
            Matcher port = PORT_PATTERN.matcher(conn.getMetaData().getURL());
            port.matches();
            System.setProperty("testcontainers.postgres.port", port.group(1));
        }
    }

    @Data
    private static class ClassloaderWithJar {

        private final String jarPath;
        private final URLClassLoader loader;

        @SneakyThrows
        ClassloaderWithJar(String jar) {
            jarPath = Arrays.stream(System.getProperty("java.class.path").split(System.getProperty("path.separator")))
                    .filter(it -> it.endsWith(jar))
                    .findAny()
                    .orElseThrow(() -> new IllegalStateException(
                            "Jar " + jar + " not found on classpath: " + System.getProperty("java.class.path"))
                    );

            loader = new URLClassLoader(
                    // It makes no sense to provide anything else except Spring JAR as it will use its own classloader
                    new URL[] {Paths.get(jarPath).toUri().toURL()},
                    null
            );
        }
    }

    @Data
    static class SandboxRunnable implements Runnable {

        private final SandboxApp app;
        private final Runnable toRun;

        @Override
        public void run() {
            toRun.run();
        }
    }
}
