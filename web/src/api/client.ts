import createClient from 'openapi-fetch';
import type { paths } from './schema';
import type { Card, LookupResponse, User } from './types';

// Requests are typed against the generated spec (./schema.d.ts, `npm run gen:api`).
const api = createClient<paths>({
  // Empty means same origin: the Vite proxy locally, the same routing in production.
  baseUrl: import.meta.env.VITE_API_BASE_URL || globalThis.location.origin,
  // Look up fetch on every request, so tools that patch globalThis.fetch (MSW) see the calls.
  fetch: (request) => globalThis.fetch(request),
});

/** A non-2xx response. `message` is the backend's `detail` when it sent one. */
export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

function toApiError(response: Response, body: unknown): ApiError {
  let errorMsg = `API request failed: ${response.status} ${response.statusText}`;
  if (body && typeof body === 'object') {
    const { detail, message } = body as { detail?: unknown; message?: unknown };
    if (detail) {
      errorMsg = typeof detail === 'string' ? detail : JSON.stringify(detail);
    } else if (typeof message === 'string' && message) {
      errorMsg = message;
    }
  }
  return new ApiError(errorMsg, response.status);
}

async function unwrap<T>(request: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  const { data, error, response } = await request;
  if (!response.ok) {
    throw toApiError(response, error);
  }
  return data as T;
}

export async function lookup(q: string): Promise<LookupResponse> {
  const trimmed = q.trim();
  if (!trimmed) {
    return { query: '', lexemes: [] };
  }
  return unwrap(api.GET('/api/dictionary/lookup', { params: { query: { q: trimmed } } }));
}

export async function listCards(): Promise<Card[]> {
  return unwrap(api.GET('/api/cards'));
}

export async function saveCard(senseId: string): Promise<Card> {
  return unwrap(api.POST('/api/cards', { body: { sense_id: senseId } }));
}

export async function deleteCard(cardId: string): Promise<void> {
  await unwrap(api.DELETE('/api/cards/{cardId}', { params: { path: { cardId } } }));
}

export async function login(credentials: { email: string; password: string }): Promise<User> {
  return unwrap(api.POST('/api/auth/login', { body: credentials }));
}

export async function register(credentials: { email: string; password: string }): Promise<User> {
  return unwrap(api.POST('/api/auth/register', { body: credentials }));
}

export async function logout(): Promise<void> {
  await unwrap(api.POST('/api/auth/logout'));
}

export async function getMe(): Promise<User | null> {
  const { data, error, response } = await api.GET('/api/auth/me');
  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw toApiError(response, error);
  }
  return data ?? null;
}
