package pl.ultrahc.common.storage;

/** Rodzaj magazynu danych. SQLITE na start, MYSQL na produkcję (patrz DECYZJE). */
public enum StorageType {
    SQLITE,
    MYSQL
}
