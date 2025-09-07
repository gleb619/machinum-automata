package machinum;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.jooby.Jooby;
import io.jooby.MapModelAndView;
import io.jooby.OpenAPIModule;
import io.jooby.handler.AssetHandler;
import io.jooby.handler.AssetSource;
import io.jooby.jackson.JacksonModule;
import io.jooby.jetty.JettyServer;
import io.jooby.thymeleaf.ThymeleafModule;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;
import org.slf4j.Logger;

import java.nio.file.Paths;
import java.util.Map;

import static machinum.Config.*;
import static machinum.controller.RemoteController.remoteController;
import static machinum.controller.ResultController.resultController;
import static machinum.controller.ScriptController.scriptController;
import static machinum.controller.SessionController.sessionController;
import static machinum.util.Util.hasCause;

@Slf4j
@OpenAPIDefinition(info =
@Info(title = "Machinum Automata",
        version = "1",
        description = """
                An application for automated control of browser behavior
                """),
        tags = {
                @Tag(name = "machinum")
        }
)
public class App extends Jooby {

  {
    install(new JettyServer());
    install(new ThymeleafModule());
    install(new JacksonModule(JacksonModule.create()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)));
    install(new OpenAPIModule());

    //        use(new AccessLogHandler());

    error((ctx, cause, statusCode) -> {
      // Log the error
      log.error("ERROR: path=%s, status=%s, message=%s".formatted(ctx.getRequestPath(), ctx.getResponseCode(), cause.getMessage()), cause);
      ctx.setResponseCode(statusCode);
      ctx.setResponseHeader("Content-Type", "application/json");
      var message = hasCause(cause, AppException.class) ? cause.getMessage() : "An unexpected error occurred, please check logs";
      ctx.render(Map.of(
              "success", Boolean.FALSE,
              "message", message
      ));
    });

    before(ctx -> log.info("> {} {}", ctx.getMethod(), ctx.getRequestPath()));

    after((ctx, result, failure) -> {
      int value = ctx.getResponseCode().value() == 0 ? 200 : ctx.getResponseCode().value();
      if (failure == null) {
        log.info("< {} {} {}", ctx.getMethod(), ctx.getRequestPath(), value);
      } else {
        log.error("[X] {} {} {}", ctx.getMethod(), ctx.getRequestPath(), value, failure);
      }
    });

    install(new Config());

    mvc(sessionController(this));
    mvc(scriptController(this));
    mvc(remoteController(this));
    mvc(resultController(this));

    get("/", ctx -> new MapModelAndView("index.html", Map.of()));

    get("/health", ctx -> Map.of("success", true));

    assets("/api/html/*", Paths.get(getConfig().getString(HTML_REPORTS_PARAM)));

    AssetSource web = AssetSource.create(Paths.get(getConfig().getString(STATIC_RESOURCES_PARAM)));
    assets("/?*", new AssetHandler("index.html", web));
  }

  public static void main(final String[] args) {
    try {
      changeLogLevel(Logger.ROOT_LOGGER_NAME, Level.INFO);
      Jooby.runApp(args, App::new);
    } catch (Exception e) {
      log.error("ERROR: ", e);
      // Thanks to graceful optimisation, the application will not stop if the
      // application port is busy
      System.exit(1);
    }
  }

}
