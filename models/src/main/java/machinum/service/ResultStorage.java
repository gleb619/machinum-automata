package machinum.service;

import machinum.model.ScenarioResult;

import java.util.List;

public interface ResultStorage {

    void save(ScenarioResult result);

    List<ScenarioResult> getAll();

    ScenarioResult getById(String id);

    void deleteById(String id);

}
