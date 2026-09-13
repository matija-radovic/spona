package io.github.matijaradovic.spona.storage;

import static io.github.matijaradovic.spona.support.AvatarFixtures.canvasPng;
import static io.github.matijaradovic.spona.support.AvatarFixtures.image;
import static io.github.matijaradovic.spona.support.AvatarFixtures.pngDataUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.matijaradovic.spona.common.error.UnprocessableException;

class AvatarServiceTest {

    private static final String OLD = "http://localhost:9000/spona-avatars/old.png";
    private static final String NEW = "http://localhost:9000/spona-avatars/new.png";

    private final StorageService storage = mock(StorageService.class);
    private final AvatarService avatars = new AvatarService(storage);

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void storesTheValidatedBytesAsAPng() {
        when(storage.putAvatar(any(), any())).thenReturn(NEW);

        assertThat(avatars.store(pngDataUrl(canvasPng()))).isEqualTo(NEW);

        verify(storage).putAvatar(canvasPng(), "image/png");
    }

    @Test
    void storesNothingForAnImageItRefuses() {
        assertThatThrownBy(() -> avatars.store(pngDataUrl(image("png", 300, 200))))
                .isInstanceOf(UnprocessableException.class);

        verifyNoInteractions(storage);
    }

    @Test
    void deletesTheStoredAvatarWhenTheTransactionRollsBack() {
        when(storage.putAvatar(any(), any())).thenReturn(NEW);
        TransactionSynchronizationManager.initSynchronization();

        avatars.store(pngDataUrl(canvasPng()));
        rollback();

        verify(storage).deleteAvatar(NEW);
    }

    @Test
    void keepsTheStoredAvatarWhenTheTransactionCommits() {
        when(storage.putAvatar(any(), any())).thenReturn(NEW);
        TransactionSynchronizationManager.initSynchronization();

        avatars.store(pngDataUrl(canvasPng()));
        commit();

        verify(storage, never()).deleteAvatar(any());
    }

    @Test
    void replacingDeletesTheOldAvatarOnlyOnceTheTransactionCommits() {
        when(storage.putAvatar(any(), any())).thenReturn(NEW);
        TransactionSynchronizationManager.initSynchronization();

        assertThat(avatars.replace(OLD, pngDataUrl(canvasPng()))).isEqualTo(NEW);
        verify(storage, never()).deleteAvatar(any());

        commit();
        verify(storage).deleteAvatar(OLD);
        verify(storage, never()).deleteAvatar(NEW);
    }

    @Test
    void aReplacementThatRollsBackKeepsTheOldAvatarAndDeletesTheNewOne() {
        when(storage.putAvatar(any(), any())).thenReturn(NEW);
        TransactionSynchronizationManager.initSynchronization();

        avatars.replace(OLD, pngDataUrl(canvasPng()));
        rollback();

        verify(storage).deleteAvatar(NEW);
        verify(storage, never()).deleteAvatar(OLD);
    }

    @Test
    void replacingWhereThereWasNoAvatarDeletesNothing() {
        when(storage.putAvatar(any(), any())).thenReturn(NEW);
        TransactionSynchronizationManager.initSynchronization();

        avatars.replace(null, pngDataUrl(canvasPng()));
        commit();

        verify(storage, never()).deleteAvatar(any());
    }

    @Test
    void discardingOutsideATransactionDeletesAtOnce() {
        avatars.discard(OLD);

        verify(storage).deleteAvatar(OLD);
    }

    @Test
    void aDeleteThatFailsIsLoggedRatherThanThrown() {
        doThrow(new IllegalStateException("store unreachable")).when(storage).deleteAvatar(OLD);

        assertThatCode(() -> avatars.discard(OLD)).doesNotThrowAnyException();
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> {
            sync.afterCommit();
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        });
    }

    private static void rollback() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }
}
