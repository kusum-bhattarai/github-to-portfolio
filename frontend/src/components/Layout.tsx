import { useNavigate } from 'react-router-dom';
import { useCurrentUser } from '../hooks/useCurrentUser';
import { BACKEND_URL } from '../lib/api';

interface NavLink {
  label: string;
  to: string;
}

interface LayoutProps {
  children: React.ReactNode;
  navLinks?: NavLink[];
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl' | '2xl';
}

const MAX_WIDTHS = {
  sm:  'max-w-xl',
  md:  'max-w-2xl',
  lg:  'max-w-3xl',
  xl:  'max-w-5xl',
  '2xl': 'max-w-6xl',
};

export default function Layout({ children, navLinks = [], maxWidth = 'xl' }: LayoutProps) {
  const navigate = useNavigate();
  const { data: user } = useCurrentUser();
  const mw = MAX_WIDTHS[maxWidth];

  return (
    <div className="min-h-screen" style={{ background: 'var(--bg)', color: 'var(--text-primary)' }}>
      <header
        style={{ borderBottom: '1px solid var(--border)', background: 'rgba(10,9,6,0.85)' }}
        className="sticky top-0 z-40 px-6 py-3.5 backdrop-blur-md flex items-center justify-between"
      >
        {/* Brand */}
        <button
          onClick={() => navigate('/dashboard')}
          className="flex items-center gap-2.5 group"
        >
          <span
            className="font-display text-base tracking-tight transition-colors"
            style={{ color: 'var(--accent)' }}
          >
            Portify
          </span>
          <span
            className="text-xs font-mono-dm transition-colors hidden sm:block"
            style={{ color: 'var(--text-muted)' }}
          >
            / portfolio intelligence
          </span>
        </button>

        {/* Nav */}
        <nav className="flex items-center gap-1">
          {navLinks.map(link => (
            <button
              key={link.to}
              onClick={() => navigate(link.to)}
              className="px-3 py-1.5 text-xs rounded-md transition-all"
              style={{ color: 'var(--text-secondary)' }}
              onMouseEnter={e => {
                (e.currentTarget as HTMLElement).style.color = 'var(--text-primary)';
                (e.currentTarget as HTMLElement).style.background = 'var(--bg-card)';
              }}
              onMouseLeave={e => {
                (e.currentTarget as HTMLElement).style.color = 'var(--text-secondary)';
                (e.currentTarget as HTMLElement).style.background = 'transparent';
              }}
            >
              {link.label}
            </button>
          ))}

          {/* Divider */}
          {navLinks.length > 0 && (
            <span className="w-px h-4 mx-1" style={{ background: 'var(--border-light)' }} />
          )}

          {/* User */}
          {user && (
            <div className="flex items-center gap-2 ml-1">
              {user.avatarUrl && (
                <img
                  src={user.avatarUrl}
                  alt={user.username}
                  className="w-6 h-6 rounded-full"
                  style={{ border: '1px solid var(--border-light)' }}
                />
              )}
              <span className="text-xs hidden sm:block" style={{ color: 'var(--text-muted)' }}>
                {user.username}
              </span>
            </div>
          )}

          <button
            onClick={() => { window.location.href = `${BACKEND_URL}/auth/logout`; }}
            className="ml-1 px-3 py-1.5 text-xs rounded-md transition-all"
            style={{ color: 'var(--text-muted)' }}
            onMouseEnter={e => {
              (e.currentTarget as HTMLElement).style.color = 'var(--red)';
              (e.currentTarget as HTMLElement).style.background = 'rgba(248,113,113,0.08)';
            }}
            onMouseLeave={e => {
              (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)';
              (e.currentTarget as HTMLElement).style.background = 'transparent';
            }}
          >
            Sign out
          </button>
        </nav>
      </header>

      <main className={`${mw} mx-auto px-6 py-8`}>
        {children}
      </main>
    </div>
  );
}
