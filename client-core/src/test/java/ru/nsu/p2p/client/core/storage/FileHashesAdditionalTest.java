package ru.nsu.p2p.client.core.storage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static ru.nsu.p2p.client.core.storage.StorageTestSupport.sha;

class FileHashesAdditionalTest {
    @Test
    void copyingInputListProtectsPublishedHashesFromCallerMutation() {
        String hash = sha(new byte[]{1});
        List<String> source = new ArrayList<>(List.of(hash));
        FileHashes result = new FileHashes(hash, source);
        source.clear();
        source.add(sha(new byte[]{2}));
        assertEquals(List.of(hash), result.chunkHashes());
    }

    @Test
    void accessorDoesNotAllowReplacingHashes() {
        String hash = sha(new byte[]{1});
        FileHashes result = new FileHashes(hash, List.of(hash));
        assertThrows(UnsupportedOperationException.class,
                () -> result.chunkHashes().set(0, sha(new byte[]{2})));
    }

    @Test
    void nullIdAndNullListAreRejected() {
        assertThrows(NullPointerException.class, () -> new FileHashes(null, List.of()));
        assertThrows(NullPointerException.class, () -> new FileHashes(sha(new byte[0]), null));
    }

    @Test
    void nullElementIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new FileHashes(sha(new byte[0]), Arrays.asList((String) null)));
    }
}
