package ru.nsu.p2p.ui.files;

import ru.nsu.p2p.common.transfer.ShareState;
import java.io.File;

public record SharedFileItem(File file, long size, ShareState state) {

    public String getName() {
        return file.getName();
    }

    public long getSize() {
        return size;
    }
}