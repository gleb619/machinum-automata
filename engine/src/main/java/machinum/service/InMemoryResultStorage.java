package machinum.service;

import machinum.model.ScenarioResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryResultStorage implements ResultStorage {

    private final Map<String, ScenarioResult> results = new ConcurrentHashMap<>();
    private final List<ScenarioResult> resultList = new CopyOnWriteArrayList<>();

    @Override
    public void save(ScenarioResult result) {
        // Assuming ScenarioResult has an ID, or generate one
        String id = result.getVideoFile() != null ? result.getVideoFile() : String.valueOf(System.nanoTime());
        results.put(id, result);
        resultList.add(result);
    }

    @Override
    public List<ScenarioResult> getAll() {
        return List.copyOf(resultList);
    }

    @Override
    public ScenarioResult getById(String id) {
        return results.get(id);
    }

    @Override
    public void deleteById(String id) {
        ScenarioResult result = results.remove(id);
        if (result != null) {
            resultList.remove(result);
        }
    }
}
