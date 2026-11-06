import { describe, it, expect } from 'vitest';
import { lookup, listCards, saveCard, deleteCard, getMe, login, logout, register } from './client';

describe('API client (interim Step 0)', () => {
  it('lookup returns lexemes and senses for known word "run"', async () => {
    const res = await lookup('run');
    expect(res.query).toBe('run');
    expect(res.lexemes.length).toBeGreaterThan(0);

    const verb = res.lexemes.find((l) => l.pos === 'verb');
    expect(verb).toBeDefined();
    expect(verb?.senses[0].translation_ru).toBe('бежать');
  });

  it('lookup handles empty string gracefully', async () => {
    const res = await lookup('   ');
    expect(res.query).toBe('');
    expect(res.lexemes).toEqual([]);
  });

  it('mock lookup matches the backend empty-query response for direct callers', async () => {
    const response = await fetch(`${globalThis.location.origin}/api/dictionary/lookup?q=%20%20`);
    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({ query: '', lexemes: [] });
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

  it('lookup gives a saved sense the id of its card, and no card id to the others', async () => {
    const [card] = await listCards();
    const senses = (await lookup('run')).lexemes.flatMap((lexeme) => lexeme.senses);

    const saved = senses.filter((sense) => sense.saved);
    expect(saved.map((sense) => sense.card_id)).toEqual([card.id]);
    expect(saved[0].id).toBe(card.sense_id);
    const unsaved = senses.filter((sense) => !sense.saved);
    expect(unsaved.length).toBeGreaterThan(0);
    expect(unsaved.every((sense) => sense.card_id === undefined)).toBe(true);
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

    // Senses for User 2 show saved: false, and not User 1's card id
    const lookupResUser2 = await lookup('run');
    expect(lookupResUser2.lexemes[0].senses[0].saved).toBe(false);
    expect(lookupResUser2.lexemes[0].senses[0].card_id).toBeUndefined();

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
