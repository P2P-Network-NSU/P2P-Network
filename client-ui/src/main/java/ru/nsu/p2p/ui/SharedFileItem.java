package ru.nsu.p2p.ui;

import ru.nsu.p2p.common.transfer.ShareState;
import java.io.File;

public record SharedFileItem(File file, ShareState state) {
    public String getName() {
        return file.getName();
    }

    public long getSize() {
        return file.length();
    }
}