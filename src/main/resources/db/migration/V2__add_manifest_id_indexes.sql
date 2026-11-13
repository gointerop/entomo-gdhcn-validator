-- Lookups by manifest_id (QrCodeRepository.findByManifestId, IpsFileRepository.findByManifestId) had no index.
-- CONCURRENTLY avoids blocking writes on a large table; it cannot run inside a transaction, so this
-- migration is configured with executeInTransaction=false (see V2__add_manifest_id_indexes.sql.conf).
-- IF NOT EXISTS makes the script safe to re-run after a partial failure.

create index concurrently if not exists idx_qr_code_manifest_id on qr_code (manifest_id);
create index concurrently if not exists idx_ips_file_manifest_id on ips_file (manifest_id);
