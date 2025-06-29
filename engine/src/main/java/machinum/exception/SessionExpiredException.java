package machinum.exception;

public class SessionExpiredException extends AppException {

    public SessionExpiredException(String sessionId) {
        super("Session expired: " + sessionId);
    }

    public SessionExpiredException(String message, Throwable cause) {
        super(message, cause);
    }

}
