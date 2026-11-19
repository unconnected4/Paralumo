import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { ApiError, deleteCard, listCards, listSavedSenses, saveCard, type Card } from '../api';
import { useAuth } from './useAuth';

/** Saved state of some senses: sense id to the user's card id, or null when not saved. */
export type SavedCardIds = Record<string, string | null>;

// Keyed by user, so one user's saved state is never shown to the next one in the same tab.
// Signing out removes every query under ['saved'] (useAuth).
const savedKey = (userId: string | undefined) => ['saved', userId ?? null] as const;

// Save and unsave write the saved state they know into every cached saved-state query, so the
// buttons don't wait for a refetch. The lookup itself doesn't depend on the user and stays cached.
function setSavedState(queryClient: QueryClient, update: (saved: SavedCardIds) => SavedCardIds) {
  queryClient.setQueriesData<SavedCardIds>({ queryKey: ['saved'] }, (saved) => saved && update(saved));
}

export function useCards() {
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuth();

  // Cards belong to a user, so don't ask for them while signed out (the API answers 401).
  const cardsQuery = useQuery<Card[], Error>({
    queryKey: ['cards'],
    queryFn: listCards,
    enabled: isAuthenticated,
  });

  // A mutation stays pending until the card list has refetched, so My Words never shows a removed
  // card again for a moment.
  const refetchCards = () => queryClient.invalidateQueries({ queryKey: ['cards'] });

  const saveMutation = useMutation({
    mutationFn: (senseId: string) => saveCard(senseId),
    onSuccess: (card) => {
      setSavedState(queryClient, (saved) =>
        saved[card.sense_id] !== undefined ? { ...saved, [card.sense_id]: card.id } : saved
      );
      return refetchCards();
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (cardId: string) => deleteCard(cardId),
    onSuccess: (_, cardId) => {
      setSavedState(queryClient, (saved) =>
        Object.fromEntries(
          Object.entries(saved).map(([senseId, savedCardId]) => [
            senseId,
            savedCardId === cardId ? null : savedCardId,
          ])
        )
      );
      return refetchCards();
    },
  });

  return {
    cards: isAuthenticated ? (cardsQuery.data ?? []) : [],
    isLoading: cardsQuery.isLoading,
    isError: cardsQuery.isError,
    error: cardsQuery.error,
    saveCard: saveMutation.mutate,
    isSaving: saveMutation.isPending,
    savingSenseId: saveMutation.variables,
    deleteCard: deleteMutation.mutate,
    isDeleting: deleteMutation.isPending,
    deletingCardId: deleteMutation.variables,
  };
}

/**
 * Which of a lookup's senses the signed-in user has saved. Never requested while signed out or for
 * a lookup without senses; `savedCardIds` is undefined until the answer arrives.
 */
export function useSavedSenses(senseIds: string[]) {
  const { user } = useAuth();

  const savedQuery = useQuery<SavedCardIds, ApiError>({
    queryKey: [...savedKey(user?.id), senseIds],
    queryFn: async () => {
      const saved = await listSavedSenses(senseIds);
      const cardIds: SavedCardIds = Object.fromEntries(senseIds.map((id) => [id, null]));
      for (const { sense_id, card_id } of saved) {
        cardIds[sense_id] = card_id;
      }
      return cardIds;
    },
    enabled: !!user && senseIds.length > 0,
    retry: 1,
  });

  return {
    savedCardIds: savedQuery.data,
    isError: savedQuery.isError,
    isRetrying: savedQuery.isFetching,
    retry: () => savedQuery.refetch(),
  };
}
