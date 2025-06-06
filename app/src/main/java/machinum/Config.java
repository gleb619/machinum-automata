package machinum;

import ch.qos.logback.classic.Level;
import io.jooby.Extension;
import io.jooby.Jooby;
import lombok.extern.slf4j.Slf4j;
import machinum.service.ContainerManagerService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Slf4j
public class Config implements Extension {

    @Override
    public void install(@NotNull Jooby application) throws Exception {
        changeLogLevel(App.class.getPackageName(), Level.DEBUG);

        var registry = application.getServices();
        var config = application.getEnvironment().getConfig();

        registry.get(TemplateEngine.class).getTemplateResolvers().forEach(resolver -> {
            if (resolver instanceof ClassLoaderTemplateResolver cpResolver) {
                cpResolver.setSuffix(".html");
            }
        });

        registry.putIfAbsent(ContainerManagerService.class, new ContainerManagerService());

        application.onStarted(() -> {
            application.require(ContainerManagerService.class).init();
        });
        application.onStop(() -> {
            application.require(ContainerManagerService.class).shutdown();
        });
    }

    public static void changeLogLevel(String name, Level level) {
        var factory = LoggerFactory.getILoggerFactory();
        var appLogger = factory.getLogger(name);
        if (appLogger instanceof ch.qos.logback.classic.Logger log) {
            log.setLevel(level);
        }
    }

}
