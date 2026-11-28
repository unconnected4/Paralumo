package orchestration;

import orchestration.config.Config;

public class OrchestrationApplication {

    public static void main(String[] args) {
        System.out.println(Config.get());
    }
}
