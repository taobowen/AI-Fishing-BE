DROP INDEX IF EXISTS boats_user_web_default_key;

CREATE UNIQUE INDEX boats_user_web_default_provenance_key
    ON boats (user_id, provenance)
    WHERE system_generated IS TRUE
      AND provenance IN ('WEB_DEFAULT', 'WEB_DEFAULT_PADDLE', 'WEB_DEFAULT_MOTOR', 'WEB_DEFAULT_BASS')
      AND active IS TRUE;
