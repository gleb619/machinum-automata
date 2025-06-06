package machinum.exception;

public class SessionExpiredException extends AppException {

    public SessionExpiredException(String sessionId) {
        super("Session expired: " + sessionId);
    }

}
