package io.github.matijaradovic.spona.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.matijaradovic.spona.common.util.TransactionHooks;

@Service
public class AvatarService {

    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);

    private final StorageService storage;

    public AvatarService(StorageService storage) {
        this.storage = storage;
    }

    public String store(String dataUrl) {
        String url = storage.putAvatar(ImageValidator.avatar(dataUrl), ImageValidator.PNG);
        TransactionHooks.afterRollback(() -> delete(url));
        return url;
    }

    public String replace(String currentUrl, String dataUrl) {
        String url = store(dataUrl);
        discard(currentUrl);
        return url;
    }

    public void discard(String url) {
        if (url != null) {
            TransactionHooks.afterCommit(() -> delete(url));
        }
    }

    private void delete(String url) {
        try {
            storage.deleteAvatar(url);
        } catch (RuntimeException ex) {
            log.warn("Avatar {} was not deleted: {}", url, ex.getMessage());
        }
    }
}
