import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { primaryRole } from '../../utils/roles';
import { Alert, type AlertTone } from '../ui/Alert';
import { RoleChip } from '../ui/RoleChip';
import { Button } from '../ui/Button';
import { Spinner } from '../ui/Spinner';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  end?: boolean;
}

/**
 * Navigation is derived from the caller's role, never from a stored preference.
 * The order here is the order shown, so the most frequent destination sits first.
 */
const NAV_BY_ROLE: Record<string, NavItem[]> = {
  CANDIDATE: [
    { path: '/jobs', label: 'Browse jobs', icon: 'search' },
    { path: '/candidate/applications', label: 'My applications', icon: 'assignment' },
    { path: '/candidate/progress', label: 'My progress', icon: 'timeline' },
    { path: '/candidate/documents', label: 'Documents', icon: 'folder' },
    { path: '/candidate/notifications', label: 'Notifications', icon: 'notifications' },
    { path: '/candidate/profile', label: 'Profile', icon: 'person' },
  ],
  RECRUITER: [
    { path: '/recruiter/jobs', label: 'Job postings', icon: 'work' },
    { path: '/recruiter/recruitments', label: 'Recruitments', icon: 'group_work' },
    { path: '/recruiter/applications', label: 'Look up application', icon: 'find_in_page' },
    { path: '/jobs', label: 'Browse all jobs', icon: 'public' },
    { path: '/recruiter/profile', label: 'Profile', icon: 'person' },
  ],
  ADMIN: [
    { path: '/admin/overview', label: 'Overview', icon: 'dashboard' },
    { path: '/admin/recruitments', label: 'All recruitments', icon: 'list_alt' },
    { path: '/admin/lookup', label: 'Record lookup', icon: 'find_in_page' },
    { path: '/admin/profile', label: 'Profile', icon: 'person' },
  ],
};

const WORKSPACE_LABEL: Record<string, string> = {
  CANDIDATE: 'Candidate workspace',
  RECRUITER: 'Recruiter workspace',
  ADMIN: 'Administration',
};

function BrandMark({ className = '' }: { className?: string }) {
  return (
    <Link
      to="/"
      className={`brand-mark inline-flex items-center gap-2.5 rounded-md focus-ring ${className}`}
      aria-label="SmartHire home"
    >
      <svg
        viewBox="0 0 64 64"
        className="brand-mark-tile h-8 w-8 shrink-0 rounded-lg"
        aria-hidden="true"
      >
        <rect width="64" height="64" rx="12" fill="#B78E4F" />
        <path
          d="M17 20h30M17 32h21M17 44h30"
          stroke="#0F271F"
          strokeWidth="4"
          strokeLinecap="square"
        />
        <circle cx="45" cy="32" r="4.5" fill="#F7F5ED" />
      </svg>
      <span className="brand-word">SmartHire</span>
    </Link>
  );
}

/**
 * Full-height application shell with a persistent sidebar.
 *
 * Below `lg` the sidebar becomes a modal drawer so navigation stays reachable on
 * a phone; the drawer closes on route change, on Escape, and on a scrim click.
 */
export function Layout({ children }: { children: ReactNode }) {
  const { user, logout, isLoading } = useAuth();
  const location = useLocation();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const [signingOut, setSigningOut] = useState(false);
  const [dismissedNotice, setDismissedNotice] = useState<string | null>(null);

  // Close the drawer whenever the route changes.
  useEffect(() => {
    setDrawerOpen(false);
  }, [location.pathname]);

  // Escape closes the drawer, and focus moves into it when it opens.
  useEffect(() => {
    if (!drawerOpen) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setDrawerOpen(false);
    };
    document.addEventListener('keydown', onKeyDown);
    closeButtonRef.current?.focus();

    return () => document.removeEventListener('keydown', onKeyDown);
  }, [drawerOpen]);

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-c-bg">
        <Spinner className="h-6 w-6 text-c-primary-600" label="Checking your session" />
      </div>
    );
  }

  const role = primaryRole(user?.roles);
  const navItems = NAV_BY_ROLE[role] ?? [];
  const workspaceLabel = WORKSPACE_LABEL[role] ?? 'Workspace';

  const notice = (
    location.state as
      | { notice?: { tone?: AlertTone; title: string; message: string } }
      | null
  )?.notice;
  const noticeKey = notice ? `${notice.title}|${notice.message}` : null;

  const handleSignOut = async () => {
    setSigningOut(true);
    try {
      await logout();
    } finally {
      setSigningOut(false);
    }
  };

  return (
    <div className="app-shell min-h-screen">
      <a href="#main-content" className="skip-link font-label-md text-label-md">
        Skip to content
      </a>

      {/* Static sidebar from lg up. */}
      <aside
        className="app-sidebar fixed bottom-0 left-0 top-0 z-30 hidden w-sidebar flex-col lg:flex"
        aria-label="Main navigation"
      >
        <SidebarContent navItems={navItems} role={role} />
      </aside>

      <TopBar
        role={role}
        workspaceLabel={workspaceLabel}
        email={user?.email}
        onOpenMenu={() => setDrawerOpen(true)}
        onSignOut={handleSignOut}
        signingOut={signingOut}
      />

      {/* Drawer below lg. */}
      {drawerOpen && (
        <div className="fixed inset-0 z-scrim lg:hidden">
          <div
            className="absolute inset-0 bg-c-overlay"
            onClick={() => setDrawerOpen(false)}
            aria-hidden="true"
          />
          <div
            role="dialog"
            aria-modal="true"
            aria-label="Main navigation"
            className="app-sidebar relative flex h-full w-72 max-w-[84vw] flex-col shadow-raised animate-slide-in-left"
          >
            <SidebarContent
              navItems={navItems}
              role={role}
              closeButton={
                <button
                  ref={closeButtonRef}
                  type="button"
                  onClick={() => setDrawerOpen(false)}
                  aria-label="Close navigation"
                  className="flex h-9 w-9 items-center justify-center rounded-md text-c-primary-100 transition-colors duration-fast hover:bg-white/10 hover:text-white focus-ring"
                >
                  <span className="material-symbols-outlined text-[20px]" aria-hidden="true">
                    close
                  </span>
                </button>
              }
            />
          </div>
        </div>
      )}

      <main id="main-content" className="min-h-screen pt-topbar lg:pl-sidebar">
        <div className="app-content">
          {notice && noticeKey !== dismissedNotice && (
            <div className="mb-5">
              <Alert
                tone={notice.tone ?? 'info'}
                title={notice.title}
                action={
                  <button
                    type="button"
                    onClick={() => setDismissedNotice(noticeKey)}
                    className="rounded px-2 py-1 font-label-md text-label-md text-current transition-opacity hover:opacity-70 focus-ring"
                  >
                    Dismiss
                  </button>
                }
              >
                {notice.message}
              </Alert>
            </div>
          )}
          {children}
        </div>
      </main>
    </div>
  );
}

function TopBar({
  role,
  workspaceLabel,
  email,
  onOpenMenu,
  onSignOut,
  signingOut,
}: {
  role: string;
  workspaceLabel: string;
  email?: string;
  onOpenMenu: () => void;
  onSignOut: () => void;
  signingOut: boolean;
}) {
  return (
    <header className="app-topbar fixed left-0 right-0 top-0 z-20 flex h-topbar items-center justify-between gap-3 px-gutter-mobile sm:px-gutter lg:left-sidebar">
      <div className="flex min-w-0 items-center gap-3">
        <button
          type="button"
          onClick={onOpenMenu}
          aria-label="Open navigation"
          className="flex h-10 w-10 items-center justify-center rounded-md text-c-text-muted transition-colors duration-fast hover:bg-c-surface-sunken hover:text-on-surface focus-ring lg:hidden"
        >
          <span className="material-symbols-outlined text-[22px]" aria-hidden="true">
            menu
          </span>
        </button>
        <span className="lg:hidden">
          <BrandMark />
        </span>
        <div className="hidden min-w-0 flex-col lg:flex">
          <span className="workspace-signature">{workspaceLabel}</span>
          {email && (
            <span className="mt-0.5 max-w-[22rem] truncate text-[13px] font-medium text-c-text">
              {email}
            </span>
          )}
        </div>
      </div>

      <div className="flex min-w-0 items-center gap-2.5 sm:gap-4">
        <span className="hidden sm:inline-flex">
          <RoleChip role={role} size="md" />
        </span>
        <Button
          variant="secondary"
          size="sm"
          onClick={onSignOut}
          isLoading={signingOut}
          aria-label="Sign out"
          leftIcon={
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
              logout
            </span>
          }
        >
          <span className="hidden sm:inline">Sign out</span>
        </Button>
      </div>
    </header>
  );
}

function SidebarContent({
  navItems,
  role,
  closeButton,
}: {
  navItems: NavItem[];
  role: string;
  closeButton?: ReactNode;
}) {
  return (
    <>
      <div className="relative z-10 border-b border-white/10 px-5 pb-5 pt-5">
        <div className="flex items-center justify-between gap-3">
          <BrandMark />
          {closeButton}
        </div>
        <p className="mt-4 text-[10px] font-medium uppercase tracking-[0.16em] text-c-primary-300">
          Employment decisions
        </p>
      </div>

      <nav className="relative z-10 grow overflow-y-auto px-3 py-5">
        <p className="px-3 pb-2 app-sidebar-label">Workspace</p>
        <div className="space-y-1">
          {navItems.map(item => (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.end}
              className={({ isActive }) =>
                `app-nav-item ${isActive ? 'app-nav-item-active' : 'app-nav-item-idle'}`
              }
            >
              <span
                className="material-symbols-outlined shrink-0 text-[20px]"
                aria-hidden="true"
              >
                {item.icon}
              </span>
              <span className="truncate font-body-md text-body-md">{item.label}</span>
            </NavLink>
          ))}
        </div>
      </nav>

      <div className="relative z-10 border-t border-white/10 p-4">
        {(role === 'RECRUITER' || role === 'ADMIN') && (
          <a
            href="http://localhost:8080/swagger-ui.html"
            target="_blank"
            rel="noopener noreferrer"
            className="app-nav-item app-nav-item-idle group mb-2 flex text-caption"
          >
            <span className="material-symbols-outlined shrink-0 text-[18px]" aria-hidden="true">
              description
            </span>
            <span>API documentation</span>
            <span
              className="material-symbols-outlined ml-auto shrink-0 text-[14px] transition-colors duration-fast group-hover:text-c-accent-300"
              aria-hidden="true"
            >
              open_in_new
            </span>
          </a>
        )}
        <p className="px-3 text-[10px] leading-relaxed text-c-primary-300">
          Role-scoped access
          <br />
          Every decision leaves a record.
        </p>
      </div>
    </>
  );
}

/** Shell for screens anyone can reach, including signed-out visitors. */
export function PublicLayout({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  const isLanding = location.pathname === '/';
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    if (!isLanding) {
      setScrolled(true);
      return;
    }

    const onScroll = () => setScrolled(window.scrollY > 16);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [isLanding]);

  const overHero = isLanding && !scrolled;

  return (
    <div className={`min-h-screen ${isLanding ? '' : 'bg-c-bg'}`}>
      <a href="#main-content" className="skip-link font-label-md text-label-md">
        Skip to content
      </a>

      <header className={`public-header ${overHero ? 'is-over-hero' : 'is-solid'}`}>
        <div className="public-header-inner">
          <BrandMark />

          <nav className="flex min-w-0 items-center gap-4 sm:gap-5" aria-label="Account">
            <Link to="/jobs" className="public-link hidden sm:inline">
              Browse jobs
            </Link>

            {user ? (
              <Link to="/workspace" className="btn btn-secondary btn-sm px-3.5 py-1.5">
                My workspace
              </Link>
            ) : (
              <>
                <Link to="/login" className="public-link hidden sm:inline">
                  Sign in
                </Link>
                <Link to="/register" className="btn btn-primary btn-sm px-3.5 py-1.5">
                  Create account
                </Link>
                <Link to="/login" className="btn btn-secondary btn-sm px-3.5 py-1.5 sm:hidden">
                  Sign in
                </Link>
              </>
            )}
          </nav>
        </div>
      </header>

      <main id="main-content" className={isLanding ? '' : 'pt-topbar'}>
        {isLanding ? (
          children
        ) : (
          <div className="app-content">{children}</div>
        )}
      </main>
    </div>
  );
}

/** Split editorial shell for the sign-in, registration and interstitial screens. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="auth-shell">
      <a href="#auth-content" className="skip-link font-label-md text-label-md">
        Skip to content
      </a>

      <aside className="auth-brand" aria-label="About SmartHire">
        <div className="relative z-10">
          <BrandMark />
        </div>

        <div className="auth-brand-main">
          <p className="auth-brand-kicker">Employment decision architecture</p>
          <p className="auth-brand-title">Every decision, on the record.</p>
          <p className="auth-brand-copy">
            One accountable trail from the first application to the final hire.
            Candidates see their progress, recruiters see the evidence, and
            administrators see the operating picture.
          </p>

          <div className="auth-trail" aria-hidden="true">
            <div className="auth-trail-row">
              <span className="auth-trail-dot" />
              Application received
              <span className="auth-trail-code">APPLIED</span>
            </div>
            <div className="auth-trail-row">
              <span className="auth-trail-dot" />
              Review and shortlist
              <span className="auth-trail-code">SHORTLISTED</span>
            </div>
            <div className="auth-trail-row">
              <span className="auth-trail-dot" />
              Interview and decision
              <span className="auth-trail-code">HIRED</span>
            </div>
          </div>
        </div>

        <div className="auth-brand-foot">
          <span>Role-scoped access</span>
          <span>SmartHire / R1</span>
        </div>
      </aside>

      <main id="auth-content" className="auth-content">
        <div className="auth-form">{children}</div>
      </main>
    </div>
  );
}
