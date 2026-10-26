import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { authPath, type AccountRole } from '../../utils/roles';

interface WorkspacePath {
  role: AccountRole;
  title: string;
  copy: string;
  bullets: string[];
  primary: { label: string; to: string };
  secondary: { label: string; to: string };
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
      'Upload documents and read notifications',
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
      'Publish roles for your organization, review applications and carry each shortlisted candidate through a recorded decision.',
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
    secondary: {
      label: 'Create with invitation code',
      to: authPath('/register', 'ADMIN'),
    },
  },
];

/**
 * Public landing page.
 *
 * Every claim maps to an implemented capability: job discovery, applications,
 * recruitment tracking, document ownership, notifications, and role-scoped
 * administration. The decision record in the hero illustrates the real
 * application lifecycle rather than showing invented metrics or live data.
 */
export function LandingPage() {
  const { user } = useAuth();

  return (
    <div className="landing">
      {/* Hero */}
      <section className="hero">
        <div className="hero-inner">
          <div className="hero-copy">
            <p className="hero-kicker">Employment decision architecture</p>
            <h1 className="hero-title">Every hiring decision should leave a record.</h1>
            <p className="hero-lede">
              SmartHire keeps the role, the application, the interview and the
              outcome on one accountable trail — for the people applying and the
              people deciding.
            </p>

            <div className="hero-actions">
              <Link to="/jobs" className="btn btn-primary btn-lg px-6">
                Browse open roles
                <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                  arrow_forward
                </span>
              </Link>
              {user ? (
                <Link to="/workspace" className="btn btn-secondary btn-lg px-6">
                  Open my workspace
                </Link>
              ) : (
                <Link to="/register" className="btn btn-secondary btn-lg px-6">
                  Create an account
                </Link>
              )}
            </div>

            <p className="hero-switch">
              Hiring or operating the platform?{' '}
              <Link to={authPath('/login', 'RECRUITER')}>Recruiter sign in</Link>
              <span aria-hidden="true"> · </span>
              <Link to={authPath('/login', 'ADMIN')}>Administrator sign in</Link>
            </p>

            <div className="hero-proof" aria-label="Platform guarantees">
              <span>Role-scoped access</span>
              <span>Document ownership respected</span>
              <span>Status changes stay visible</span>
            </div>
          </div>

          <aside aria-label="Illustration of the application lifecycle">
            <div className="decision-card">
              <span className="decision-tab">Decision record</span>
              <div className="decision-card-inner">
                <div className="decision-head">
                  <div>
                    <p className="decision-kicker">Application record</p>
                    <p className="decision-title">One role. One trail.</p>
                  </div>
                  <span className="decision-stamp">Role scoped</span>
                </div>

                <div className="decision-meta">
                  <span>Candidate owned</span>
                  <span>Recruiter reviewed</span>
                  <span>Live status</span>
                </div>

                <div className="trail">
                  {[
                    { code: 'APPLIED', label: 'Application received', state: 'done' },
                    { code: 'UNDER_REVIEW', label: 'Under review', state: 'done' },
                    { code: 'SHORTLISTED', label: 'Shortlisted', state: 'current' },
                    { code: 'SCHEDULED', label: 'Interview scheduled', state: 'pending' },
                    { code: 'HIRED', label: 'Final decision', state: 'pending' },
                  ].map(step => (
                    <div key={step.code} className={`trail-step is-${step.state}`}>
                      <span className="trail-dot" aria-hidden="true" />
                      <span className="trail-label">{step.label}</span>
                      <span className="trail-code">{step.code}</span>
                    </div>
                  ))}
                </div>

                <div className="decision-foot">
                  <span>
                    Visible to <strong>one candidate</strong> and{' '}
                    <strong>their recruiter</strong>.
                  </span>
                  <span className="tabular">05 stages</span>
                </div>
              </div>
            </div>
            <p className="hero-note">
              An illustration of the implemented application lifecycle. No
              candidate data is shown.
            </p>
          </aside>
        </div>
      </section>

      {/* Lifecycle */}
      <section className="landing-section" id="lifecycle">
        <div className="landing-inner">
          <p className="section-label">The lifecycle</p>
          <h2 className="section-title">A role moves through one accountable path.</h2>
          <p className="section-copy">
            The interface exposes the same states the services enforce. Nothing is
            skipped, collapsed or invented for display.
          </p>

          <div className="process-grid" role="list" aria-label="Application lifecycle">
            {[
              {
                code: 'APPLIED',
                title: 'Application received',
                copy: 'A candidate submits a résumé link against a published role.',
              },
              {
                code: 'UNDER_REVIEW',
                title: 'Recruiter review',
                copy: 'The application enters a review state owned by the hiring team.',
              },
              {
                code: 'SHORTLISTED',
                title: 'Shortlisted',
                copy: 'A recruitment record begins and interview planning can start.',
              },
              {
                code: 'SCHEDULED',
                title: 'Interview scheduled',
                copy: 'Date, start time, interviewer and location are recorded.',
              },
              {
                code: 'COMPLETED',
                title: 'Interview completed',
                copy: 'The outcome is recorded, including an explicit no-show path.',
              },
              {
                code: 'HIRED',
                title: 'Decision recorded',
                copy: 'A hire or rejection closes the recruitment’s final status.',
              },
            ].map((step, index) => (
              <div className="process-step" role="listitem" key={step.code}>
                <span className="process-index">0{index + 1}</span>
                <p className="process-code">{step.code}</p>
                <p className="process-title">{step.title}</p>
                <p className="process-copy">{step.copy}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Capabilities */}
      <section className="landing-section border-y border-c-border bg-c-surface">
        <div className="landing-inner">
          <p className="section-label">Platform capability</p>
          <h2 className="section-title">Run the decision, not just the posting.</h2>
          <p className="section-copy">
            Candidate, recruiter and administrator workspaces are shaped around
            what each role is actually allowed to do.
          </p>

          <div className="capability-layout">
            <div className="capability-lede">
              <p className="font-display text-[2.6rem] leading-[1.02] text-c-text">
                One system of record for the whole hiring table.
              </p>
              <p className="section-copy">
                Job discovery, applications, interviews, documents, notifications
                and administration share the same identities and authorization
                rules.
              </p>
              <Link
                to="/jobs"
                className="btn btn-secondary btn-md mt-7 inline-flex"
              >
                See the open roles
              </Link>

              <div className="capability-scope" aria-label="Role scopes">
                <p className="capability-scope-title">Same record. Different scope.</p>
                <div className="capability-scope-row">
                  <span className="capability-scope-role">Candidate</span>
                  <span className="capability-scope-detail">
                    Own applications, documents and notifications
                  </span>
                </div>
                <div className="capability-scope-row">
                  <span className="capability-scope-role">Recruiter</span>
                  <span className="capability-scope-detail">
                    Organization roles, applicants and interview decisions
                  </span>
                </div>
                <div className="capability-scope-row">
                  <span className="capability-scope-role">Administrator</span>
                  <span className="capability-scope-detail">
                    Read-only operating view across both workspaces
                  </span>
                </div>
              </div>
            </div>

            <div className="capability-list">
              {[
                {
                  icon: 'search',
                  title: 'Discover published roles',
                  copy: 'Search by role, company and location. Only postings the backend considers visible are returned.',
                  tag: 'CANDIDATE',
                },
                {
                  icon: 'assignment',
                  title: 'Apply with a résumé link',
                  copy: 'An application is created against the role and stays owned by the signed-in candidate.',
                  tag: 'CANDIDATE',
                },
                {
                  icon: 'timeline',
                  title: 'Follow application and interview progress',
                  copy: 'Application status and interview status are shown as separate lifecycles, exactly as stored.',
                  tag: 'CANDIDATE',
                },
                {
                  icon: 'folder',
                  title: 'Keep documents with the candidate',
                  copy: 'Upload, download and delete résumé, cover letter, portfolio and certificate files within ownership rules.',
                  tag: 'CANDIDATE',
                },
                {
                  icon: 'work',
                  title: 'Create, publish and close postings',
                  copy: 'Drafts become visible only when opened. Edit and lifecycle actions are scoped to the owning organization.',
                  tag: 'RECRUITER',
                },
                {
                  icon: 'group_work',
                  title: 'Shortlist, schedule and decide',
                  copy: 'Review applications, record interview outcomes, and set a hire or reject decision with the service’s real transitions.',
                  tag: 'RECRUITER',
                },
              ].map(item => (
                <div className="capability-row" key={item.title}>
                  <span className="capability-icon" aria-hidden="true">
                    <span className="material-symbols-outlined text-[22px]">{item.icon}</span>
                  </span>
                  <div>
                    <p className="capability-title">{item.title}</p>
                    <p className="capability-copy">{item.copy}</p>
                  </div>
                  <span className="capability-tag">{item.tag}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* Three workspaces */}
      <section className="landing-section paths-section" id="workspaces">
        <div className="landing-inner">
          <p className="section-label">Three workspaces, one record</p>
          <h2 className="section-title">The same record, seen from the right side.</h2>
          <p className="section-copy">
            Candidates see their own progress. Recruiters see the applications and
            interviews they own. Administrators get a read-only operating view. Every
            account enters through the same front door and lands in the workspace its
            role is allowed to open.
          </p>

          <div className="paths-grid">
            {WORKSPACE_PATHS.map(path => (
              <div className="path-column" key={path.role}>
                <p className="path-title">{path.title}</p>
                <p className="path-copy">{path.copy}</p>
                <ul className="path-list">
                  {path.bullets.map(bullet => (
                    <li key={bullet}>{bullet}</li>
                  ))}
                </ul>
                <div className="path-actions">
                  <Link to={path.primary.to} className="btn btn-primary btn-md">
                    {path.primary.label}
                  </Link>
                  <Link to={path.secondary.to} className="btn btn-secondary btn-md">
                    {path.secondary.label}
                  </Link>
                </div>
                <p className="path-note">
                  {path.role === 'ADMIN'
                    ? 'Administrator views are read-only. Account creation requires a deployment-issued invitation code.'
                    : path.role === 'RECRUITER'
                    ? 'Recruiter actions are limited to the organization carried in the signed-in identity.'
                    : 'Your application record is visible to you and the recruiter reviewing that role.'}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Trust / architecture */}
      <section className="landing-section">
        <div className="landing-inner">
          <p className="section-label">Built on the record</p>
          <h2 className="section-title">Trust is a property of the system, not a promise.</h2>

          <div className="trust-grid">
            {[
              {
                title: 'Role-scoped by design',
                copy: 'Navigation and routes derive from the roles returned by the identity service. There is no preference switch to widen them.',
              },
              {
                title: 'Documents keep their owner',
                copy: 'Candidate files stay candidate-owned. Recruiters cannot read them; administrators can inspect them within their scope.',
              },
              {
                title: 'Notifications reflect real events',
                copy: 'Delivery rows are created from actual application status changes, with recipient resolution against the identity directory.',
              },
            ].map((item, index) => (
              <div className="trust-item" key={item.title}>
                <p className="trust-number">0{index + 1}</p>
                <p className="trust-title">{item.title}</p>
                <p className="trust-copy">{item.copy}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Closing */}
      <section className="closing-section">
        <div className="closing-inner">
          <div>
            <h2 className="closing-title">Start with the role. Keep the record.</h2>
            <p className="closing-copy">
              Browse published roles, or create an account for the workspace you
              need. Candidates, recruiters and administrators enter through the
              same front door.
            </p>
          </div>
          <div className="closing-actions">
            <Link to="/jobs" className="btn btn-primary btn-lg">
              Browse open roles
            </Link>
            {!user && (
              <Link to="/register" className="btn btn-secondary btn-lg">
                Create an account
              </Link>
            )}
          </div>
        </div>
      </section>

      <footer className="landing-footer">
        <div className="landing-footer-inner">
          <div>
            <p className="font-serif text-lg text-c-bg">SmartHire</p>
            <p className="mt-1">Employment decision architecture</p>
          </div>
          <nav className="flex flex-wrap gap-5" aria-label="Footer">
            <Link to="/jobs">Browse roles</Link>
            <Link to={authPath('/login', 'CANDIDATE')}>Candidate sign in</Link>
            <Link to={authPath('/login', 'RECRUITER')}>Recruiter sign in</Link>
            <Link to={authPath('/login', 'ADMIN')}>Administrator sign in</Link>
            {!user && <Link to="/register">Create account</Link>}
          </nav>
          <p className="font-mono text-[10px] tracking-wide text-c-primary-400">
            ROLE-SCOPED ACCESS / REVIEW 1
          </p>
        </div>
      </footer>
    </div>
  );
}
