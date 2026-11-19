-- Language-neutral columns and language annotations (task feedback-amendments, proposal
-- docs/api/proposals/feedback-amendments.md). Sense ids now include the language pair, so every
-- stored id is stale: the dictionary data and cards are reset first. Nothing is deployed yet, which
-- is why this one migration may rename in place and delete rows (backend/intent.md).

DELETE FROM user_cards;
DELETE FROM senses;
DELETE FROM lexemes;

ALTER TABLE lexemes RENAME COLUMN lang TO source_lang;
ALTER TABLE lexemes ALTER COLUMN source_lang DROP DEFAULT;
ALTER TABLE lexemes DROP CONSTRAINT lexemes_lemma_pos_key;
ALTER TABLE lexemes ADD CONSTRAINT lexemes_source_lang_lemma_pos_key UNIQUE (source_lang, lemma, pos);

ALTER TABLE senses RENAME COLUMN translation_ru TO translation;
ALTER TABLE senses RENAME COLUMN synonyms_ru TO synonyms;
ALTER TABLE senses RENAME COLUMN meanings_en TO meanings;
ALTER TABLE senses ADD COLUMN target_lang TEXT NOT NULL DEFAULT 'ru';
ALTER TABLE senses ALTER COLUMN target_lang DROP DEFAULT;

ALTER TABLE user_cards RENAME COLUMN translation_ru TO translation;
ALTER TABLE user_cards RENAME COLUMN synonyms_ru TO synonyms;
ALTER TABLE user_cards RENAME COLUMN meanings_en TO meanings;
ALTER TABLE user_cards ADD COLUMN source_lang TEXT NOT NULL DEFAULT 'en';
ALTER TABLE user_cards ADD COLUMN target_lang TEXT NOT NULL DEFAULT 'ru';
ALTER TABLE user_cards ALTER COLUMN source_lang DROP DEFAULT;
ALTER TABLE user_cards ALTER COLUMN target_lang DROP DEFAULT;
