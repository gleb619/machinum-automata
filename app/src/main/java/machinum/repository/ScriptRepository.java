package machinum.repository;

import machinum.exception.AppException;
import machinum.model.Script;

import java.util.List;
import java.util.Optional;

public interface ScriptRepository {

    List<Script> findAll();

    default Script getByIdOrName(String id) {
        return findByIdOrName(id)
                .orElseThrow(() -> new AppException("Script for given id is not found: %s".formatted(id)));
    }

    default Optional<Script> findByIdOrName(String id) {
        return findById(id)
                .or(() -> findByName(id));
    }

    Optional<Script> findById(String id);

    Optional<Script> findByName(String name);

    Script save(Script script);

    Script update(Script script);

    boolean deleteById(String id);

}
