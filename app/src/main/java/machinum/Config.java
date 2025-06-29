package machinum;

import ch.qos.logback.classic.Level;
import io.jooby.Extension;
import io.jooby.Jooby;
import lombok.extern.slf4j.Slf4j;
import machinum.repository.JsonFileScriptRepository;
import machinum.repository.ScriptCodeEncoder;
import machinum.repository.ScriptRepository;
import machinum.service.CacheMediator;
import machinum.service.ContainerManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.File;
import java.util.Map;

@Slf4j
public class Config implements Extension {

    public static final String RECORDING_DIRECTORY_PARAM = "app.recording-directory";

    public static final String HTML_REPORTS_PARAM = "app.html-reports";

    public static final String SCRIPTS_PATH_PARAM = "app.scripts-path";

    public static final String CACHE_DIRECTORY_PARAM = "app.cache-directory";

    public static final String SECRET_KEY_PARAM = "app.secret-key";


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

        var cacheDirectory = config.getString(CACHE_DIRECTORY_PARAM);
        var cacheMediator = new CacheMediator(Map.of(
                "persistenceDir", cacheDirectory
        ));
        registry.putIfAbsent(CacheMediator.class, cacheMediator);

        var recordingDirectory = config.getString(RECORDING_DIRECTORY_PARAM);
        var reportDirectory = config.getString(HTML_REPORTS_PARAM);
        registry.putIfAbsent(ContainerManager.class, new ContainerManager(cacheMediator, recordingDirectory, reportDirectory));

        var scriptsPath = config.getString(SCRIPTS_PATH_PARAM);
        var secretKey = config.getString(SECRET_KEY_PARAM);
        var encoder = ScriptCodeEncoder.create(secretKey);
        var scriptRepository = JsonFileScriptRepository.create(encoder, scriptsPath)
                .init();
        registry.putIfAbsent(ScriptRepository.class, scriptRepository);

        //Create html reports dir on app start
        var htmlFolder = new File(config.getString(HTML_REPORTS_PARAM));
        if (!htmlFolder.exists()) htmlFolder.mkdirs();

        application.onStarted(() -> {
            application.require(ContainerManager.class).init();
            application.require(CacheMediator.class).init();
        });
        application.onStop(() -> {
            application.require(ContainerManager.class).shutdown();
            application.require(CacheMediator.class).close();
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
