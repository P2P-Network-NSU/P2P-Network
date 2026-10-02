package ru.nsu.p2p.common.api;

import java.util.concurrent.CompletionStage;
import ru.nsu.p2p.common.model.CatalogPage;
import ru.nsu.p2p.common.model.CatalogQuery;
import ru.nsu.p2p.common.model.FileManifest;
import ru.nsu.p2p.common.model.PeerList;

public interface CatalogApi {

    CompletionStage<CatalogPage> search(CatalogQuery query);

    CompletionStage<FileManifest> getManifest(String fileId);

    CompletionStage<PeerList> getPeers(String fileId);

}
