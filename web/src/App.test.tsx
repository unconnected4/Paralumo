import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import App from './App';
import { listCards, logout } from './api';
import { server } from './test/setup';

describe('Paralumo App', () => {
  beforeEach(() => {
    window.history.pushState({}, '', '/');
  });

  it('renders the header and an empty search state by default', async () => {
    render(<App />);

    expect(screen.getByText('Paralumo')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /search/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /my words/i })).toBeInTheDocument();
    expect(await screen.findByText('Enter a word to explore senses')).toBeInTheDocument();
  });

  it('searches the word given in the URL', async () => {
    window.history.pushState({}, '', '/?q=run');
    render(<App />);

    expect(await screen.findByText('бежать')).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/search an english word/i)).toHaveValue('run');
  });

  it('allows searching for another word like "bank" and records it in the URL', async () => {
    const user = userEvent.setup();
    render(<App />);

    const input = screen.getByPlaceholderText(/search an english word/i);
    await user.type(input, 'bank');
    await user.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => {
      expect(screen.getByText('банк')).toBeInTheDocument();
      expect(screen.getByText('берег')).toBeInTheDocument();
    });
    expect(window.location.search).toBe('?q=bank');
  });

  it('follows the browser back button to the previous search', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/?q=bank');
    render(<App />);
    await screen.findByText('банк');

    await user.click(screen.getByRole('button', { name: 'light' }));
    await screen.findByText('свет');

    await act(async () => {
      window.history.back();
    });

    expect(await screen.findByText('банк')).toBeInTheDocument();
    expect(screen.queryByText('свет')).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText(/search an english word/i)).toHaveValue('bank');
  });

  it('navigates to My Words page and displays saved cards', async () => {
    const user = userEvent.setup();
    render(<App />);

    const myWordsLink = screen.getByRole('link', { name: /my words/i });
    await user.click(myWordsLink);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /my words/i })).toBeInTheDocument();
      expect(screen.getByText('Personal vocabulary deck saved with per-sense precision.')).toBeInTheDocument();
    });
  });

  it('shows "Word not found" when the lookup answers 404', async () => {
    window.history.pushState({}, '', '/?q=zzzz');
    render(<App />);

    expect(await screen.findByText('Word not found')).toBeInTheDocument();
  });

  it('shows "Lookup failed", not "Word not found", when the lookup fails for another reason', async () => {
    server.use(
      http.get('/api/dictionary/lookup', () =>
        HttpResponse.json({ detail: 'Dictionary provider unavailable' }, { status: 502 })
      )
    );
    window.history.pushState({}, '', '/?q=run');
    render(<App />);

    expect(await screen.findByText('Lookup failed', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByText('Word not found')).not.toBeInTheDocument();
  });

  it('sends a signed-out user from My Words to sign in', async () => {
    await logout();
    window.history.pushState({}, '', '/my-words');
    render(<App />);

    expect(await screen.findByLabelText(/email address/i)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /my words/i })).not.toBeInTheDocument();
  });

  it('sends a signed-out user who clicks Save to sign in, then back to the same search', async () => {
    const user = userEvent.setup();
    await logout();
    window.history.pushState({}, '', '/?q=run');
    render(<App />);

    const saveButtons = await screen.findAllByRole('button', { name: /^save$/i });
    await user.click(saveButtons[0]);

    await user.type(await screen.findByLabelText(/email address/i), 'learner@example.com');
    await user.type(screen.getByLabelText(/password/i), 'password123{enter}');

    expect(await screen.findByText('бежать')).toBeInTheDocument();
    expect(window.location.search).toBe('?q=run');
    // The lookup was cached while signed out; signing in refetches it with this user's saved flags.
    expect(await screen.findByText('Saved')).toBeInTheDocument();
  });

  it('unsaves a saved sense from the search page and saves it again', async () => {
    const user = userEvent.setup();
    window.history.pushState({}, '', '/?q=run');
    render(<App />);

    // The signed-in mock user starts with one saved sense of "run".
    await user.click(await screen.findByRole('button', { name: 'Saved' }));

    await waitFor(() => {
      expect(screen.queryByRole('button', { name: 'Saved' })).not.toBeInTheDocument();
    });
    expect(await listCards()).toEqual([]);

    await user.click(screen.getAllByRole('button', { name: 'Save' })[0]);

    expect(await screen.findByRole('button', { name: 'Saved' })).toBeInTheDocument();
    expect(await listCards()).toHaveLength(1);
  });

  it('does not show mock-only labels in the UI', async () => {
    window.history.pushState({}, '', '/?q=run');
    render(<App />);
    await screen.findByText('бежать');

    expect(screen.queryByText(/mock/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/msw/i)).not.toBeInTheDocument();
  });
});
