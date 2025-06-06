package machinum.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Util {

    public static boolean hasCause(@NonNull Throwable rootCause, @NonNull Class<?> clazz) {
        var throwable = rootCause;
        int depth = 0;

        while (throwable != null && depth < 20) {
            if (clazz.isInstance(throwable)) {
                return true;
            }
            throwable = throwable.getCause();
            depth++;
        }

        return false;
    }

}
