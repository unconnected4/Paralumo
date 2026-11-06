import { http, HttpResponse } from 'msw';
import type { Card, Lexeme, Sense, User } from '../api/types';
import { MOCK_DICTIONARY_FIXTURES } from './fixtures';

// In-memory state for mock sessions and card saves
const initialUser = (): User => ({
  id: 'usr_mock_1',
  email: 'learner@example.com',
});

const initialCards = (): Card[] => [
  {
    id: 'card_mock_1',
    sense_id: 'sense_run_v_1',
    lemma: 'run',
    pos: 'verb',
    transcription: 'rʌn',
    translation_ru: 'бежать',
    synonyms_ru: ['мчаться', 'нестись'],
    meanings_en: ['move at a speed faster than a walk'],
    examples: [
      {
        en: 'She runs five miles every morning along the coast.',
        ru: 'Она бегает пять миль каждое утро вдоль побережья.',
      },
    ],
    created_at: new Date(Date.now() - 86400000).toISOString(),
    notes: 'Common verb with irregular forms: run - ran - run',
  },
];

// Seeded account for mock mode: learner@example.com / password123
const SEED_PASSWORD = 'password123';

interface MockAccount {
  user: User;
  password: string;
}

const initialAccounts = (): Map<string, MockAccount> =>
  new Map([['learner@example.com', { user: initialUser(), password: SEED_PASSWORD }]]);

const userCardsStore = new Map<string, Card[]>();
const registeredUsers = initialAccounts();
let nextUserNumber = 2;

function getCardsForUser(userId: string): Card[] {
  if (!userCardsStore.has(userId)) {
    if (userId === 'usr_mock_1') {
      userCardsStore.set(userId, initialCards());
    } else {
      userCardsStore.set(userId, []);
    }
  }
  return userCardsStore.get(userId)!;
}

let currentUser: User | null = initialUser();

function findSenseAcrossFixtures(senseId: string): { sense: Sense; lexeme: Lexeme } | null {
  for (const lexemes of Object.values(MOCK_DICTIONARY_FIXTURES)) {
    for (const lexeme of lexemes) {
      for (const sense of lexeme.senses) {
        if (sense.id === senseId) {
          return { sense, lexeme };
        }
      }
    }
  }
  return null;
}

function problem(status: number, detail: string): Response {
  return HttpResponse.json(
    { status, detail },
    { status, headers: { 'Content-Type': 'application/problem+json' } }
  );
}

async function readJsonObject(request: Request): Promise<Record<string, unknown> | null> {
  try {
    const parsed: unknown = await request.json();
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>;
    }
    return null;
  } catch {
    return null;
  }
}

function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+$/.test(email);
}

function utf8ByteLength(value: string): number {
  return new TextEncoder().encode(value).byteLength;
}

export const handlers = [
  // 1. Dictionary Lookup
  http.get('/api/dictionary/lookup', ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get('q')?.toLowerCase().trim() || '';

    if (!q) {
      return problem(422, 'q must not be blank');
    }

    const fixture = MOCK_DICTIONARY_FIXTURES[q];
    if (!fixture) {
      return problem(404, `Word '${q}' not found`);
    }

    // Dynamic overlay for `saved` state (P12). Saved is per-user, so nothing is saved when signed out.
    const userCards = currentUser ? getCardsForUser(currentUser.id) : [];
    const savedSenseIds = new Set(userCards.map((c) => c.sense_id));

    const overlaidLexemes: Lexeme[] = fixture.map((lex) => ({
      ...lex,
      senses: lex.senses.map((s) => ({
        ...s,
        saved: currentUser !== null && savedSenseIds.has(s.id),
      })),
    }));

    return HttpResponse.json({
      query: q,
      lexemes: overlaidLexemes,
    });
  }),

  // 2. List Cards
  http.get('/api/cards', () => {
    if (!currentUser) {
      return problem(401, 'Authentication required');
    }
    return HttpResponse.json(getCardsForUser(currentUser.id));
  }),

  // 3. Save Card
  http.post('/api/cards', async ({ request }) => {
    if (!currentUser) {
      return problem(401, 'Authentication required');
    }

    const body = await readJsonObject(request);
    if (!body) {
      return problem(422, 'Request body is missing or malformed JSON');
    }
    const senseId = typeof body.sense_id === 'string' ? body.sense_id : '';
    if (!senseId.trim()) {
      return problem(422, 'sense_id is required');
    }

    const match = findSenseAcrossFixtures(senseId);
    if (!match) {
      return problem(404, `Sense '${senseId}' not found`);
    }

    const userCards = getCardsForUser(currentUser.id);
    const existing = userCards.find((c) => c.sense_id === senseId);
    if (existing) {
      return HttpResponse.json(existing);
    }

    const newCard: Card = {
      id: `card_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      sense_id: match.sense.id,
      lemma: match.lexeme.lemma,
      pos: match.lexeme.pos,
      transcription: match.lexeme.transcription,
      translation_ru: match.sense.translation_ru,
      synonyms_ru: match.sense.synonyms_ru,
      meanings_en: match.sense.meanings_en,
      examples: match.sense.examples,
      created_at: new Date().toISOString(),
    };

    userCards.unshift(newCard);
    return HttpResponse.json(newCard, { status: 201 });
  }),

  // 4. Delete Card
  http.delete('/api/cards/:id', ({ params }) => {
    if (!currentUser) {
      return problem(401, 'Authentication required');
    }

    const userCards = getCardsForUser(currentUser.id);
    const id = params.id as string;
    const index = userCards.findIndex((c) => c.id === id);
    if (index !== -1) {
      userCards.splice(index, 1);
      return new HttpResponse(null, { status: 204 });
    }

    return problem(404, 'Card not found');
  }),

  // 5. Auth: Me
  http.get('/api/auth/me', () => {
    if (!currentUser) {
      return problem(401, 'Not authenticated');
    }
    return HttpResponse.json(currentUser);
  }),

  // 6. Auth: Login
  http.post('/api/auth/login', async ({ request }) => {
    const body = await readJsonObject(request);
    if (!body) {
      return problem(422, 'Request body is missing or malformed JSON');
    }
    const rawEmail = typeof body.email === 'string' ? body.email : '';
    const password = typeof body.password === 'string' ? body.password : '';
    const errors: string[] = [];
    if (!rawEmail.trim()) {
      errors.push('email is required');
    }
    if (!password.trim()) {
      errors.push('password is required');
    }
    if (errors.length > 0) {
      return problem(422, errors.join('; '));
    }
    // Behave like the real backend: >72 UTF-8 byte passwords, unknown emails and wrong passwords are 401.
    if (utf8ByteLength(password) > 72) {
      return problem(401, 'Invalid email or password');
    }
    const account = registeredUsers.get(normalizeEmail(rawEmail));
    if (!account || account.password !== password) {
      return problem(401, 'Invalid email or password');
    }
    currentUser = account.user;
    return HttpResponse.json(currentUser);
  }),

  // 7. Auth: Register
  http.post('/api/auth/register', async ({ request }) => {
    const body = await readJsonObject(request);
    if (!body) {
      return problem(422, 'Request body is missing or malformed JSON');
    }
    const rawEmail = typeof body.email === 'string' ? body.email : '';
    const password = typeof body.password === 'string' ? body.password : '';
    const trimmedEmail = rawEmail.trim();
    const errors: string[] = [];
    if (!trimmedEmail) {
      errors.push('email is required');
    } else if (!isValidEmail(trimmedEmail)) {
      errors.push('email must be a valid email address');
    }
    if (!password.trim()) {
      errors.push('password is required');
    } else {
      if (password.length < 8) {
        errors.push('password must be at least 8 characters');
      }
      if (utf8ByteLength(password) > 72) {
        errors.push('password must be at most 72 bytes');
      }
    }
    if (errors.length > 0) {
      return problem(422, errors.join('; '));
    }
    const email = normalizeEmail(trimmedEmail);
    if (registeredUsers.has(email)) {
      return problem(409, 'A user with this email already exists');
    }
    const user: User = { id: `usr_mock_${nextUserNumber++}`, email };
    registeredUsers.set(email, { user, password });
    currentUser = user;
    return HttpResponse.json(currentUser, { status: 201 });
  }),

  // 8. Auth: Logout
  http.post('/api/auth/logout', () => {
    currentUser = null;
    return new HttpResponse(null, { status: 204 });
  }),
];

// Helper to reset mocks in tests
export function resetMockStore(): void {
  currentUser = initialUser();
  userCardsStore.clear();
  userCardsStore.set('usr_mock_1', initialCards());
  registeredUsers.clear();
  initialAccounts().forEach((account, email) => registeredUsers.set(email, account));
  nextUserNumber = 2;
}
