import { describe, it, expect } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../test/setup';
import {
  lookup,
  listCards,
  listSavedSenses,
  saveCard,
  deleteCard,
  getMe,
  login,
  logout,
  register,
} from './client';

describe('API client (interim Step 0)', () => {
  it('lookup returns lexemes and senses for known word "run"', async () => {
    const res = await lookup('run');
    expect(res.query).toBe('run');
    expect(res.source_lang).toBe('en');
    expect(res.target_lang).toBe('ru');
    expect(res.lexemes.length).toBeGreaterThan(0);

    const verb = res.lexemes.find((l) => l.pos === 'verb');
    expect(verb).toBeDefined();
    expect(verb?.source_lang).toBe('en');
    expect(verb?.senses[0].target_lang).toBe('ru');
    expect(verb?.senses[0].translation).toBe('бежать');
    expect(verb?.senses[0]).not.toHaveProperty('saved');
    expect(verb?.senses[0]).not.toHaveProperty('card_id');
  });

  it('lookup handles empty string gracefully, like the backend', async () => {
    const res = await lookup('   ');
    expect(res).toEqual({ query: '', source_lang: 'en', target_lang: 'ru', lexemes: [] });
  });

  it('lookup is the same signed in and signed out', async () => {
    const signedIn = await lookup('run');
    await logout();
    expect(await lookup('run')).toEqual(signedIn);
  });

  it('mock 404 and malformed-body details match the backend wording', async () => {
    await expect(lookup('  Zzyzx ')).rejects.toMatchObject({
      status: 404,
      message: 'Word "zzyzx" not found',
    });
    await expect(saveCard('sense_unknown')).rejects.toMatchObject({
      status: 404,
      message: 'Sense not found',
    });
    const malformed = await fetch(`${globalThis.location.origin}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{',
    });
    expect(malformed.status).toBe(422);
    await expect(malformed.json()).resolves.toMatchObject({
      detail: 'Request body is missing or not valid JSON',
    });
  });

  it('listSavedSenses gives the saved senses their card ids and leaves the others out', async () => {
    const [card] = await listCards();
    const senseIds = (await lookup('run')).lexemes.flatMap((lexeme) => lexeme.senses.map((s) => s.id));

    expect(await listSavedSenses([...senseIds, 'sense_unknown'])).toEqual([
      { sense_id: card.sense_id, card_id: card.id },
    ]);
  });

  it('listSavedSenses sends repeated sense_id parameters', async () => {
    let query = '';
    server.use(
      http.get('/api/cards/saved', ({ request }) => {
        query = new URL(request.url).search;
        return HttpResponse.json([]);
      })
    );

    await listSavedSenses(['s_a', 's_b']);
    expect(query).toBe('?sense_id=s_a&sense_id=s_b');
  });

  it('listSavedSenses is 401 when signed out and 422 without ids, as in the backend', async () => {
    await expect(listSavedSenses([])).rejects.toMatchObject({
      status: 422,
      message: 'sense_id is required',
    });
    await logout();
    await expect(listSavedSenses(['sense_run_v_1'])).rejects.toMatchObject({ status: 401 });
  });

  it('listCards returns saved cards', async () => {
    const cards = await listCards();
    expect(Array.isArray(cards)).toBe(true);
    expect(cards.length).toBeGreaterThan(0);
    expect(cards[0].lemma).toBe('run');
  });

  it('saveCard adds a new card and deleteCard removes it', async () => {
    const newCard = await saveCard('sense_bank_n_1');
    expect(newCard.sense_id).toBe('sense_bank_n_1');
    expect(newCard.lemma).toBe('bank');

    const updated = await listCards();
    expect(updated.some((c) => c.sense_id === 'sense_bank_n_1')).toBe(true);

    await deleteCard(newCard.id);
    const afterDelete = await listCards();
    expect(afterDelete.some((c) => c.sense_id === 'sense_bank_n_1')).toBe(false);
  });

  it('handles auth lifecycle: getMe, logout, login', async () => {
    const initialUser = await getMe();
    expect(initialUser).not.toBeNull();
    expect(initialUser?.email).toBe('learner@example.com');

    await logout();
    const loggedOutUser = await getMe();
    expect(loggedOutUser).toBeNull();

    const loggedInUser = await login({ email: 'learner@example.com', password: 'password123' });
    expect(loggedInUser.email).toBe('learner@example.com');
  });

  it('rejects login with an unknown email or a wrong password', async () => {
    await logout();
    await expect(login({ email: 'nobody@example.com', password: 'password123' })).rejects.toMatchObject({
      status: 401,
    });
    await expect(login({ email: 'learner@example.com', password: 'wrong' })).rejects.toMatchObject({
      status: 401,
    });
    await expect(
      login({ email: 'learner@example.com', password: 'пароль'.repeat(6) + 'x' })
    ).rejects.toMatchObject({
      status: 401,
    });
    expect(await getMe()).toBeNull();
  });

  it('rejects registering an email that already exists, ignoring case and outer spaces', async () => {
    await expect(
      register({ email: '  LEARNER@example.com  ', password: 'otherpass123' })
    ).rejects.toMatchObject({
      status: 409,
      message: 'A user with this email already exists',
    });
  });

  it('validates registration email format and password bounds with 422 problem details', async () => {
    await expect(register({ email: 'not-an-email', password: 'short' })).rejects.toMatchObject({
      status: 422,
      message: 'email must be a valid email address; password must be at least 8 characters',
    });
    await expect(
      register({ email: 'long@example.com', password: 'пароль'.repeat(6) + 'x' })
    ).rejects.toMatchObject({
      status: 422,
      message: 'password must be at most 72 bytes',
    });
  });

  it('normalizes email on register and login and gives each user a distinct id', async () => {
    const a = await register({ email: '  Alice@Example.com ', password: 'secret123' });
    const b = await register({ email: 'b@example.com', password: 'secret123' });
    expect(a.email).toBe('alice@example.com');
    expect(a.id).not.toBe(b.id);

    await logout();
    const signedIn = await login({ email: ' ALICE@example.com ', password: 'secret123' });
    expect(signedIn.id).toBe(a.id);
    expect(signedIn.email).toBe('alice@example.com');
  });

  it('isolates saved cards and saved flag between different users', async () => {
    // User 1 has the seeded card "run"
    const user1Cards = await listCards();
    expect(user1Cards.length).toBe(1);
    expect(user1Cards[0].lemma).toBe('run');

    // Register User 2
    await register({ email: 'user2@example.com', password: 'secret123' });
    const user2Cards = await listCards();
    expect(user2Cards).toEqual([]);

    // User 2 hasn't saved User 1's sense, and doesn't see User 1's card
    expect(await listSavedSenses([user1Cards[0].sense_id])).toEqual([]);

    // User 2 saves bank
    await saveCard('sense_bank_n_1');
    const user2Updated = await listCards();
    expect(user2Updated.length).toBe(1);
    expect(user2Updated[0].lemma).toBe('bank');

    // Log back in as User 1
    await login({ email: 'learner@example.com', password: 'password123' });
    const user1After = await listCards();
    expect(user1After.length).toBe(1);
    expect(user1After[0].lemma).toBe('run');
  });
});
