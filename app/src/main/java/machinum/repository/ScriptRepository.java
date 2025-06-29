package machinum.repository;

import machinum.exception.AppException;
import machinum.model.Script;

import java.util.List;
import java.util.Optional;

public interface ScriptRepository {

    List<Script> findAll();

    default Script getById(String id) {
        return findById(id)
                .orElseThrow(() -> new AppException("Script for given id is not found: %s".formatted(id)));
    }

    Optional<Script> findById(String id);

    Script save(Script script);

    Script update(Script script);

    boolean deleteById(String id);

}
