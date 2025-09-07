package machinum.controller;

import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.annotation.DELETE;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.PathParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppException;
import machinum.model.ScenarioResult;
import machinum.service.ResultStorage;

import java.util.List;

@Slf4j
@Path("/api")
@RequiredArgsConstructor
public class ResultController {

    private final ResultStorage resultStorage;

    public static ResultController_ resultController(Jooby application) {
        return new ResultController_(application.require(ResultStorage.class));
    }

    @GET("/results")
    public List<ScenarioResult> getAllResults() {
        return resultStorage.getAll();
    }

    @GET("/results/{id}")
    public ScenarioResult getResultById(@PathParam("id") String id, Context ctx) {
        ScenarioResult result = resultStorage.getById(id);
        if (result == null) {
            ctx.setResponseCode(StatusCode.NOT_FOUND);
            throw new AppException("Result not found");
        }
        return result;
    }

    @DELETE("/results/{id}")
    public void deleteResult(@PathParam("id") String id, Context ctx) {
        resultStorage.deleteById(id);
        ctx.setResponseCode(StatusCode.NO_CONTENT);
    }

}
