import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useCurrentUser } from './hooks/useCurrentUser';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import ResultsPage from './pages/ResultsPage';
import WorkspacePage from './pages/WorkspacePage';
import AnalysisStatusPage from './pages/AnalysisStatusPage';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { data: user, isLoading, error } = useCurrentUser();

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-950 flex items-center justify-center">
        <div className="text-gray-400">Loading...</div>
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
