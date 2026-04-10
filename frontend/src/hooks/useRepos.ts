import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';

export function useRepos() {
  return useQuery({
    queryKey: ['repos'],
    queryFn: api.repos.list,
    staleTime: 2 * 60 * 1000,
  });
}

export function useSyncRepos() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: api.repos.sync,
    onSuccess: (data) => {
      queryClient.setQueryData(['repos'], data);
    },
  });
}
