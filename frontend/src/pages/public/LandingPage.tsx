import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { authPath, type AccountRole } from '../../utils/roles';

interface WorkspacePath {
  role: AccountRole;
  title: string;
  copy: string;
  bullets: string[];
  primary: { label: string; to: string };
  secondary?: { label: string; to: string };
}

const WORKSPACE_PATHS: WorkspacePath[] = [
  {
    role: 'CANDIDATE',
    title: 'For candidates',
    copy:
      'Find a published role, submit your application and follow it without guessing what happened next.',
    bullets: [
      'Browse open roles and filter by location',
      'Apply with a résumé link',
      'Track application and interview status',
      'Keep documents and notifications in one place',
    ],
    primary: {
      label: 'Create candidate account',
      to: authPath('/register', 'CANDIDATE'),
    },
    secondary: { label: 'Browse open roles', to: '/jobs' },
  },
  {
    role: 'RECRUITER',
    title: 'For recruiters',
    copy:
      'Publish roles for your organization, review applicants and carry each shortlisted candidate through a recorded decision.',
    bullets: [
      'Create and publish organization roles',
      'Look up applications and shortlist candidates',
      'Schedule interviews and record outcomes',
      'Set the final hire or reject decision',
    ],
    primary: {
      label: 'Create recruiter account',
      to: authPath('/register', 'RECRUITER'),
    },
    secondary: {
      label: 'Recruiter sign in',
      to: authPath('/login', 'RECRUITER'),
    },
  },
  {
    role: 'ADMIN',
    title: 'For administrators',
    copy:
      'Review the operating picture across recruitment records and confirm the decisions recorded by the platform.',
    bullets: [
      'Read-only overview of live recruitment activity',
      'Inspect any recruitment record and its decisions',
      'Look up applications without changing candidate data',
      'Confirm the signed-in identity and its role scope',
    ],
    primary: {
      label: 'Administrator sign in',
      to: authPath('/login', 'ADMIN'),
    },
  },
];

const LIFECYCLE = [
  {
    code: 'APPLIED',
    tone: 'blue',
    title: 'Application received',
    copy: 'A candidate submits a résumé link against a published role.',
  },
  {
    code: 'UNDER_REVIEW',
    tone: 'amber',
    title: 'Recruiter review',
    copy: 'The application enters a review state owned by the hiring team.',
  },
  {
    code: 'SHORTLISTED',
    tone: 'violet',
    title: 'Shortlisted',
    copy: 'A recruitment record begins and interview planning can start.',
  },
  {
    code: 'SCHEDULED',
    tone: 'amber',
    title: 'Interview scheduled',
    copy: 'Date, start time, interviewer and location are recorded.',
  },
  {
    code: 'HIRED',
    tone: 'green',
    title: 'Decision recorded',
    copy: 'A hire or rejection closes the recruitment’s final status.',
  },
] as const;

const PIPELINE = [
  {
    title: 'Applied',
    count: 12,
    candidates: [
      { initials: 'AR', name: 'Ananya R.', detail: 'Product designer · 6 yrs', tone: 'lavender' },
      { initials: 'KM', name: 'Kiran M.', detail: 'UX lead · 8 yrs', tone: 'peach' },
    ],
  },
  {
    title: 'Interview',
    count: 4,
    candidates: [
      { initials: 'MS', name: 'Meera S.', detail: 'Today, 10:30', tone: 'blue' },
      { initials: 'ZK', name: 'Zoya K.', detail: 'Thu, 14:00', tone: 'mint' },
    ],
  },
  {
    title: 'Decision',
    count: 2,
    candidates: [
      { initials: 'VP', name: 'Vikram P.', detail: 'Feedback in', tone: 'lime' },
      { initials: 'AN', name: 'Arjun N.', detail: 'Review', tone: 'lavender' },
    ],
  },
];

/**
 * Public landing page.
 *
 * Every claim maps to an implemented capability: job discovery, applications,
 * recruitment tracking, document ownership, notifications, and role-scoped
 * administration. The hero window illustrates the real application lifecycle
 * rather than showing invented live metrics.
 */
export function LandingPage() {
  const { user } = useAuth();

  return (
    <div className="landing-page">
      {/* Hero */}
      <section className="lp-hero">
        <div className="lp-hero-glow" aria-hidden="true" />
        <div className="lp-shell lp-hero-grid">
          <div className="lp-hero-copy">
            <p className="lp-eyebrow">
              <span className="lp-eyebrow-dot" aria-hidden="true" />
              Employment decision architecture
            </p>
            <h1 className="lp-hero-title">
              Hire with a <span className="lp-mark">memory.</span>
            </h1>
            <p className="lp-hero-lede">
              Every role, application, interview and outcome on one accountable
              record — so candidates always know where they stand, and recruiters
              can defend every decision.
            </p>

            <div className="lp-actions">
              <Link to="/jobs" className="lp-btn lp-btn-ink">
                Browse open roles
                <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                  arrow_forward
                </span>
              </Link>
              {user ? (
                <Link to="/workspace" className="lp-btn lp-btn-light">
                  Open my workspace
                </Link>
              ) : (
                <Link to="/register" className="lp-btn lp-btn-light">
                  Create an account
                </Link>
              )}
            </div>

            <p className="lp-hero-switch">
              Hiring or operating the platform?{' '}
              <Link to={authPath('/login', 'RECRUITER')}>Recruiter sign in</Link>
              <span aria-hidden="true"> · </span>
              <Link to={authPath('/login', 'ADMIN')}>Administrator sign in</Link>
            </p>

            <ul className="lp-hero-proof" aria-label="Platform guarantees">
              <li>Role-scoped access</li>
              <li>Candidate-owned documents</li>
              <li>Every status change visible</li>
            </ul>
          </div>

          <div className="lp-hero-visual">
            <div className="lp-window">
              <div className="lp-window-top">
                <span className="lp-window-dots" aria-hidden="true">
                  <i />
                  <i />
                  <i />
                </span>
                <span className="lp-window-path">SmartHire / Hiring record</span>
                <span className="lp-live">
                  <span aria-hidden="true" />
                  Live
                </span>
              </div>

              <div className="lp-window-body">
                <div className="lp-window-head">
                  <div>
                    <p className="lp-mini-label">Open role</p>
                    <p className="lp-window-title">Senior Product Designer</p>
                    <p className="lp-window-sub">Nova Labs · Bengaluru · Hybrid</p>
                  </div>
                  <span className="lp-pill lp-pill-green">Open</span>
                </div>

                <div className="lp-pipeline">
                  {PIPELINE.map(column => (
                    <div className="lp-column" key={column.title}>
                      <div className="lp-column-head">
                        <span>{column.title}</span>
                        <span className="lp-column-count">{column.count}</span>
                      </div>
                      {column.candidates.map(candidate => (
                        <div className="lp-candidate" key={candidate.name}>
                          <span className={`lp-avatar lp-avatar-${candidate.tone}`}>
                            {candidate.initials}
                          </span>
                          <span className="lp-candidate-copy">
                            <strong>{candidate.name}</strong>
                            <span>{candidate.detail}</span>
                          </span>
                        </div>
                      ))}
                    </div>
                  ))}
                </div>

                <div className="lp-window-foot">
                  <span>
                    <span className="lp-foot-dot" aria-hidden="true" />
                    Every change is recorded
                  </span>
                  <span className="lp-foot-link">View record →</span>
                </div>
              </div>
            </div>

            <div className="lp-float lp-float-a">
              <span className="lp-float-icon lp-float-icon-green" aria-hidden="true">
                <span className="material-symbols-outlined text-[18px]">check</span>
              </span>
              <span>
                <strong>Decision recorded</strong>
                <span>Hired · just now</span>
              </span>
            </div>

            <div className="lp-float lp-float-b">
              <span className="lp-float-icon lp-float-icon-blue" aria-hidden="true">
                <span className="material-symbols-outlined text-[18px]">calendar_month</span>
              </span>
              <span>
                <strong>Interview scheduled</strong>
                <span>Thu, 14:00 · Google Meet</span>
              </span>
            </div>

            <p className="lp-visual-note">
              An illustration of the implemented lifecycle. No candidate data is shown.
            </p>
          </div>
        </div>
      </section>

      {/* Trust strip */}
      <section className="lp-trust" aria-label="Platform scope">
        <div className="lp-shell lp-trust-inner">
          <p className="lp-trust-label">Built for the whole hiring table</p>
          <div className="lp-trust-roles">
            <span>
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                person
              </span>
              Candidates
            </span>
            <span>
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                work
              </span>
              Recruiters
            </span>
            <span>
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                dashboard
              </span>
              Administrators
            </span>
          </div>
          <p className="lp-trust-note">One record. Three scopes.</p>
        </div>
      </section>

      {/* Lifecycle */}
      <section className="lp-section" id="lifecycle">
        <div className="lp-shell">
          <div className="lp-section-head">
            <p className="lp-eyebrow">The lifecycle</p>
            <h2 className="lp-section-title">One role. One trail. No guesswork.</h2>
            <p className="lp-section-lede">
              The interface exposes the same states the services enforce. Nothing is
              skipped, collapsed or invented for display.
            </p>
          </div>

          <ol className="lp-lifecycle">
            {LIFECYCLE.map((step, index) => (
              <li className="lp-step" key={step.code}>
                <div className="lp-step-top">
                  <span className="lp-step-index">{String(index + 1).padStart(2, '0')}</span>
                  <span className={`lp-pill lp-pill-${step.tone}`}>{step.code}</span>
                </div>
                <h3>{step.title}</h3>
                <p>{step.copy}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* Product surface */}
      <section className="lp-section lp-section-soft" id="workspace">
        <div className="lp-shell">
          <div className="lp-section-head lp-section-head-row">
            <div>
              <p className="lp-eyebrow">The workspace</p>
              <h2 className="lp-section-title">
                Built around the decision,
                <br />
                not the dashboard.
              </h2>
            </div>
            <p className="lp-section-lede">
              A calm surface for the work that matters: the record, the interview,
              the documents and the scope. Every screen answers a question a hiring
              team actually asks.
            </p>
          </div>

          <div className="lp-bento">
            <article className="lp-bento-card lp-bento-wide lp-tone-ink">
              <div className="lp-bento-head">
                <span className="lp-bento-icon lp-bento-icon-light" aria-hidden="true">
                  <span className="material-symbols-outlined text-[20px]">history</span>
                </span>
                <span className="lp-bento-tag">Application record</span>
              </div>
              <h3>The whole story, in one place.</h3>
              <p>
                Every status change, interview note and final decision stays attached
                to the application — not scattered across inboxes and spreadsheets.
              </p>

              <div className="lp-mini-trail" aria-hidden="true">
                <div className="lp-mini-step is-done">
                  <span className="lp-mini-dot" />
                  <span>Application received</span>
                  <span className="lp-mini-code">APPLIED</span>
                </div>
                <div className="lp-mini-step is-done">
                  <span className="lp-mini-dot" />
                  <span>Shortlisted by Nova Labs</span>
                  <span className="lp-mini-code">SHORTLISTED</span>
                </div>
                <div className="lp-mini-step is-done">
                  <span className="lp-mini-dot" />
                  <span>Interview completed</span>
                  <span className="lp-mini-code">COMPLETED</span>
                </div>
                <div className="lp-mini-step is-current">
                  <span className="lp-mini-dot" />
                  <span>Decision recorded</span>
                  <span className="lp-mini-code">HIRED</span>
                </div>
              </div>
            </article>

            <article className="lp-bento-card lp-tone-lavender">
              <div className="lp-bento-head">
                <span className="lp-bento-icon" aria-hidden="true">
                  <span className="material-symbols-outlined text-[20px]">calendar_month</span>
                </span>
                <span className="lp-bento-tag">Interviews</span>
              </div>
              <h3>A time, a place, a person.</h3>
              <p>
                Scheduling is a recorded event: date, start time, interviewer and
                location all travel with the recruitment.
              </p>
              <div className="lp-meeting">
                <div className="lp-meeting-top">
                  <span>
                    <strong>Thu, 14:00</strong>
                    <span>45 min · Google Meet</span>
                  </span>
                  <span className="lp-pill lp-pill-violet">Scheduled</span>
                </div>
                <div className="lp-meeting-person">
                  <span className="lp-avatar lp-avatar-blue">PN</span>
                  <span>
                    <strong>Priya Nair</strong>
                    <span>Hiring manager</span>
                  </span>
                </div>
              </div>
            </article>

            <article className="lp-bento-card lp-tone-peach">
              <div className="lp-bento-head">
                <span className="lp-bento-icon" aria-hidden="true">
                  <span className="material-symbols-outlined text-[20px]">folder</span>
                </span>
                <span className="lp-bento-tag">Documents</span>
              </div>
              <h3>Files keep their owner.</h3>
              <p>
                Résumés, cover letters and portfolios stay candidate-owned. Access is
                scoped by identity, not by link.
              </p>
              <div className="lp-files">
                <div className="lp-file">
                  <span className="lp-file-icon" aria-hidden="true">
                    <span className="material-symbols-outlined text-[16px]">description</span>
                  </span>
                  <span>ananya-resume.pdf</span>
                  <span className="lp-file-size">284 KB</span>
                </div>
                <div className="lp-file">
                  <span className="lp-file-icon" aria-hidden="true">
                    <span className="material-symbols-outlined text-[16px]">description</span>
                  </span>
                  <span>portfolio-2026.pdf</span>
                  <span className="lp-file-size">1.8 MB</span>
                </div>
              </div>
              <span className="lp-owner-chip">
                <span className="material-symbols-outlined text-[14px]" aria-hidden="true">
                  lock
                </span>
                Candidate-owned
              </span>
            </article>

            <article className="lp-bento-card lp-bento-wide lp-tone-lime">
              <div className="lp-bento-head">
                <span className="lp-bento-icon" aria-hidden="true">
                  <span className="material-symbols-outlined text-[20px]">admin_panel_settings</span>
                </span>
                <span className="lp-bento-tag">Role scopes</span>
              </div>
              <h3>The right view for every seat.</h3>
              <p>
                Navigation and routes derive from the roles returned by the identity
                service. There is no preference switch to widen them.
              </p>
              <div className="lp-scopes">
                <div className="lp-scope-row">
                  <span className="lp-scope-role">Candidate</span>
                  <span>Own applications, documents and notifications</span>
                </div>
                <div className="lp-scope-row">
                  <span className="lp-scope-role">Recruiter</span>
                  <span>Organization roles, applicants and interview decisions</span>
                </div>
                <div className="lp-scope-row">
                  <span className="lp-scope-role">Administrator</span>
                  <span>Read-only operating view across both workspaces</span>
                </div>
              </div>
            </article>
          </div>
        </div>
      </section>

      {/* Workspaces */}
      <section className="lp-section" id="workspaces">
        <div className="lp-shell">
          <div className="lp-section-head">
            <p className="lp-eyebrow">Three workspaces, one record</p>
            <h2 className="lp-section-title">The same record, seen from the right side.</h2>
            <p className="lp-section-lede">
              Candidates see their own progress. Recruiters see the applications and
              interviews they own. Administrators get a read-only operating view.
            </p>
          </div>

          <div className="lp-roles">
            {WORKSPACE_PATHS.map(path => (
              <article
                className={`lp-role-card ${path.role === 'RECRUITER' ? 'is-featured' : ''}`}
                key={path.role}
              >
                <div className="lp-role-head">
                  <span className="lp-role-icon" aria-hidden="true">
                    <span className="material-symbols-outlined text-[20px]">
                      {path.role === 'CANDIDATE'
                        ? 'person'
                        : path.role === 'RECRUITER'
                          ? 'work'
                          : 'dashboard'}
                    </span>
                  </span>
                  <span className="lp-role-label">{path.role.toLowerCase()}</span>
                </div>
                <h3>{path.title}</h3>
                <p className="lp-role-copy">{path.copy}</p>
                <ul className="lp-role-list">
                  {path.bullets.map(bullet => (
                    <li key={bullet}>{bullet}</li>
                  ))}
                </ul>
                <div className="lp-role-actions">
                  <Link
                    to={path.primary.to}
                    className={`lp-btn lp-btn-sm ${
                      path.role === 'RECRUITER' ? 'lp-btn-light' : 'lp-btn-ink'
                    }`}
                  >
                    {path.primary.label}
                  </Link>
                  {path.secondary && (
                    <Link
                      to={path.secondary.to}
                      className={`lp-role-link ${
                        path.role === 'RECRUITER' ? 'is-light' : ''
                      }`}
                    >
                      {path.secondary.label}
                    </Link>
                  )}
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      {/* Principle */}
      <section className="lp-quote-section">
        <div className="lp-shell lp-quote-grid">
          <div>
            <p className="lp-eyebrow lp-eyebrow-light">The principle</p>
            <blockquote className="lp-quote">
              “A hiring decision is only as good as the record behind it.”
            </blockquote>
            <p className="lp-quote-attr">The SmartHire design principle</p>
          </div>
          <div className="lp-quote-stats">
            <div>
              <span className="lp-stat-number">5</span>
              <p>lifecycle stages, visible end to end</p>
            </div>
            <div>
              <span className="lp-stat-number">3</span>
              <p>role scopes, enforced by the system</p>
            </div>
            <div>
              <span className="lp-stat-number">1</span>
              <p>accountable record per application</p>
            </div>
          </div>
        </div>
      </section>

      {/* Closing */}
      <section className="lp-cta-section">
        <div className="lp-shell">
          <div className="lp-cta-card">
            <div>
              <p className="lp-eyebrow">Start here</p>
              <h2>Start with the role. Keep the record.</h2>
              <p>
                Browse published roles, or create an account for the workspace you
                need. Candidates, recruiters and administrators enter through the
                same front door.
              </p>
            </div>
            <div className="lp-cta-actions">
              <Link to="/jobs" className="lp-btn lp-btn-ink">
                Browse open roles
              </Link>
              {!user && (
                <Link to="/register" className="lp-btn lp-btn-light">
                  Create an account
                </Link>
              )}
            </div>
          </div>
        </div>
      </section>

      <footer className="lp-footer">
        <div className="lp-shell">
          <div className="lp-footer-grid">
            <div className="lp-footer-brand">
              <p className="lp-footer-word">SmartHire</p>
              <p className="lp-footer-tag">Employment decision architecture.</p>
              <p className="lp-footer-note">
                One accountable trail from the first application to the final hire.
              </p>
            </div>
            <nav className="lp-footer-col" aria-label="Product">
              <p>Product</p>
              <Link to="/jobs">Browse roles</Link>
              <Link to="/register">Create account</Link>
              <Link to={authPath('/login', 'CANDIDATE')}>Candidate sign in</Link>
            </nav>
            <nav className="lp-footer-col" aria-label="Workspaces">
              <p>Workspaces</p>
              <Link to={authPath('/login', 'RECRUITER')}>Recruiter sign in</Link>
              <Link to={authPath('/login', 'ADMIN')}>Administrator sign in</Link>
              <Link to="/workspace">My workspace</Link>
            </nav>
            <div className="lp-footer-col">
              <p>Platform</p>
              <span>Role-scoped access</span>
              <span>Document ownership</span>
              <span>Status history</span>
            </div>
          </div>
          <div className="lp-footer-bottom">
            <span>© 2026 SmartHire</span>
            <span className="lp-footer-mono">Role-scoped access / Review 1</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
