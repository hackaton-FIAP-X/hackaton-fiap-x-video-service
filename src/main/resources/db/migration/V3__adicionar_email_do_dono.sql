-- PLT-8: e-mail do dono do video, para avisar quando o processamento falha.
-- Vem do claim `email` do JWT no momento do upload. Nulo para videos anteriores
-- a esta migration (esses simplesmente nao recebem aviso).
ALTER TABLE videos ADD COLUMN owner_email VARCHAR(255);

COMMENT ON COLUMN videos.owner_email IS 'Claim email do JWT no upload; destino do aviso de falha (PLT-8)';
