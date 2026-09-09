CREATE TABLE fishing_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    experience_level VARCHAR(32),
    preferred_species JSONB NOT NULL DEFAULT '[]'::jsonb,
    preferred_fishing_styles JSONB NOT NULL DEFAULT '[]'::jsonb,
    home_address VARCHAR(512),
    home_city VARCHAR(128),
    home_region VARCHAR(128),
    home_country VARCHAR(128),
    home_location geometry(Point, 4326),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fishing_profiles_user_id_key UNIQUE (user_id)
);

CREATE INDEX idx_fishing_profiles_home_location
    ON fishing_profiles USING GIST (home_location);

CREATE TABLE gear (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    type VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    brand VARCHAR(255),
    metadata JSONB,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_gear_user_id ON gear (user_id);

CREATE TABLE boats (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    propulsion_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    max_speed_kmh NUMERIC(6, 2),
    notes TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_boats_user_id ON boats (user_id);
