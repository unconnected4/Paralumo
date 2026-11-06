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

export const handlers = [
  // 1. Dictionary Lookup
  http.get('/api/dictionary/lookup', ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get('q')?.toLowerCase().trim() || '';

    if (!q) {
      return HttpResponse.json({ query: '', lexemes: [] });
    }

    const fixture = MOCK_DICTIONARY_FIXTURES[q];
    if (!fixture) {
      return HttpResponse.json(
        { detail: `Word "${q}" not found in dictionary. Try "run", "bank", or "light".` },
        { status: 404 }
      );
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
      return HttpResponse.json({ detail: 'Authentication required' }, { status: 401 });
    }
    return HttpResponse.json(getCardsForUser(currentUser.id));
  }),

  // 3. Save Card
  http.post('/api/cards', async ({ request }) => {
    if (!currentUser) {
      return HttpResponse.json({ detail: 'Authentication required' }, { status: 401 });
    }

    const body = (await request.json()) as { sense_id?: string; notes?: string };
    if (!body?.sense_id) {
      return HttpResponse.json({ detail: 'sense_id is required' }, { status: 422 });
    }

    const match = findSenseAcrossFixtures(body.sense_id);
    if (!match) {
      return HttpResponse.json({ detail: 'Sense not found' }, { status: 404 });
    }

    const userCards = getCardsForUser(currentUser.id);
    const existing = userCards.find((c) => c.sense_id === body.sense_id);
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
      notes: body.notes,
    };

    userCards.unshift(newCard);
    return HttpResponse.json(newCard, { status: 201 });
  }),

  // 4. Delete Card
  http.delete('/api/cards/:id', ({ params }) => {
    if (!currentUser) {
      return HttpResponse.json({ detail: 'Authentication required' }, { status: 401 });
    }

    const userCards = getCardsForUser(currentUser.id);
    const id = params.id as string;
    const index = userCards.findIndex((c) => c.id === id);
    if (index !== -1) {
      userCards.splice(index, 1);
      return new HttpResponse(null, { status: 204 });
    }

    return HttpResponse.json({ detail: 'Card not found' }, { status: 404 });
  }),

  // 5. Auth: Me
  http.get('/api/auth/me', () => {
    if (!currentUser) {
      return HttpResponse.json({ detail: 'Not authenticated' }, { status: 401 });
    }
    return HttpResponse.json(currentUser);
  }),

  // 6. Auth: Login
  http.post('/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as { email?: string; password?: string };
    if (!body?.email || !body?.password) {
      return HttpResponse.json({ detail: 'Email and password required' }, { status: 422 });
    }
    // Behave like a real backend: unknown email or wrong password is a 401, never an implicit sign-up.
    const account = registeredUsers.get(body.email);
    if (!account || account.password !== body.password) {
      return HttpResponse.json({ detail: 'Invalid email or password' }, { status: 401 });
    }
    currentUser = account.user;
    return HttpResponse.json(currentUser);
  }),

  // 7. Auth: Register
  http.post('/api/auth/register', async ({ request }) => {
    const body = (await request.json()) as { email?: string; password?: string };
    if (!body?.email || !body?.password) {
      return HttpResponse.json({ detail: 'Email and password required' }, { status: 422 });
    }
    if (registeredUsers.has(body.email)) {
      return HttpResponse.json({ detail: 'A user with this email already exists' }, { status: 409 });
    }
    const user: User = { id: `usr_mock_${nextUserNumber++}`, email: body.email };
    registeredUsers.set(body.email, { user, password: body.password });
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
