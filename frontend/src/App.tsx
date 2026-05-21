import { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useCurrentUser } from './hooks/useCurrentUser';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import ResultsPage from './pages/ResultsPage';
import WorkspacePage from './pages/WorkspacePage';
import AnalysisStatusPage from './pages/AnalysisStatusPage';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { data: user, isLoading, error, refetch } = useCurrentUser();
  const [slowLoad, setSlowLoad] = useState(false);
  const [timedOut, setTimedOut] = useState(false);

  useEffect(() => {
    if (!isLoading) { setSlowLoad(false); setTimedOut(false); return; }
    const slow = setTimeout(() => setSlowLoad(true), 5_000);
    const timeout = setTimeout(() => setTimedOut(true), 18_000);
    return () => { clearTimeout(slow); clearTimeout(timeout); };
  }, [isLoading]);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#0a0906] flex flex-col items-center justify-center gap-4">
        {timedOut ? (
          <>
            <p className="text-amber-400 text-sm font-mono">Connection is taking too long.</p>
            <p className="text-stone-500 text-xs">The server may be waking up — try again in a moment.</p>
            <button
              onClick={() => { setSlowLoad(false); setTimedOut(false); refetch(); }}
              className="mt-2 px-4 py-2 bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/30 text-amber-400 text-sm rounded-lg transition-colors"
            >
              Retry
            </button>
          </>
        ) : (
          <>
            <div className="w-5 h-5 border-2 border-amber-500/60 border-t-transparent rounded-full animate-spin" />
            {slowLoad && (
              <p className="text-stone-500 text-xs font-mono animate-pulse">Connecting to server…</p>
            )}
          </>
        )}
      </div>
    );
  }

  if (error || !user) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <DashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/results/:repoId"
          element={
            <ProtectedRoute>
              <ResultsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/workspace"
          element={
            <ProtectedRoute>
              <WorkspacePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/status"
          element={
            <ProtectedRoute>
              <AnalysisStatusPage />
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
