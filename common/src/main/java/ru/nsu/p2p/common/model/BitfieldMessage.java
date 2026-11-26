package ru.nsu.p2p.common.model;

/**
 * Битовая маска, описывающая наличие чанков у пира.
 * Каждый бит в массиве соответствует одному чанку (1 - чанк есть, 0 - чанка нет).
 *
 * @param availableChunks Массив байтов, представляющий битовую маску.
 */
public record BitfieldMessage(
    byte[] availableChunks
) {
}