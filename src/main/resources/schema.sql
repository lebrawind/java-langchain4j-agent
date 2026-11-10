CREATE TABLE IF NOT EXISTS chat_memory (
    id VARCHAR(255) PRIMARY KEY,
    messages LONGTEXT
    );
CREATE TABLE IF NOT EXISTS `document_chunk` (
    `id`        BIGINT       NOT NULL AUTO_INCREMENT,
    `doc_name`  VARCHAR(255) NOT NULL,
    `content`   TEXT         NOT NULL,
    `embedding` LONGTEXT     NULL,
    PRIMARY KEY (`id`),
    KEY `idx_document_chunk_doc_name` (`doc_name`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;