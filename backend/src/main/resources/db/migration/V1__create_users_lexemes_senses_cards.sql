-- Step 1 schema: accounts, the dictionary data seen in lookups, and saved cards.

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    -- Stored lowercased by the service, so the unique constraint is case-insensitive in effect.
    email         TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT users_email_key UNIQUE (email)
);

CREATE TABLE lexemes (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lemma         TEXT NOT NULL,
    lang          TEXT NOT NULL DEFAULT 'en',
    pos           TEXT NOT NULL,
    transcription TEXT,
    CONSTRAINT lexemes_lemma_pos_key UNIQUE (lemma, pos)
);

-- One row per sense a provider has returned. source_key is the public, opaque sense_id.
CREATE TABLE senses (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lexeme_id      BIGINT NOT NULL REFERENCES lexemes (id),
    source         TEXT NOT NULL,
    source_key     TEXT NOT NULL,
    translation_ru TEXT NOT NULL,
    synonyms_ru    TEXT[] NOT NULL,
    meanings_en    TEXT[] NOT NULL,
    examples       JSONB NOT NULL,
    CONSTRAINT senses_source_key_key UNIQUE (source_key)
);

-- A saved sense. The sense data is copied so the card survives changes at the dictionary source.
-- Deleting a card sets deleted_at; saving the same sense again clears it.
CREATE TABLE user_cards (
    id             UUID PRIMARY KEY,
    user_id        UUID NOT NULL REFERENCES users (id),
    sense_id       BIGINT NOT NULL REFERENCES senses (id),
    sense_key      TEXT NOT NULL,
    lemma          TEXT NOT NULL,
    pos            TEXT NOT NULL,
    transcription  TEXT,
    translation_ru TEXT NOT NULL,
    synonyms_ru    TEXT[] NOT NULL,
    meanings_en    TEXT[] NOT NULL,
    examples       JSONB NOT NULL,
    notes          TEXT,
    custom_example TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT user_cards_user_sense_key UNIQUE (user_id, sense_id)
);
CREATE INDEX user_cards_user_active_idx ON user_cards (user_id, created_at DESC)
    WHERE deleted_at IS NULL;
