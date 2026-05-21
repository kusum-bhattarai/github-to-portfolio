const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

const FEATURES = [
  { label: 'Portfolio summaries', desc: 'AI-written narratives that explain what you actually built' },
  { label: 'Resume bullets', desc: 'Impact-first lines pulled from real commits and code' },
  { label: 'Tech stack detection', desc: 'Accurate — reads your code, no hallucinations' },
  { label: 'Interview story mode', desc: 'Situation, task, action, result — ready to practice' },
];

export default function LoginPage() {
  return (
    <div
      className="min-h-screen flex flex-col items-center justify-center px-4 relative overflow-hidden"
      style={{ background: 'var(--bg)' }}
    >
      {/* Ambient glow */}
      <div
        className="absolute top-0 left-1/2 -translate-x-1/2 w-[700px] h-[400px] rounded-full blur-[120px] pointer-events-none"
        style={{ background: 'radial-gradient(ellipse, rgba(240,160,48,0.06) 0%, transparent 70%)' }}
      />

      <div className="relative w-full max-w-sm animate-fade-up">
        {/* Brand */}
        <div className="text-center mb-10">
          <p
            className="font-display text-5xl mb-3 leading-none"
            style={{ color: 'var(--accent)' }}
          >
            Portify
          </p>
          <p
            className="text-sm leading-relaxed"
            style={{ color: 'var(--text-secondary)' }}
          >
            Turn your GitHub history into<br />portfolio-ready content — automatically.
          </p>
        </div>

        {/* Card */}
        <div
          className="rounded-2xl p-7"
          style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--border)',
          }}
        >
          {/* Feature list */}
          <ul className="space-y-4 mb-8">
            {FEATURES.map((f, i) => (
              <li key={f.label} className={`flex items-start gap-3 animate-fade-up stagger-${i + 1}`}>
                <span
                  className="mt-0.5 w-4 h-4 shrink-0 rounded-full flex items-center justify-center"
                  style={{ background: 'var(--accent-glow)', border: '1px solid var(--accent-border)' }}
                >
                  <svg className="w-2.5 h-2.5" viewBox="0 0 12 12" fill="none">
                    <path d="M2 6l3 3 5-5" stroke="var(--accent)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </span>
                <div>
                  <p className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{f.label}</p>
                  <p className="text-xs mt-0.5 leading-relaxed" style={{ color: 'var(--text-muted)' }}>{f.desc}</p>
                </div>
              </li>
            ))}
          </ul>

          {/* CTA */}
          <button
            onClick={() => { window.location.href = `${BACKEND_URL}/oauth2/authorization/github`; }}
            className="w-full flex items-center justify-center gap-2.5 py-3 px-5 rounded-xl text-sm font-semibold transition-all duration-200 group"
            style={{
              background: 'var(--text-primary)',
              color: 'var(--bg)',
            }}
            onMouseEnter={e => {
              (e.currentTarget as HTMLElement).style.background = '#fffff0';
              (e.currentTarget as HTMLElement).style.transform = 'translateY(-1px)';
              (e.currentTarget as HTMLElement).style.boxShadow = '0 8px 24px rgba(240,160,48,0.15)';
            }}
            onMouseLeave={e => {
              (e.currentTarget as HTMLElement).style.background = 'var(--text-primary)';
              (e.currentTarget as HTMLElement).style.transform = 'translateY(0)';
              (e.currentTarget as HTMLElement).style.boxShadow = 'none';
            }}
          >
            <svg className="w-4 h-4" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z" />
            </svg>
            Continue with GitHub
          </button>
        </div>

        <p
          className="text-center text-xs mt-5"
          style={{ color: 'var(--text-muted)' }}
        >
          Read-only access · No data stored beyond what you authorize
        </p>
      </div>
    </div>
  );
}
