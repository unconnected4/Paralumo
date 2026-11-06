import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { deleteCard, listCards, saveCard, type Card } from '../api';
import { useAuth } from './useAuth';

export function useCards() {
  const queryClient = useQueryClient();
  const { isAuthenticated } = useAuth();

  // Cards belong to a user, so don't ask for them while signed out (the API answers 401).
  const cardsQuery = useQuery<Card[], Error>({
    queryKey: ['cards'],
    queryFn: listCards,
    enabled: isAuthenticated,
  });

  // A mutation stays pending until this resolves, so a button goes straight from "Saving..." to
  // '✓' (or from "Removing..." to '+') without showing its old state while the lookup refetches.
  const refetchCardsAndLookups = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['cards'] }),
      queryClient.invalidateQueries({ queryKey: ['dictionary'] }),
    ]);

  const saveMutation = useMutation({
    mutationFn: (senseId: string) => saveCard(senseId),
    onSuccess: refetchCardsAndLookups,
  });

  const deleteMutation = useMutation({
    mutationFn: (cardId: string) => deleteCard(cardId),
    onSuccess: refetchCardsAndLookups,
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
