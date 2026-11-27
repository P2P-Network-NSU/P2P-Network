package ru.nsu.p2p.client.file;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static ru.nsu.p2p.common.utils.HashUtils.computeSha256;

class FileHashesTest {
    @Test
    void copyingInputListProtectsPublishedHashesFromCallerMutation() {
        String hash = computeSha256(new byte[]{1});
        List<String> source = new ArrayList<>(List.of(hash));
        FileHashes result = new FileHashes(hash, 0L, source);
        source.clear();
        source.add(computeSha256(new byte[]{2}));
        assertEquals(List.of(hash), result.chunkHashes());
    }

    @Test
    void accessorDoesNotAllowReplacingHashes() {
        String hash = computeSha256(new byte[]{1});
        FileHashes result = new FileHashes(hash, 0L, List.of(hash));
        assertThrows(UnsupportedOperationException.class,
                () -> result.chunkHashes().set(0, computeSha256(new byte[]{2})));
    }

    @Test
    void nullIdAndNullListAreRejected() {
        assertThrows(NullPointerException.class, () -> new FileHashes(null, 0L, List.of()));
        assertThrows(NullPointerException.class, () -> new FileHashes(computeSha256(new byte[0]), 0L, null));
    }

    @Test
    void nullElementIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new FileHashes(computeSha256(new byte[0]), 0L, Arrays.asList((String) null)));
    }
}

