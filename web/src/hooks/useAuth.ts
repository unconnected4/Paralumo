import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getMe, login, logout, register, type User } from '../api';

// Lookups don't depend on the user, so signing in or out leaves them cached. Saved state is keyed
// by user (useSavedSenses), and signing out drops it.
export function useAuth() {
  const queryClient = useQueryClient();

  const userQuery = useQuery<User | null>({
    queryKey: ['auth', 'me'],
    queryFn: getMe,
    staleTime: Infinity,
  });

  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: (user) => {
      queryClient.setQueryData(['auth', 'me'], user);
      queryClient.invalidateQueries({ queryKey: ['cards'] });
    },
  });

  const registerMutation = useMutation({
    mutationFn: register,
    onSuccess: (user) => {
      queryClient.setQueryData(['auth', 'me'], user);
      queryClient.invalidateQueries({ queryKey: ['cards'] });
    },
  });

  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.setQueryData(['auth', 'me'], null);
      queryClient.removeQueries({ queryKey: ['saved'] });
      queryClient.invalidateQueries({ queryKey: ['cards'] });
    },
  });

  return {
    user: userQuery.data ?? null,
    isAuthenticated: !!userQuery.data,
    isLoading: userQuery.isLoading,
    login: loginMutation.mutateAsync,
    isLoggingIn: loginMutation.isPending,
    loginError: loginMutation.error,
    register: registerMutation.mutateAsync,
    isRegistering: registerMutation.isPending,
    registerError: registerMutation.error,
    logout: logoutMutation.mutateAsync,
    isLoggingOut: logoutMutation.isPending,
  };
}
