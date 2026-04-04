import { useCurrentUser } from '../hooks/useCurrentUser';

export default function DashboardPage() {
  const { data: user, isLoading } = useCurrentUser();

  const handleLogout = () => {
    window.location.href = '/auth/logout';
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center">
        <div className="text-gray-400">Loading...</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <header className="border-b border-gray-800 px-6 py-4 flex items-center justify-between">
        <h1 className="text-lg font-semibold">GitHub Portfolio Intelligence</h1>
        <div className="flex items-center gap-4">
          {user?.avatarUrl && (
            <img src={user.avatarUrl} alt={user.username} className="w-8 h-8 rounded-full" />
          )}
          <span className="text-gray-300 text-sm">{user?.username}</span>
          <button
            onClick={handleLogout}
            className="text-sm text-gray-400 hover:text-white transition-colors"
          >
            Log out
          </button>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-6 py-12">
        <h2 className="text-2xl font-bold mb-2">Welcome, {user?.username}!</h2>
        <p className="text-gray-400">Repository sync coming in Phase 2.</p>
      </main>
    </div>
  );
}
