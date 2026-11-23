package orchestration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class OrchestrationApplication {

    public static void main(String[] args) throws IOException {
        var props = new Properties();

        try (var in = Files.newInputStream(Path.of("application.properties"))) {
            props.load(in);
        }

        // var appName = props.getProperty("app.name", "noname application");
        System.out.println(props);
    }
}
