package machinum.controller;

import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.controller.SessionController.StartRequest;
import machinum.exception.AppException;
import machinum.model.ScenarioResult;
import machinum.model.Script;
import machinum.repository.ScriptRepository;
import machinum.service.ContainerManager;
import org.codehaus.groovy.control.*;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Path("/api")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptRepository scriptRepository;
    private final ContainerManager containerManager;

    public static ScriptController_ scriptController(Jooby application) {
        return new ScriptController_(application.require(ScriptRepository.class), application.require(ContainerManager.class));
    }

    public static ValidationResult validateGroovyCode(String code) {
        var errors = new ArrayList<ValidationError>();
        var config = new CompilerConfiguration();
        var errorCollector = new ErrorCollector(config);

        var sourceUnit = new SourceUnit("Script" + System.currentTimeMillis(), code, config, null, errorCollector);
        var compilationUnit = new CompilationUnit(config);
        compilationUnit.addSource(sourceUnit);

        try {
            compilationUnit.compile(Phases.CANONICALIZATION);
        } catch (CompilationFailedException ignored) {
            // Continue to error inspection
        }

        if (errorCollector.hasErrors()) {
            for (var message : errorCollector.getErrors()) {
                if (message instanceof SyntaxErrorMessage syntax) {
                    SyntaxException se = syntax.getCause();
                    errors.add(new ValidationError(se.getLine(), se.getMessage()));
                }
            }
        }

        return new ValidationResult(errors);
    }

    @GET("/scripts")
    public List<Script> getAllScripts() {
        return scriptRepository.findAll();
    }

    @GET("/scripts/{id}")
    public Script getScriptById(@PathParam("id") String id, Context ctx) {
        Optional<Script> script = scriptRepository.findById(id);

        if (script.isEmpty()) {
            ctx.setResponseCode(StatusCode.NOT_FOUND);
            throw new AppException("Script not found");
        }

        return script.get();
    }

    @POST("/scripts")
    public Script saveScript(Context ctx, Script script) {
        if (validateDto(script)) {
            ctx.setResponseCode(StatusCode.BAD_REQUEST);
            throw new AppException("Name and code are required");
        }

        return scriptRepository.save(script);
    }

    @PUT("/scripts/{id}")
    public Script updateScript(@PathParam("id") String id, Context ctx, Script script) {
        if (!Objects.equals(id, script.getId())) {
            ctx.setResponseCode(StatusCode.FORBIDDEN);
            throw new AppException("Id doesn't match");
        }

        if (validateDto(script)) {
            ctx.setResponseCode(StatusCode.BAD_REQUEST);
            throw new AppException("Name and code are required");
        }

        return scriptRepository.update(script);
    }

    @DELETE("/scripts/{id}")
    public void deleteScript(@PathParam("id") String id, Context ctx) {
        boolean deleted = scriptRepository.deleteById(id);

        if (!deleted) {
            ctx.setResponseCode(StatusCode.NOT_FOUND);
            throw new AppException("Script not found");
        }

        ctx.setResponseCode(StatusCode.NO_CONTENT);
    }

    //TODO make it async?
    @POST("/scripts/validate")
    public ValidationResult validateScript(ValidationRequest request) {
        return validateGroovyCode(request.code());
    }

    @POST("/scripts/{id}/execute")
    public ScenarioResult executeScript(@PathParam("id") String id, StartRequest request) {
        var script = scriptRepository.getByIdOrName(id);

        return containerManager.execute(instance ->
                instance.executeScript(script.getText(), request.params(), script.getTimeout()));
    }

    private boolean validateDto(Script script) {
        return script.getName() == null ||
                script.getName().trim().isEmpty() ||
                script.getText() == null ||
                script.getText().trim().isEmpty();
    }

    public record ValidationRequest(String code) {
    }

    public record ValidationResult(List<ValidationError> errors) {
    }

    public record ValidationError(int line, String message) {
    }

}
