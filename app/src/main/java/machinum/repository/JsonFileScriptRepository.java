package machinum.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.model.Script;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public class JsonFileScriptRepository implements ScriptRepository {

    private final ObjectMapper objectMapper;
    private final ScriptCodeEncoder encoder;
    private final Map<String, Script> scripts;
    private final String filePath;

    @SneakyThrows
    public static JsonFileScriptRepository create(ScriptCodeEncoder encoder, String filePath) {
        var absolutePath = new File(filePath).getCanonicalFile().getAbsolutePath();
        var mapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(SerializationFeature.INDENT_OUTPUT);
        return new JsonFileScriptRepository(mapper, encoder, new ConcurrentHashMap<>(), absolutePath);
    }

    public JsonFileScriptRepository init() {
        loadScripts();
        return this;
    }

    @Override
    public List<Script> findAll() {
        return scripts.values().stream()
                .map(this::decodeScript)
                .toList();
    }

    @Override
    public Optional<Script> findById(String id) {
        return Optional.ofNullable(scripts.get(id))
                .map(this::decodeScript);
    }

    @Override
    public Optional<Script> findByName(@NonNull String name) {
        return scripts.values().stream()
                .filter(script -> name.equalsIgnoreCase(script.getName()))
                .map(this::decodeScript)
                .findFirst();
    }

    @Override
    public Script save(Script script) {
        if (script.getId() == null) {
            script.setId(UUID.randomUUID().toString());
        }

        var scriptToStore = encodeScript(script);
        scripts.put(script.getId(), scriptToStore);
        saveScripts();
        return script; // Return original unencoded script
    }

    @Override
    public Script update(Script script) {
        return save(script);
    }

    @Override
    public boolean deleteById(String id) {
        boolean removed = scripts.remove(id) != null;
        if (removed) {
            saveScripts();
        }
        return removed;
    }

    /* ============= */

    private Script encodeScript(Script script) {
        return script.toBuilder()
                .text(encoder.processForStorage(script.getText()))
                .build();
    }

    private Script decodeScript(Script script) {
        return script.toBuilder()
                .text(encoder.processForRetrieval(script.getText()))
                .build();
    }

    private void loadScripts() {
        var file = new File(filePath);
        if (!file.exists()) {
            log.info("Scripts file not found, starting with empty collection");
            file.getParentFile().mkdirs();
            return;
        }

        try {
            var scriptList = objectMapper.readValue(file, new TypeReference<List<Script>>() {
            });
            scripts.clear();
            scriptList.forEach(script -> scripts.put(script.getId(), script));
            log.info("Loaded {} scripts from file", scripts.size());
        } catch (IOException e) {
            log.error("Failed to load scripts from file: %s".formatted(e.getMessage()), e);
        }
    }

    private void saveScripts() {
        try {
            var file = new File(filePath);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(file, new ArrayList<>(scripts.values()));
        } catch (IOException e) {
            log.error("Failed to save scripts to file: %s".formatted(e.getMessage()), e);
        }
    }

}
