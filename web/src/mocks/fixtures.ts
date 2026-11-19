import type { Lexeme } from '../api/types';

export const MOCK_LEXEMES_RUN: Lexeme[] = [
  {
    lemma: 'run',
    source_lang: 'en',
    pos: 'verb',
    transcription: 'rʌn',
    senses: [
      {
        id: 'sense_run_v_1',
        target_lang: 'ru',
        translation: 'бежать',
        synonyms: ['мчаться', 'нестись'],
        meanings: ['move at a speed faster than a walk'],
        examples: [
          {
            text: 'She runs five miles every morning along the coast.',
            translation: 'Она бегает пять миль каждое утро вдоль побережья.',
          },
        ],
      },
      {
        id: 'sense_run_v_2',
        target_lang: 'ru',
        translation: 'управлять',
        synonyms: ['руководить', 'вести'],
        meanings: ['be in charge of; manage or organize'],
        examples: [
          {
            text: 'He runs a small independent bakery in the town center.',
            translation: 'Он управляет небольшой независимой пекарней в центре города.',
          },
        ],
      },
      {
        id: 'sense_run_v_3',
        target_lang: 'ru',
        translation: 'работать',
        synonyms: ['функционировать'],
        meanings: ['function or operate continuously'],
        examples: [
          {
            text: 'The engine is running quietly and smoothly.',
            translation: 'Двигатель работает тихо и плавно.',
          },
        ],
      },
    ],
  },
  {
    lemma: 'run',
    source_lang: 'en',
    pos: 'noun',
    transcription: 'rʌn',
    senses: [
      {
        id: 'sense_run_n_1',
        target_lang: 'ru',
        translation: 'пробежка',
        synonyms: ['бег'],
        meanings: ['an act or spell of running for exercise'],
        examples: [
          {
            text: 'I went for a 5-kilometer run in the park before breakfast.',
            translation: 'Я вышел на 5-километровую пробежку в парке перед завтраком.',
          },
        ],
      },
      {
        id: 'sense_run_n_2',
        target_lang: 'ru',
        translation: 'полоса',
        synonyms: ['серия'],
        meanings: ['a continuous sequence or streak'],
        examples: [
          {
            text: 'The football team enjoyed a magnificent run of six consecutive victories.',
            translation: 'Футбольная команда наслаждалась великолепной серией из шести побед подряд.',
          },
        ],
      },
    ],
  },
];

export const MOCK_LEXEMES_BANK: Lexeme[] = [
  {
    lemma: 'bank',
    source_lang: 'en',
    pos: 'noun',
    transcription: 'bæŋk',
    senses: [
      {
        id: 'sense_bank_n_1',
        target_lang: 'ru',
        translation: 'банк',
        synonyms: ['финансовое учреждение'],
        meanings: ['a financial institution licensed to receive deposits and make loans'],
        examples: [
          {
            text: 'I went to the bank to open a savings account.',
            translation: 'Я пошёл в банк, чтобы открыть сберегательный счёт.',
          },
        ],
      },
      {
        id: 'sense_bank_n_2',
        target_lang: 'ru',
        translation: 'берег',
        synonyms: ['набережная', 'склон'],
        meanings: ['the land along the edge of a river or lake'],
        examples: [
          {
            text: 'They set up camp on the grassy bank of the river.',
            translation: 'Они разбили лагерь на травянистом берегу реки.',
          },
        ],
      },
    ],
  },
  {
    lemma: 'bank',
    source_lang: 'en',
    pos: 'verb',
    transcription: 'bæŋk',
    senses: [
      {
        id: 'sense_bank_v_1',
        target_lang: 'ru',
        translation: 'рассчитывать',
        synonyms: ['полагаться'],
        meanings: ['rely on or count on with confidence'],
        examples: [
          {
            text: "You can bank on his support when you need it most.",
            translation: 'Ты можешь рассчитывать на его поддержку, когда она нужнее всего.',
          },
        ],
      },
    ],
  },
];

export const MOCK_LEXEMES_LIGHT: Lexeme[] = [
  {
    lemma: 'light',
    source_lang: 'en',
    pos: 'noun',
    transcription: 'laɪt',
    senses: [
      {
        id: 'sense_light_n_1',
        target_lang: 'ru',
        translation: 'свет',
        synonyms: ['освещение', 'сияние'],
        meanings: ['the natural agent that stimulates sight and makes things visible'],
        examples: [
          {
            text: 'Morning light began to filter through the trees.',
            translation: 'Утренний свет начал пробиваться сквозь деревья.',
          },
        ],
      },
    ],
  },
  {
    lemma: 'light',
    source_lang: 'en',
    pos: 'adjective',
    transcription: 'laɪt',
    senses: [
      {
        id: 'sense_light_adj_1',
        target_lang: 'ru',
        translation: 'лёгкий',
        synonyms: ['невесомый', 'нетяжёлый'],
        meanings: ['of little weight; not heavy'],
        examples: [
          {
            text: 'This carbon-fiber bicycle is remarkably light.',
            translation: 'Этот велосипед из углеродного волокна удивительно лёгкий.',
          },
        ],
      },
      {
        id: 'sense_light_adj_2',
        target_lang: 'ru',
        translation: 'светлый',
        synonyms: ['ясный', 'яркий'],
        meanings: ['having a considerable amount of natural or artificial illumination'],
        examples: [
          {
            text: 'The studio apartment was bright, light, and airy.',
            translation: 'Квартира-студия была светлой, яркой и просторной.',
          },
        ],
      },
    ],
  },
];

export const MOCK_DICTIONARY_FIXTURES: Record<string, Lexeme[]> = {
  run: MOCK_LEXEMES_RUN,
  bank: MOCK_LEXEMES_BANK,
  light: MOCK_LEXEMES_LIGHT,
};
