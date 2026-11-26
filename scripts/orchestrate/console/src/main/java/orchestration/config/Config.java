package orchestration.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.List;

public final class Config {

    public record AgentConfig(String name, String command, String model, String parameters) {
    };

    public record AppConfig(String appName, List<AgentConfig> agaents) {
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
        return new AppConfig("foo",
                List.of(new AgentConfig("zzz", "zZzcmd", "zzzModel", "-zzz -ZZZZ -{model} -{prompt}")));

    }

}
