package orchestration.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class Config {

    public record RunnerConfig(String name, String command, String parameters) {
    };

    public record AgentConfig(String name, String runner, String model, String prompt) {
    };

    public record AppConfig(String appName, List<RunnerConfig> runners, List<AgentConfig> agents) {
    };

    private static final AppConfig CONFIG = load();

    private Config() {
    }

    public static AppConfig get() {
        return CONFIG;
    }
    // var props = new Properties();

    // try (var in = Files.newInputStream(Path.of("application.properties"))) {
    // props.load(in);
    // }

    // // var appName = props.getProperty("app.name", "noname application");
    // System.out.println(props);

    private static AppConfig load() {
        System.out.println("=========");

        var props = new Properties();

        try (var in = Files.newInputStream(Path.of("application.properties"))) {
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Configfile is not found", e);
        }

        System.out.println(getConfigEntry(props, "runner", "1", RunnerConfig.class));
        // collect engines
        // var runnerIds = getSectionIds(props, "runner");

        // var runners = new ArrayList<RunnerConfig>();
        // for (String i : runnerIds) {
        // var prefix = "runner." + i;
        // runners.add(new RunnerConfig(
        // props.getProperty(prefix + ".name"),
        // props.getProperty(prefix + ".command"),
        // props.getProperty(prefix + ".parameters")));
        // }

        // List<String> runnerIds = parseList(require(props, "runner"));

        // System.out.println(props);
        // System.out.println(props.getProperty("runner.1.name", "ffs"));

        System.out.println("====!!!===");

        return new AppConfig(props.getProperty("app.name", "noname application"), null, null);

    }

    private static List<String> getSectionIds(Properties props, String section) {
        return props.stringPropertyNames().stream()
                .filter(k -> k.startsWith(section))
                .map(k -> k.split("\\.")[1])
                .distinct()
                .sorted()
                .toList();
    }

    // implementing generics and reflection
    private static <T extends Record> T getConfigEntry(Properties props, String section, String index, Class<T> type) {
        var recordComponents = type.getRecordComponents();
        var types = new Class<?>[recordComponents.length];
        var values = new Object[recordComponents.length];
        for (var i = 0; i < recordComponents.length; i++) {
            types[i] = recordComponents[i].getType();
            values[i] = props.getProperty(section + "." + index + "." + recordComponents[i].getName());
        }

        try {
            return type.getConstructor(types).newInstance(values);
        } catch (Exception e) {
            /// do nothing
            return null;
        }
    }
}
