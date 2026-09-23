ALTER TABLE boats
    ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE boats b
SET is_default = TRUE
WHERE b.active = TRUE
  AND b.system_generated = FALSE
  AND b.id = (
      SELECT b2.id
      FROM boats b2
      WHERE b2.user_id = b.user_id
        AND b2.active = TRUE
        AND b2.system_generated = FALSE
      ORDER BY b2.created_at ASC, b2.id ASC
      LIMIT 1
  );

CREATE UNIQUE INDEX boats_user_default_key
    ON boats (user_id)
    WHERE is_default AND active AND NOT system_generated;
