CREATE TABLE videos (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    zip_key VARCHAR(512),
    status VARCHAR(20) NOT NULL,
    frame_count INTEGER,
    error_message VARCHAR(1000),
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT chk_videos_status CHECK (
        status IN ('RECEIVED', 'QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')
    ),
    CONSTRAINT chk_videos_frame_count CHECK (frame_count IS NULL OR frame_count >= 0),
    CONSTRAINT chk_videos_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_videos_user_status_created ON videos (user_id, status, created_at DESC);

CREATE INDEX idx_videos_user_created ON videos (user_id, created_at DESC);

COMMENT ON TABLE videos IS 'Videos enviados pelos usuarios e o estado do seu processamento';
COMMENT ON COLUMN videos.user_id IS 'Claim sub do JWT; toda consulta e escopada por esta coluna';
COMMENT ON COLUMN videos.storage_key IS 'Chave do video original: fiapx/inputs/{userId}/{videoId}/{originalFilename}';
COMMENT ON COLUMN videos.zip_key IS 'Chave do ZIP de frames: fiapx/outputs/{userId}/{videoId}.zip';
COMMENT ON COLUMN videos.status IS 'RECEIVED, QUEUED, PROCESSING, COMPLETED ou FAILED; os dois ultimos sao finais';
COMMENT ON COLUMN videos.attempts IS 'Tentativas de processamento consumidas pelo worker antes da DLQ';
COMMENT ON INDEX idx_videos_user_status_created IS 'Atende GET /videos com filtro de status';
COMMENT ON INDEX idx_videos_user_created IS 'Atende GET /videos sem filtro de status';
