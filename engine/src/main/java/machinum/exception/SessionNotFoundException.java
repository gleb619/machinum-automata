package machinum.exception;

public class SessionNotFoundException extends AppException {

    public SessionNotFoundException(String sessionId) {
        super("Session not found: " + sessionId);
    }

}
