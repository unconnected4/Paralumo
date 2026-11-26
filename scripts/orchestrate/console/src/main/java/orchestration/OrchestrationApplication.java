package orchestration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import orchestration.config.Config;

public class OrchestrationApplication {

    public static void main(String[] args) throws IOException {
        System.out.println(Config.get());
    }
}
